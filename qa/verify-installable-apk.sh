#!/usr/bin/env bash
set -euo pipefail

APK="${1:?usage: verify-installable-apk.sh <apk> [expected-application-id]}"
EXPECTED_APPLICATION_ID="${2:-com.ruckus.agent}"

[[ -s "$APK" ]] || { echo "APK missing or empty: $APK" >&2; exit 2; }

APKSIGNER="$(find "${ANDROID_HOME:?}/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
APKANALYZER="${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$APKSIGNER" ]] || { echo "apksigner not found" >&2; exit 3; }
[[ -x "$APKANALYZER" ]] || { echo "apkanalyzer not found" >&2; exit 3; }

"$APKSIGNER" verify --verbose "$APK"

APPLICATION_ID="$("$APKANALYZER" manifest application-id "$APK")"
[[ "$APPLICATION_ID" == "$EXPECTED_APPLICATION_ID" ]] || {
  echo "unexpected application id: $APPLICATION_ID (expected $EXPECTED_APPLICATION_ID)" >&2
  exit 4
}

VERSION_CODE="$("$APKANALYZER" manifest version-code "$APK")"
VERSION_NAME="$("$APKANALYZER" manifest version-name "$APK")"
[[ "$VERSION_CODE" =~ ^[0-9]+$ && "$VERSION_CODE" -gt 0 ]] || {
  echo "invalid version code: $VERSION_CODE" >&2
  exit 5
}
[[ -n "$VERSION_NAME" && "$VERSION_NAME" != "null" ]] || {
  echo "missing version name" >&2
  exit 6
}

printf 'installable APK contract: PASS\napplication_id=%s\nversion_code=%s\nversion_name=%s\n' \
  "$APPLICATION_ID" "$VERSION_CODE" "$VERSION_NAME"
