#!/usr/bin/env python3
"""Static guard for GitHub Actions least-privilege workflow permissions."""
from pathlib import Path
import re
import sys

ALLOWED = {"contents: read", "permissions:\n  contents: read"}


def check(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors = []
    if "permissions:" not in text:
        errors.append(f"{path}: missing explicit permissions block")
        return errors
    if re.search(r"(?m)^\s{2,}(contents|actions|packages|pull-requests|checks|id-token|issues|security-events):\s*(write|read-write)\s*$", text):
        errors.append(f"{path}: broad or writable workflow permission")
    if re.search(r"(?m)^\s*permissions:\s*$\n(?:\s+[^\n]+\n)*\s+contents:\s*(?!read\s*$).+", text):
        errors.append(f"{path}: contents permission must be read-only")
    if re.search(r"(?m)^\s*permissions:\s*write-all\s*$|^\s*permissions:\s*read-all\s*$", text):
        errors.append(f"{path}: wildcard workflow permissions are forbidden")
    return errors


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.github/workflows')
    errors: list[str] = []
    for path in sorted(root.glob('*.yml')) + sorted(root.glob('*.yaml')):
        errors.extend(check(path))
    if errors:
        print('\n'.join(errors))
        return 1
    print(f"workflow permission boundary: PASS ({len(list(root.glob('*.y*ml')))} workflows)")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
