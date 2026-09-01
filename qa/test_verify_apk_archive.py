#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import stat
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("verify-apk-archive.py")
spec = importlib.util.spec_from_file_location("verify_apk_archive", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApkArchiveValidationTests(unittest.TestCase):
    def make_apk(self, entries: list[tuple[str, bytes]]) -> Path:
        temp = tempfile.NamedTemporaryFile(suffix=".apk", delete=False)
        temp.close()
        path = Path(temp.name)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for name, payload in entries:
                    archive.writestr(name, payload)
        self.addCleanup(path.unlink, missing_ok=True)
        return path

    def valid_entries(self) -> list[tuple[str, bytes]]:
        return [
            ("AndroidManifest.xml", b"manifest"),
            ("classes.dex", b"dex\n035\x00"),
            ("resources.arsc", b"resources"),
        ]

    def test_accepts_minimal_well_formed_apk_container(self) -> None:
        path = self.make_apk(self.valid_entries())
        count, total = module.validate_apk(path)
        self.assertEqual(count, 3)
        self.assertGreater(total, 0)

    def test_rejects_symlinked_apk_input(self) -> None:
        target = self.make_apk(self.valid_entries())
        link = target.with_name(target.name + ".link.apk")
        link.symlink_to(target)
        self.addCleanup(link.unlink, missing_ok=True)
        with self.assertRaisesRegex(module.ArchiveValidationError, "must not be a symlink"):
            module.validate_apk(link)

    def test_rejects_symlink_member(self) -> None:
        path = self.make_apk(self.valid_entries())
        with zipfile.ZipFile(path, "a") as archive:
            info = zipfile.ZipInfo("assets/current")
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(info, b"../outside")
        with self.assertRaisesRegex(module.ArchiveValidationError, "unsafe symlink"):
            module.validate_apk(path)

    def test_rejects_fifo_member(self) -> None:
        path = self.make_apk(self.valid_entries())
        with zipfile.ZipFile(path, "a") as archive:
            info = zipfile.ZipInfo("assets/channel")
            info.create_system = 3
            info.external_attr = (stat.S_IFIFO | 0o600) << 16
            archive.writestr(info, b"")
        with self.assertRaisesRegex(module.ArchiveValidationError, "unsafe fifo"):
            module.validate_apk(path)

    def test_rejects_duplicate_entry(self) -> None:
        path = self.make_apk(self.valid_entries() + [("classes.dex", b"shadow")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "duplicate"):
            module.validate_apk(path)

    def test_rejects_parent_traversal(self) -> None:
        path = self.make_apk(self.valid_entries() + [("../escape", b"x")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "travers"):
            module.validate_apk(path)

    def test_rejects_backslash_parent_traversal(self) -> None:
        path = self.make_apk(self.valid_entries() + [("dir\\..\\escape", b"x")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "travers"):
            module.validate_apk(path)

    def test_rejects_absolute_unix_path(self) -> None:
        path = self.make_apk(self.valid_entries() + [("/absolute", b"x")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "absolute"):
            module.validate_apk(path)

    def test_rejects_windows_drive_path(self) -> None:
        path = self.make_apk(self.valid_entries() + [("C:/escape", b"x")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "absolute"):
            module.validate_apk(path)

    def test_rejects_missing_manifest(self) -> None:
        path = self.make_apk([("classes.dex", b"dex")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "AndroidManifest.xml"):
            module.validate_apk(path)

    def test_rejects_missing_primary_dex(self) -> None:
        path = self.make_apk([("AndroidManifest.xml", b"manifest"), ("classes2.dex", b"dex")])
        with self.assertRaisesRegex(module.ArchiveValidationError, "classes.dex"):
            module.validate_apk(path)

    def test_rejects_non_zip_file(self) -> None:
        temp = tempfile.NamedTemporaryFile(suffix=".apk", delete=False)
        temp.write(b"not a zip")
        temp.close()
        path = Path(temp.name)
        self.addCleanup(path.unlink, missing_ok=True)
        with self.assertRaisesRegex(module.ArchiveValidationError, "invalid APK ZIP"):
            module.validate_apk(path)


if __name__ == "__main__":
    unittest.main()
