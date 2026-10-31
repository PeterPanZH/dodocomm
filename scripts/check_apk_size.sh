#!/usr/bin/env bash
# C25: keep the release APK small enough for a GitHub-download app.
set -euo pipefail
APK="app/build/outputs/apk/release/app-release-unsigned.apk"
BUDGET="6291456" # 6 MiB
if [ ! -f "$APK" ]; then
  echo "::error::Release APK not found at $APK"
  exit 1
fi
SIZE="$(stat -c%s "$APK")"
echo "Release APK size: ${SIZE} bytes (budget ${BUDGET})"
if [ "$SIZE" -gt "$BUDGET" ]; then
  echo "::error::APK exceeds the 6 MiB budget: ${SIZE} > ${BUDGET}"
  exit 1
fi
