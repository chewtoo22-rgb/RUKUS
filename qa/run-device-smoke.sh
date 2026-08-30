#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
APP_APK="${APP_APK:-app-debug.apk}"
TEST_APK="${TEST_APK:-app-debug-androidTest.apk}"
SERIAL="${ANDROID_SERIAL:-}"
RUNNER="${INSTRUMENTATION_RUNNER:-com.ruckus.agent.test/androidx.test.runner.AndroidJUnitRunner}"
ARTIFACT_DIR="${SMOKE_ARTIFACT_DIR:-rukus-device-smoke-results}"

mkdir -p "$ARTIFACT_DIR"

adb_cmd=("$ADB_BIN")
if [[ -n "$SERIAL" ]]; then
  adb_cmd+=("-s" "$SERIAL")
fi

fail() {
  printf 'RUKUS device smoke failed: %s\n' "$*" >&2
  "${adb_cmd[@]}" logcat -d > "$ARTIFACT_DIR/logcat.txt" 2>&1 || true
  exit 1
}

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "adb not found: $ADB_BIN"
[[ -s "$APP_APK" ]] || fail "app APK missing or empty: $APP_APK"
[[ -s "$TEST_APK" ]] || fail "instrumentation APK missing or empty: $TEST_APK"

"${adb_cmd[@]}" get-state 2>/dev/null | grep -qx device || fail "target device is not in adb device state"

"${adb_cmd[@]}" logcat -c || true
"${adb_cmd[@]}" install -r -t "$APP_APK" > "$ARTIFACT_DIR/install-app.txt" 2>&1 || fail "app APK installation failed"
"${adb_cmd[@]}" install -r -t "$TEST_APK" > "$ARTIFACT_DIR/install-test.txt" 2>&1 || fail "instrumentation APK installation failed"

set +e
"${adb_cmd[@]}" shell am instrument -w "$RUNNER" | tee "$ARTIFACT_DIR/instrumentation.txt"
status=${PIPESTATUS[0]}
set -e

if [[ $status -ne 0 ]] || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$ARTIFACT_DIR/instrumentation.txt"; then
  fail "instrumentation suite reported failure"
fi

"${adb_cmd[@]}" shell dumpsys package com.ruckus.agent > "$ARTIFACT_DIR/package.txt" 2>&1 || true
"${adb_cmd[@]}" logcat -d > "$ARTIFACT_DIR/logcat.txt" 2>&1 || true
printf 'RUKUS device smoke passed. Results: %s\n' "$ARTIFACT_DIR"
