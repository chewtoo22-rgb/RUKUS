#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import pathlib
import re
import stat
import sys

SCHEMA = 1
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
HASH_RE = re.compile(r"^[0-9a-f]{64}$")
APP_ID_RE = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")
ALLOWED_KEYS = {
    "schema_version",
    "product",
    "source_sha",
    "application_id",
    "version_code",
    "apk_filename",
    "apk_size",
    "apk_sha256",
    "signed",
}

class ProvenanceError(ValueError):
    pass


def fail(message: str) -> None:
    raise ProvenanceError(message)


def load_regular_file(path: pathlib.Path, *, max_bytes: int) -> bytes:
    try:
        st = path.lstat()
    except FileNotFoundError:
        fail(f"missing file: {path}")
    if stat.S_ISLNK(st.st_mode) or not stat.S_ISREG(st.st_mode):
        fail(f"not a regular non-symlink file: {path}")
    if st.st_size > max_bytes:
        fail(f"file exceeds {max_bytes} bytes: {path}")
    return path.read_bytes()


def validate(evidence_path: pathlib.Path, apk_path: pathlib.Path, expected_sha: str) -> dict:
    if not SHA_RE.fullmatch(expected_sha):
        fail("expected source SHA must be 40 lowercase hex characters")

    raw = load_regular_file(evidence_path, max_bytes=16 * 1024)
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(f"invalid provenance JSON: {exc}")
    if not isinstance(data, dict):
        fail("provenance root must be an object")
    if set(data) != ALLOWED_KEYS:
        fail("provenance fields do not exactly match schema")

    if data["schema_version"] != SCHEMA:
        fail("unsupported provenance schema")
    if data["product"] != "RUKUS":
        fail("product must be RUKUS")
    if data["source_sha"] != expected_sha:
        fail("source SHA does not match exact workflow head")
    if not SHA_RE.fullmatch(str(data["source_sha"])):
        fail("source SHA is malformed")
    if data["application_id"] != "com.ruckus.agent" or not APP_ID_RE.fullmatch(str(data["application_id"])):
        fail("unexpected application id")
    if not isinstance(data["version_code"], int) or isinstance(data["version_code"], bool) or data["version_code"] <= 0:
        fail("version code must be a positive integer")
    if data["signed"] is not False:
        fail("current release artifact must be explicitly marked unsigned")

    apk_bytes = load_regular_file(apk_path, max_bytes=512 * 1024 * 1024)
    if pathlib.PurePosixPath(str(data["apk_filename"])).name != data["apk_filename"]:
        fail("APK filename must be a basename")
    if data["apk_filename"] != apk_path.name:
        fail("APK filename does not match artifact")
    if data["apk_size"] != len(apk_bytes):
        fail("APK size does not match artifact")
    actual_hash = hashlib.sha256(apk_bytes).hexdigest()
    if not HASH_RE.fullmatch(str(data["apk_sha256"])) or data["apk_sha256"] != actual_hash:
        fail("APK SHA-256 does not match artifact")
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate RUKUS APK release provenance")
    parser.add_argument("evidence")
    parser.add_argument("apk")
    parser.add_argument("--expected-sha", default=os.environ.get("GITHUB_SHA", ""))
    args = parser.parse_args()
    try:
        data = validate(pathlib.Path(args.evidence), pathlib.Path(args.apk), args.expected_sha)
    except ProvenanceError as exc:
        print(f"APK PROVENANCE: FAIL: {exc}", file=sys.stderr)
        return 1
    print("APK PROVENANCE: PASS")
    print(f"source_sha={data['source_sha']}")
    print(f"apk_sha256={data['apk_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
