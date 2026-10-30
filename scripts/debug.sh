#!/usr/bin/env bash
# Send one debug-harness command to the installed debug build via adb.
# Usage: scripts/debug.sh ACTION [am broadcast extras...]
#   scripts/debug.sh DUMP_STATE
#   scripts/debug.sh SET_CONFIG --es server http://10.0.2.2:8787 --es key testdevicekey
#   scripts/debug.sh SEED_DEMO
#   scripts/debug.sh TEST_PUSH
# NOTE: explicit component is required on Android 16 for manifest receivers while
# the app is backgrounded (otherwise "Background execution not allowed").
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh
ACTION="${1:?usage: debug.sh ACTION [extras...]}"
shift
"$ADB" shell am broadcast -n "$APP_ID/.debug.DebugReceiver" -f 0x00000020 \
  -a "com.dodocomm.app.debug.$ACTION" "$@" 2>&1 | tr -d '\r'
