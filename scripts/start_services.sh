#!/usr/bin/env bash
# Start (idempotently) the long-lived verification services: mock Bark server and
# the headless API36 emulator. Processes are started in a NEW SESSION (setsid via
# python) so they survive the end of the agent turn.
# Usage: scripts/start_services.sh [--emulator-only|--mock-only] [--restart]
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh

ONLY="${1:-all}"
RESTART="${2:-}"

# detach <logfile> <cmd...>: exec cmd in a new session with stdout/stderr -> logfile
detach() {
  local log="$1"; shift
  "$PY" -c '
import os, sys
log_path = sys.argv[1]
os.setsid()
try:
    os.nice(5)
except Exception:
    pass
log = open(log_path, "ab", buffering=0)
os.dup2(log.fileno(), 1)
os.dup2(log.fileno(), 2)
try:
    os.execv(sys.argv[2], sys.argv[2:])
except Exception as e:
    sys.stderr.write("exec failed: %r\n" % (e,))
    sys.exit(1)
' "$log" "$@" &
}

# ---------------------------------------------------------------- mock bark
if [ "$ONLY" = "all" ] || [ "$ONLY" = "--mock-only" ]; then
  HEALTH=$(curl -s -m 2 "http://127.0.0.1:$MOCK_PORT/health" 2>/dev/null || true)
  if echo "$HEALTH" | grep -q '"ok"' && [ "$RESTART" != "--restart" ]; then
    echo "mock_bark: already running ($HEALTH)"
  else
    if lsof -nP -iTCP:"$MOCK_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "mock_bark: port $MOCK_PORT busy, killing stale listener"
      PID=$(lsof -nP -iTCP:"$MOCK_PORT" -sTCP:LISTEN -t 2>/dev/null | head -1)
      [ -n "$PID" ] && kill "$PID" 2>/dev/null
      sleep 1
    fi
    echo "mock_bark: starting (log $VERIFY/mock_bark_server.log)"
    detach "$VERIFY/mock_bark_server.log" "$PY" "$SMS2BARK/scripts/mock_bark_server.py"
    for i in 1 2 3 4 5 6 7 8 9 10; do
      sleep 0.5
      HEALTH=$(curl -s -m 2 "http://127.0.0.1:$MOCK_PORT/health" 2>/dev/null || true)
      if echo "$HEALTH" | grep -q '"ok"'; then break; fi
    done
    echo "mock_bark: $HEALTH"
  fi
fi

# ---------------------------------------------------------------- emulator
if [ "$ONLY" = "all" ] || [ "$ONLY" = "--emulator-only" ]; then
  if "$ADB" devices 2>/dev/null | grep -q "emulator-.*device"; then
    echo "emulator: already running ($("$ADB" devices | grep emulator | head -1 | tr -d '\r'))"
  else
    echo "emulator: starting $AVD_NAME (log $VERIFY/emulator.log)"
    : > "$VERIFY/emulator.log"
    detach "$VERIFY/emulator.log" "$EMU" -avd "$AVD_NAME" \
      -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot \
      -camera-back none -camera-front none -netdelay none -netspeed full
    echo "emulator: launched, waiting for boot (scripts/wait_boot.sh)"
  fi
fi
