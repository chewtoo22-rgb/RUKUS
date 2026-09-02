#!/usr/bin/env python3
"""Static QA contract for the NITRO persisted-state migration boundary."""
from pathlib import Path
import sys

REQUIRED_MARKERS = (
    "schemaVersion",
    "NITRO",
    "nitro_projects",
    "rukus",
)
FORBIDDEN_MARKERS = (
    "network",
    "Runtime.getRuntime",
    "ProcessBuilder",
    "java.io.FileOutputStream",
)


def main() -> int:
    candidates = [
        Path("app/src/main/java"),
        Path("app/src/test/java"),
    ]
    files = [p for root in candidates if root.exists() for p in root.rglob("*.kt")]
    migration_files = [p for p in files if "Migration" in p.name or "migration" in p.name.lower()]
    if not migration_files:
        print("NITRO migration contract: no migration source found", file=sys.stderr)
        return 1
    text = "\n".join(p.read_text(encoding="utf-8") for p in migration_files)
    missing = [m for m in REQUIRED_MARKERS if m not in text]
    if missing:
        print(f"NITRO migration contract: missing markers: {', '.join(missing)}", file=sys.stderr)
        return 1
    forbidden = [m for m in FORBIDDEN_MARKERS if m in text]
    if forbidden:
        print(f"NITRO migration contract: forbidden coupling: {', '.join(forbidden)}", file=sys.stderr)
        return 1
    print(f"NITRO migration contract: PASS ({len(migration_files)} migration files scanned)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
