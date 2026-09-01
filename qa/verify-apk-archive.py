#!/usr/bin/env python3
"""Fail-closed structural validation for release APK ZIP containers."""

from __future__ import annotations

import re
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

MAX_ENTRIES = 20_000
MAX_UNCOMPRESSED_BYTES = 1 << 30  # 1 GiB
_DRIVE_PREFIX = re.compile(r"^[A-Za-z]:")


class ArchiveValidationError(ValueError):
    pass


def _safe_name(raw_name: str) -> str:
    if not raw_name:
        raise ArchiveValidationError("empty archive entry name")
    normalized = raw_name.replace("\\", "/")
    if normalized.startswith("/") or _DRIVE_PREFIX.match(normalized):
        raise ArchiveValidationError(f"absolute archive entry path: {raw_name!r}")

    parts = normalized.split("/")
    if any(part in {"", ".", ".."} for part in parts[:-1]):
        raise ArchiveValidationError(f"ambiguous/traversing archive entry path: {raw_name!r}")
    if parts[-1] in {".", ".."}:
        raise ArchiveValidationError(f"traversing archive entry path: {raw_name!r}")

    canonical = str(PurePosixPath(normalized))
    if canonical.startswith("../") or canonical == "..":
        raise ArchiveValidationError(f"traversing archive entry path: {raw_name!r}")
    return canonical


def _reject_unsafe_member_type(info: zipfile.ZipInfo, name: str) -> None:
    """Reject Unix symlink/device/socket members while allowing normal files/dirs."""
    if info.create_system != 3:
        return

    mode = (info.external_attr >> 16) & 0xFFFF
    file_type = stat.S_IFMT(mode)
    if file_type in {0, stat.S_IFREG, stat.S_IFDIR}:
        return

    if file_type == stat.S_IFLNK:
        kind = "symlink"
    elif file_type == stat.S_IFCHR:
        kind = "character device"
    elif file_type == stat.S_IFBLK:
        kind = "block device"
    elif file_type == stat.S_IFIFO:
        kind = "fifo"
    elif file_type == stat.S_IFSOCK:
        kind = "socket"
    else:
        kind = "special file"
    raise ArchiveValidationError(f"unsafe {kind} archive entry: {name!r}")


def validate_apk(path: Path) -> tuple[int, int]:
    try:
        path_mode = path.lstat().st_mode
    except FileNotFoundError as exc:
        raise ArchiveValidationError(f"APK is not a regular file: {path}") from exc

    if stat.S_ISLNK(path_mode):
        raise ArchiveValidationError(f"APK path must not be a symlink: {path}")
    if not stat.S_ISREG(path_mode):
        raise ArchiveValidationError(f"APK is not a regular file: {path}")

    try:
        with zipfile.ZipFile(path, "r") as archive:
            entries = archive.infolist()
            if not entries:
                raise ArchiveValidationError("APK archive is empty")
            if len(entries) > MAX_ENTRIES:
                raise ArchiveValidationError(
                    f"APK has too many entries: {len(entries)} > {MAX_ENTRIES}"
                )

            seen: set[str] = set()
            total_uncompressed = 0
            for info in entries:
                name = _safe_name(info.filename)
                if name in seen:
                    raise ArchiveValidationError(f"duplicate archive entry: {name!r}")
                seen.add(name)

                _reject_unsafe_member_type(info, name)

                if info.flag_bits & 0x1:
                    raise ArchiveValidationError(f"encrypted archive entry: {name!r}")

                total_uncompressed += info.file_size
                if total_uncompressed > MAX_UNCOMPRESSED_BYTES:
                    raise ArchiveValidationError(
                        "APK uncompressed payload exceeds 1 GiB safety bound"
                    )

            required = {"AndroidManifest.xml", "classes.dex"}
            missing = sorted(required - seen)
            if missing:
                raise ArchiveValidationError(
                    "required APK entries missing: " + ", ".join(missing)
                )

            bad_member = archive.testzip()
            if bad_member is not None:
                raise ArchiveValidationError(f"CRC failure in archive entry: {bad_member!r}")

            return len(entries), total_uncompressed
    except zipfile.BadZipFile as exc:
        raise ArchiveValidationError(f"invalid APK ZIP container: {exc}") from exc


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <apk>", file=sys.stderr)
        return 2

    try:
        count, total = validate_apk(Path(argv[1]))
    except ArchiveValidationError as exc:
        print(f"APK ARCHIVE GATE: FAIL: {exc}", file=sys.stderr)
        return 1

    print("APK ARCHIVE GATE: PASS")
    print(f"entries={count}")
    print(f"uncompressed_bytes={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
