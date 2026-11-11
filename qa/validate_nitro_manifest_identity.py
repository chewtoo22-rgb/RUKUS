from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
THEME = ROOT / "app/src/main/res/values/themes.xml"


def require(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, re.MULTILINE):
        raise AssertionError(f"missing {label}")


def main() -> int:
    manifest = MANIFEST.read_text(encoding="utf-8")
    theme = THEME.read_text(encoding="utf-8")
    require(manifest, r'android:label="NITRO"', "canonical application label")
    require(manifest, r'android:theme="@style/Theme\.Nitro"', "canonical application theme")
    require(manifest, r'android:name="\.control\.RuckusAccessibilityService"', "accessibility service")
    require(manifest, r'android:permission="android\.permission\.BIND_ACCESSIBILITY_SERVICE"', "accessibility binding")
    require(manifest, r'android:exported="false"', "non-exported accessibility service")
    require(manifest, r'android:name="rikka\.shizuku\.ShizukuProvider"', "Shizuku provider")
    require(manifest, r'android:authorities="\$\{applicationId\}\.shizuku"', "scoped Shizuku authority")
    require(manifest, r'android:exported="true"', "explicit provider export posture")
    require(manifest, r'android:name="\.MainActivity"', "launcher activity")
    require(manifest, r'android:name="\.MainActivity"[\s\S]*?android:exported="true"', "exported launcher activity")
    require(theme, r'<style name="Theme\.Nitro"', "canonical theme resource")
    if "Theme.Ruckus" in manifest or "Theme.Ruckus" in theme or 'android:label="RUCKUS"' in manifest:
        raise AssertionError("legacy RUCKUS identity remains in shipped manifest/theme")
    print("NITRO manifest identity contract: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
