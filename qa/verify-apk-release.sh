#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "usage: $0 <apk>" >&2
  exit 2
fi

APKAnalyzer="${APK_ANALYZER:-${ANDROID_HOME:-}/cmdline-tools/latest/bin/apkanalyzer}"
if [[ ! -x "$APKAnalyzer" ]]; then
  APKAnalyzer="$(command -v apkanalyzer || true)"
fi
if [[ -z "$APKAnalyzer" || ! -x "$APKAnalyzer" ]]; then
  echo "apkanalyzer not found" >&2
  exit 3
fi

fail() {
  echo "APK RELEASE GATE: FAIL: $*" >&2
  exit 1
}

expect_eq() {
  local label="$1" actual="$2" expected="$3"
  [[ "$actual" == "$expected" ]] || fail "$label expected '$expected' but got '$actual'"
}

APP_ID="$($APKAnalyzer manifest application-id "$APK")"
VERSION_CODE="$($APKAnalyzer manifest version-code "$APK")"
MIN_SDK="$($APKAnalyzer manifest min-sdk "$APK")"
TARGET_SDK="$($APKAnalyzer manifest target-sdk "$APK")"
DEBUGGABLE="$($APKAnalyzer manifest debuggable "$APK")"
PERMISSIONS="$($APKAnalyzer manifest permissions "$APK")"
MANIFEST="$($APKAnalyzer manifest print "$APK")"
FILES="$($APKAnalyzer files list "$APK")"

expect_eq "applicationId" "$APP_ID" "com.ruckus.agent"
expect_eq "minSdk" "$MIN_SDK" "29"
expect_eq "targetSdk" "$TARGET_SDK" "35"
expect_eq "debuggable" "$DEBUGGABLE" "false"

[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || fail "versionCode must be a positive integer"
grep -Fq 'android.permission.WRITE_SETTINGS' <<<"$PERMISSIONS" || fail "WRITE_SETTINGS permission missing"
grep -Fq 'android.permission.POST_NOTIFICATIONS' <<<"$PERMISSIONS" || fail "POST_NOTIFICATIONS permission missing"
grep -Fq 'com.ruckus.agent.control.RuckusAccessibilityService' <<<"$MANIFEST" || fail "RUKUS accessibility service missing"
grep -Fq 'android.permission.BIND_ACCESSIBILITY_SERVICE' <<<"$MANIFEST" || fail "accessibility service binding permission missing"
grep -Fq 'android:exported="false"' <<<"$MANIFEST" || fail "expected a non-exported privileged component"
grep -Fq 'classes.dex' <<<"$FILES" || fail "classes.dex missing"

if grep -Fq 'android:testOnly="true"' <<<"$MANIFEST"; then
  fail "release APK must not be testOnly"
fi

echo "APK RELEASE GATE: PASS"
echo "applicationId=$APP_ID"
echo "versionCode=$VERSION_CODE"
echo "minSdk=$MIN_SDK"
echo "targetSdk=$TARGET_SDK"
echo "debuggable=$DEBUGGABLE"
