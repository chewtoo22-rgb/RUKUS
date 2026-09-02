#!/usr/bin/env python3
"""Static release contract for the Android manifest."""
from pathlib import Path
import sys

MANIFEST = Path("app/src/main/AndroidManifest.xml")
REQUIRED = (
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.WRITE_SETTINGS',
    'android:name="rikka.shizuku.ShizukuProvider"',
    'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"',
    'android:name=".MainActivity"',
    'android.intent.action.MAIN',
    'android.intent.category.LAUNCHER',
    'android:exported="true"',
)
FORBIDDEN = (
    'android:exported="true"\n        </service>',
)


def main() -> int:
    if not MANIFEST.is_file():
        print(f"missing manifest: {MANIFEST}", file=sys.stderr)
        return 1
    text = MANIFEST.read_text(encoding="utf-8")
    missing = [marker for marker in REQUIRED if marker not in text]
    if missing:
        print("missing required manifest markers:", file=sys.stderr)
        for marker in missing:
            print(f"- {marker}", file=sys.stderr)
        return 1
    for marker in FORBIDDEN:
        if marker in text:
            print(f"forbidden manifest pattern: {marker!r}", file=sys.stderr)
            return 1
    print("Android manifest release contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
