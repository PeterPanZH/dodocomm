#!/usr/bin/env bash
# Wait for the headless emulator to finish booting (sys.boot_completed=1).
# Usage: scripts/wait_boot.sh [timeout_s] [interval_s]
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh
TIMEOUT_S="${1:-240}"
INTERVAL_S="${2:-10}"
START=$(date +%s)
while :; do
  DEV=$("$ADB" devices 2>/dev/null | grep -c "emulator-.*device$")
  BOOT=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  ANIM=$("$ADB" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')
  NOW=$(date +%s)
  echo "[$(date +%H:%M:%S)] devices=$DEV boot_completed=${BOOT:-?} bootanim=${ANIM:-?} elapsed=$((NOW-START))s"
  if [ "$DEV" -ge 1 ] && [ "$BOOT" = "1" ]; then
    echo "BOOT_OK after $((NOW-START))s"
    "$ADB" shell wm size 2>/dev/null
    exit 0
  fi
  if [ $((NOW-START)) -ge "$TIMEOUT_S" ]; then
    echo "BOOT_TIMEOUT after $((NOW-START))s"
    echo "--- emulator.log tail:"; tail -30 "$VERIFY/emulator.log" 2>/dev/null
    exit 1
  fi
  sleep "$INTERVAL_S"
done
