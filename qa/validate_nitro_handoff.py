#!/usr/bin/env python3
"""Validate a NITRO -> RUKUS APK handoff manifest without executing the APK."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import sys
from pathlib import Path, PurePosixPath

SCHEMA_VERSION = 2
MAX_APK_BYTES = 1 << 30
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SOURCE_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$")
ALLOWED_BUILD_VARIANTS = {"debug", "release"}
ALLOWED_KEYS = {
    "schema_version",
    "producer",
    "consumer",
    "source_sha",
    "build_variant",
    "apk_path",
    "apk_sha256",
    "apk_size_bytes",
    "package_name",
}


class ValidationError(ValueError):
    pass


def _load_manifest(path: Path) -> dict:
    if path.is_symlink():
        raise ValidationError("manifest must not be a symlink")
    try:
        mode = path.stat().st_mode
    except OSError as exc:
        raise ValidationError(f"manifest is not readable: {exc}") from exc
    if not stat.S_ISREG(mode):
        raise ValidationError("manifest must be a regular file")
    if path.stat().st_size > 16 * 1024:
        raise ValidationError("manifest exceeds 16 KiB")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"invalid manifest JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise ValidationError("manifest root must be an object")
    unknown = set(data) - ALLOWED_KEYS
    missing = ALLOWED_KEYS - set(data)
    if unknown:
        raise ValidationError(f"unknown manifest fields: {sorted(unknown)}")
    if missing:
        raise ValidationError(f"missing manifest fields: {sorted(missing)}")
    return data


def _validate_relative_apk_path(raw: object) -> PurePosixPath:
    if not isinstance(raw, str) or not raw or "\\" in raw or "\x00" in raw:
        raise ValidationError("apk_path must be a non-empty POSIX relative path")
    path = PurePosixPath(raw)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ValidationError("apk_path must not be absolute or contain traversal")
    if path.suffix.lower() != ".apk":
        raise ValidationError("apk_path must reference an .apk file")
    return path


def _resolve_regular_apk(root: Path, relative: PurePosixPath) -> Path:
    if root.is_symlink() or not root.is_dir():
        raise ValidationError("handoff root must be a real directory, not a symlink")
    root = root.resolve(strict=True)
    candidate = root.joinpath(*relative.parts)

    current = root
    for part in relative.parts[:-1]:
        current = current / part
        try:
            mode = current.lstat().st_mode
        except OSError as exc:
            raise ValidationError(f"apk parent is unavailable: {exc}") from exc
        if stat.S_ISLNK(mode):
            raise ValidationError("apk path must not traverse symlink directories")
        if not stat.S_ISDIR(mode):
            raise ValidationError("apk parent component is not a directory")

    try:
        mode = candidate.lstat().st_mode
    except OSError as exc:
        raise ValidationError(f"apk is unavailable: {exc}") from exc
    if stat.S_ISLNK(mode) or not stat.S_ISREG(mode):
        raise ValidationError("apk must be a regular non-symlink file")

    resolved = candidate.resolve(strict=True)
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ValidationError("apk resolves outside the handoff root") from exc
    return resolved


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate(manifest_path: Path, root: Path) -> dict:
    manifest = _load_manifest(manifest_path)
    if type(manifest["schema_version"]) is not int or manifest["schema_version"] != SCHEMA_VERSION:
        raise ValidationError("unsupported schema_version")
    if manifest["producer"] != "NITRO" or manifest["consumer"] != "RUKUS":
        raise ValidationError("handoff identity must be exactly NITRO -> RUKUS")

    source_sha = manifest["source_sha"]
    if not isinstance(source_sha, str) or not SOURCE_SHA_RE.fullmatch(source_sha):
        raise ValidationError("source_sha must be a lowercase 40-character Git commit SHA")

    build_variant = manifest["build_variant"]
    if build_variant not in ALLOWED_BUILD_VARIANTS:
        raise ValidationError("build_variant must be exactly debug or release")

    relative = _validate_relative_apk_path(manifest["apk_path"])
    expected_suffix = f"-{build_variant}.apk"
    if not relative.name.endswith(expected_suffix):
        raise ValidationError("apk_path filename does not match build_variant")

    apk = _resolve_regular_apk(root, relative)
    actual_size = apk.stat().st_size
    declared_size = manifest["apk_size_bytes"]
    if type(declared_size) is not int or declared_size <= 0 or declared_size > MAX_APK_BYTES:
        raise ValidationError("apk_size_bytes must be an integer in 1..1GiB")
    if actual_size != declared_size:
        raise ValidationError("APK size does not match manifest")

    declared_hash = manifest["apk_sha256"]
    if not isinstance(declared_hash, str) or not SHA256_RE.fullmatch(declared_hash):
        raise ValidationError("apk_sha256 must be lowercase SHA-256 hex")
    actual_hash = _sha256(apk)
    if actual_hash != declared_hash:
        raise ValidationError("APK SHA-256 does not match manifest")

    package_name = manifest["package_name"]
    if not isinstance(package_name, str) or len(package_name) > 255 or not PACKAGE_RE.fullmatch(package_name):
        raise ValidationError("package_name is malformed")

    return {
        "schema_version": SCHEMA_VERSION,
        "producer": "NITRO",
        "consumer": "RUKUS",
        "source_sha": source_sha,
        "build_variant": build_variant,
        "apk_path": relative.as_posix(),
        "apk_sha256": actual_hash,
        "apk_size_bytes": actual_size,
        "package_name": package_name,
        "ready_for_rukus_handoff": True,
        "apk_executed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = validate(args.manifest, args.root)
    except ValidationError as exc:
        print(f"NITRO handoff validation failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
