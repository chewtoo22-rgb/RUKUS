#!/usr/bin/env python3
"""Fail-closed checks for the shipped Android product identity boundary."""
from __future__ import annotations

import re
import sys
from pathlib import Path

MANIFEST = Path("app/src/main/AndroidManifest.xml")


def main() -> int:
    text = MANIFEST.read_text(encoding="utf-8")
    checks = {
        "NITRO label": 'android:label="NITRO"' in text,
        "no legacy product label": 'android:label="RUCKUS"' not in text,
        "launcher exported": 'android:name=".MainActivity"' in text and 'android:exported="true"' in text,
        "accessibility binding": 'android.permission.BIND_ACCESSIBILITY_SERVICE' in text,
        "accessibility non-exported": re.search(r'android:name="\.control\.RuckusAccessibilityService"[\s\S]*?android:exported="false"', text) is not None,
        "shizuku provider authority": 'android:authorities="${applicationId}.shizuku"' in text,
        "shizuku provider enabled": 'android:enabled="true"' in text,
    }
    failures = [name for name, ok in checks.items() if not ok]
    if failures:
        print("manifest identity contract failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print(f"manifest identity contract passed ({len(checks)} checks)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
