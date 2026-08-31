#!/usr/bin/env python3
"""Fail-closed structural validation for release APK ZIP containers."""

from __future__ import annotations

import re
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


def validate_apk(path: Path) -> tuple[int, int]:
    if not path.is_file():
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
