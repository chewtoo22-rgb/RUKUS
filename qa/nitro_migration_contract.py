#!/usr/bin/env python3
"""Static QA contract for the NITRO persisted-state migration boundary."""
from pathlib import Path
import sys

MIGRATION_PATH = Path("app/src/main/java/com/ruckus/agent/builder/NitroProjectStateMigration.kt")
REQUIRED_MARKERS = (
    "schemaVersion",
    "PRODUCT_NITRO",
    "rukus",
)
FORBIDDEN_MARKERS = (
    "network",
    "Runtime.getRuntime",
    "ProcessBuilder",
    "java.io.FileOutputStream",
)


def main() -> int:
    if not MIGRATION_PATH.is_file():
        print(f"NITRO migration contract: missing migration source: {MIGRATION_PATH}", file=sys.stderr)
        return 1
    text = MIGRATION_PATH.read_text(encoding="utf-8")
    missing = [marker for marker in REQUIRED_MARKERS if marker not in text]
    if missing:
        print(f"NITRO migration contract: missing markers: {', '.join(missing)}", file=sys.stderr)
        return 1
    forbidden = [marker for marker in FORBIDDEN_MARKERS if marker in text]
    if forbidden:
        print(f"NITRO migration contract: forbidden coupling: {', '.join(forbidden)}", file=sys.stderr)
        return 1
    print(f"NITRO migration contract: PASS ({MIGRATION_PATH})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
