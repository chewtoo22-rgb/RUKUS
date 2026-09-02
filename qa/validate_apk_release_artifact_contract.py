#!/usr/bin/env python3
"""Static contract for the RUKUS APK release artifact boundary."""
from pathlib import Path

WORKFLOW = Path('.github/workflows/apk-release-gate.yml')


def main() -> int:
    text = WORKFLOW.read_text(encoding='utf-8')
    required = [
        'permissions:\n  contents: read',
        'gradle :app:assembleRelease --stacktrace',
        'qa/verify-apk-archive.py app/build/outputs/apk/release/app-release-unsigned.apk',
        'qa/verify-apk-release.sh app/build/outputs/apk/release/app-release-unsigned.apk',
        'sha256sum app/build/outputs/apk/release/app-release-unsigned.apk',
        'name: RUKUS-release-unsigned',
        'if-no-files-found: error',
    ]
    missing = [item for item in required if item not in text]
    if missing:
        raise SystemExit('APK release contract missing: ' + '; '.join(missing))
    if 'upload-artifact@v4' not in text:
        raise SystemExit('APK release contract must upload through upload-artifact@v4')
    if 'app-release-unsigned.apk' not in text:
        raise SystemExit('APK release contract must name the unsigned release APK explicitly')
    print('APK release artifact contract: PASS')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
