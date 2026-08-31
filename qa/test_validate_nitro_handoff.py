#!/usr/bin/env python3

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from validate_nitro_handoff import ValidationError, validate


class NitroHandoffValidatorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "handoff"
        self.root.mkdir()
        self.apk = self.root / "outputs" / "app-debug.apk"
        self.apk.parent.mkdir()
        self.apk.write_bytes(b"PK\x03\x04synthetic-apk")
        self.manifest = Path(self.tmp.name) / "handoff.json"
        self.base = {
            "schema_version": 2,
            "producer": "NITRO",
            "consumer": "RUKUS",
            "source_sha": "1" * 40,
            "build_variant": "debug",
            "apk_path": "outputs/app-debug.apk",
            "apk_sha256": hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            "apk_size_bytes": self.apk.stat().st_size,
            "package_name": "com.example.generated",
        }
        self.write_manifest()

    def tearDown(self):
        self.tmp.cleanup()

    def write_manifest(self, **updates):
        data = dict(self.base)
        data.update(updates)
        self.manifest.write_text(json.dumps(data), encoding="utf-8")

    def assertRejected(self, **updates):
        self.write_manifest(**updates)
        with self.assertRaises(ValidationError):
            validate(self.manifest, self.root)

    def test_valid_handoff_is_ready_without_execution(self):
        result = validate(self.manifest, self.root)
        self.assertTrue(result["ready_for_rukus_handoff"])
        self.assertFalse(result["apk_executed"])
        self.assertEqual(result["producer"], "NITRO")
        self.assertEqual(result["consumer"], "RUKUS")
        self.assertEqual(result["source_sha"], "1" * 40)
        self.assertEqual(result["build_variant"], "debug")

    def test_release_variant_is_accepted_when_filename_matches(self):
        release = self.root / "outputs" / "app-release.apk"
        release.write_bytes(self.apk.read_bytes())
        self.apk.unlink()
        self.apk = release
        self.base.update({
            "build_variant": "release",
            "apk_path": "outputs/app-release.apk",
            "apk_sha256": hashlib.sha256(release.read_bytes()).hexdigest(),
            "apk_size_bytes": release.stat().st_size,
        })
        self.write_manifest()
        result = validate(self.manifest, self.root)
        self.assertEqual(result["build_variant"], "release")

    def test_rejects_wrong_identity(self):
        self.assertRejected(producer="MUTINY")
        self.assertRejected(consumer="OTHER")

    def test_rejects_legacy_schema_without_provenance(self):
        self.assertRejected(schema_version=1)

    def test_rejects_malformed_source_sha(self):
        self.assertRejected(source_sha="A" * 40)
        self.assertRejected(source_sha="1" * 39)
        self.assertRejected(source_sha="g" * 40)

    def test_rejects_unknown_build_variant(self):
        self.assertRejected(build_variant="benchmark")
        self.assertRejected(build_variant="Debug")

    def test_rejects_variant_filename_mismatch(self):
        self.assertRejected(build_variant="release")
        self.assertRejected(apk_path="outputs/app-release.apk")

    def test_rejects_traversal_and_backslashes(self):
        self.assertRejected(apk_path="../app-debug.apk")
        self.assertRejected(apk_path="outputs\\app-debug.apk")

    def test_rejects_symlink_apk(self):
        target = self.root / "real.apk"
        target.write_bytes(self.apk.read_bytes())
        self.apk.unlink()
        self.apk.symlink_to(target)
        with self.assertRaises(ValidationError):
            validate(self.manifest, self.root)

    def test_rejects_symlink_parent(self):
        real = self.root / "real-output"
        real.mkdir()
        real_apk = real / "app-debug.apk"
        real_apk.write_bytes(self.apk.read_bytes())
        self.apk.unlink()
        self.apk.parent.rmdir()
        self.apk.parent.symlink_to(real, target_is_directory=True)
        with self.assertRaises(ValidationError):
            validate(self.manifest, self.root)

    def test_rejects_hash_mismatch(self):
        self.assertRejected(apk_sha256="0" * 64)

    def test_rejects_size_mismatch(self):
        self.assertRejected(apk_size_bytes=self.apk.stat().st_size + 1)

    def test_rejects_unknown_manifest_fields(self):
        data = dict(self.base)
        data["command"] = "install"
        self.manifest.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(ValidationError):
            validate(self.manifest, self.root)

    def test_rejects_malformed_package_name(self):
        self.assertRejected(package_name="com.example;rm -rf")

    def test_rejects_manifest_symlink(self):
        real = Path(self.tmp.name) / "real.json"
        real.write_text(json.dumps(self.base), encoding="utf-8")
        self.manifest.unlink()
        self.manifest.symlink_to(real)
        with self.assertRaises(ValidationError):
            validate(self.manifest, self.root)


if __name__ == "__main__":
    unittest.main()
