#!/usr/bin/env python3
"""Static contract for the canonical NITRO builder identity surface."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ROOT / "app/src/main/java/com/ruckus/agent/builder/BuilderEngine.kt",
    ROOT / "app/src/main/java/com/ruckus/agent/builder/ProjectSpec.kt",
    ROOT / "app/src/main/java/com/ruckus/agent/builder/NitroProjectStateMigration.kt",
]


def main() -> int:
    errors: list[str] = []
    for path in FILES:
        if not path.is_file():
            errors.append(f"missing builder identity source: {path}")
            continue
        text = path.read_text(encoding="utf-8")
        if "package com.ruckus.agent.builder" not in text:
            errors.append(f"wrong package boundary: {path}")
        if "android." in text or "java.io" in text or "java.net" in text or "ProcessBuilder" in text:
            errors.append(f"builder identity contract has runtime coupling: {path}")

    migration = FILES[-1]
    if migration.is_file():
        text = migration.read_text(encoding="utf-8")
        required = [
            'const val CURRENT_SCHEMA = 2',
            'const val PRODUCT_NITRO = "nitro"',
            'product = PRODUCT_NITRO',
        ]
        for marker in required:
            if marker not in text:
                errors.append(f"missing NITRO migration marker {marker!r}")

    for path in FILES[:2]:
        if path.is_file():
            text = path.read_text(encoding="utf-8")
            if "MUTINY" in text or "Mutiny" in text or "mutiny_" in text:
                errors.append(f"legacy MUTINY identity leaked into canonical builder source: {path}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("NITRO builder identity contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
