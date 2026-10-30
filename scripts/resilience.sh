#!/usr/bin/env bash
# Phase-1 resilience checks for sms2bark (run after scripts/e2e.sh passes).
#   a) failure + automatic WorkManager retry: wrong port -> FAILED row -> config fixed
#      -> retry worker flips the SAME row to SENT and mock bark gets the delayed POST.
#   b) not-configured path: CLEAR_DATA -> SMS -> FAILED row with 未配置 error, no crash.
#   c) reboot self-heal: adb reboot -> FGS restored via BOOT_COMPLETED, TEST_PUSH works.
#
# Usage: scripts/resilience.sh [a|b|c|all]     (default: a b)
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh

PASS=0; FAIL=0; WARN=0
ok()   { echo "PASS: $*"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $*"; FAIL=$((FAIL+1)); }
warn() { echo "WARN: $*"; WARN=$((WARN+1)); }
hr()   { echo "----------------------------------------------------------------"; }

LOG="$VERIFY/bark_requests.log"
DBG_TAG="SMS2BARK_DEBUG"
PIPE_TAG="SMS2BARK"

dbg() { local action="$1"; shift; "$ADB" shell am broadcast -n "$APP_ID/.debug.DebugReceiver" -f 0x00000020 -a "$action" "$@" 2>&1 | tr -d '\r' | tail -1; }
logcat_dbg() { "$ADB" logcat -d -s "$DBG_TAG" 2>/dev/null | tr -d '\r'; }
logcat_pipe() { "$ADB" logcat -d -s "$PIPE_TAG" 2>/dev/null | tr -d '\r'; }
mock_count() { wc -l < "$LOG" 2>/dev/null | tr -d ' '; }
json_row() { # json_row <body-substring> [max_log_lines]
  logcat_dbg | grep "HISTORY_ROW " | grep -F "$1" | tail -1 | sed 's/.*HISTORY_ROW //'
}

wait_boot() {
  local t=0
  while [ "$t" -lt 240 ]; do
    if "$ADB" devices 2>/dev/null | grep -q "emulator-.*device" \
       && [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      return 0
    fi
    sleep 5; t=$((t+5))
  done
  return 1
}

ensure_ready() {
  "$ADB" wait-for-device
  "$ADB" shell svc wifi disable >/dev/null 2>&1
  "$ADB" shell settings put global captive_portal_mode 0 >/dev/null 2>&1
  "$ADB" shell svc power stayon true >/dev/null 2>&1
  "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
}

# ---------------------------------------------------------------- scenario A
scenario_a() {
  hr; echo "A) failure + WorkManager retry"
  local ts body sender before after recovered=0
  ts=$(date +%s); body="重试验证码 ${ts} 请忽略"; sender=10010
  ensure_ready
  "$ADB" shell am start -W -n "$APP_ID/.ui.MainActivity" >/dev/null 2>&1
  "$ADB" logcat -c 2>/dev/null
  dbg com.dodocomm.app.debug.CLEAR_DATA >/dev/null
  sleep 1
  # point at a closed port on purpose
  dbg com.dodocomm.app.debug.SET_CONFIG --es server http://10.0.2.2:8899 --es key testdevicekey >/dev/null
  sleep 1
  local before_lines; before_lines=$(mock_count); before_lines=${before_lines:-0}
  "$ADB" emu sms send "$sender" "$body" >/dev/null 2>&1
  local inject_rc=$?
  [ $inject_rc -eq 0 ] && ok "A: SMS injected (sender=$sender)" || bad "A: sms inject failed"
  sleep 7
  dbg com.dodocomm.app.debug.DUMP_HISTORY >/dev/null
  sleep 2
  local row; row=$(json_row "$body")
  echo "  history row after failed push: $row"
  if echo "$row" | grep -q '"status":"FAILED"'; then ok "A: history row FAILED after closed-port push"; else bad "A: expected FAILED row, got: $row"; fi
  local err; err=$(echo "$row" | sed -n 's/.*"error":"\([^"]*\)".*/\1/p')
  [ -n "$err" ] && ok "A: failure reason recorded: $err" || warn "A: no error text on failed row"
  logcat_pipe | grep -E "短信处理完成|连接|超时|HTTP" | tail -2 | sed 's/^/  pipe: /'

  # WorkManager job must be enqueued (network-constrained retry, backoff 15s)
  local jobs retry_job
  jobs=$("$ADB" shell dumpsys jobscheduler 2>/dev/null | tr -d '\r')
  retry_job=$(echo "$jobs" | grep -B12 "initial=+15s0ms" | grep -E "SystemJobService|pkg=com.dodocomm.app|Network type|Required constraints|Satisfied constraints|Unsatisfied constraints" | head -8)
  if echo "$retry_job" | grep -q "com.dodocomm.app"; then
    ok "A: network-constrained RetryWorker job enqueued in JobScheduler"
    echo "$retry_job" | sed 's/^/  job: /'
  else
    bad "A: RetryWorker job NOT found in JobScheduler"
  fi
  if echo "$retry_job" | grep -qE "Unsatisfied constraints:.*CONNECTIVITY"; then
    warn "A: retry job CONNECTIVITY unsatisfied (network not VALIDATED); re-applying captive_portal_mode=0 + data toggle"
    "$ADB" shell settings put global captive_portal_mode 0 >/dev/null 2>&1
    "$ADB" shell svc data disable >/dev/null 2>&1; sleep 1
    "$ADB" shell svc data enable >/dev/null 2>&1; sleep 5
  fi

  # fix the config BEFORE the first retry fires (backoff = 15s)
  dbg com.dodocomm.app.debug.SET_CONFIG --es server "http://10.0.2.2:$MOCK_PORT" --es key testdevicekey >/dev/null
  ok "A: config restored to http://10.0.2.2:$MOCK_PORT before retry"

  # wait for WorkManager retry to deliver (first backoff 15s, exponential)
  local i
  for i in $(seq 1 20); do
    sleep 5
    after=$(mock_count); after=${after:-0}
    if [ "$after" -gt "$before_lines" ]; then recovered=1; break; fi
    dbg com.dodocomm.app.debug.DUMP_HISTORY >/dev/null
    sleep 1
    row=$(json_row "$body")
    if echo "$row" | grep -q '"status":"SENT"'; then recovered=1; break; fi
  done
  dbg com.dodocomm.app.debug.DUMP_HISTORY >/dev/null
  sleep 2
  row=$(json_row "$body")
  echo "  history row after retry window (${i}x5s): $row"
  if echo "$row" | grep -q '"status":"SENT"'; then
    ok "A: SAME history row flipped FAILED -> SENT by WorkManager retry"
  else
    bad "A: row still not SENT after retry wait: $row"
  fi
  after=$(mock_count); after=${after:-0}
  if [ "$after" -gt "$before_lines" ]; then
    ok "A: mock bark received delayed retry POST ($((after-before_lines)) new request(s))"
    tail -n +"$((before_lines+1))" "$LOG" | grep -F "$body" | tail -1 | cut -c1-400 | sed 's/^/  retry: /'
  else
    bad "A: mock bark never received delayed retry POST"
  fi
  logcat_pipe | grep -E "短信处理完成" | tail -3 | sed 's/^/  pipe: /'
}

# ---------------------------------------------------------------- scenario B
scenario_b() {
  hr; echo "B) not-configured path (CLEAR_DATA -> SMS)"
  ensure_ready
  "$ADB" shell am start -W -n "$APP_ID/.ui.MainActivity" >/dev/null 2>&1
  "$ADB" logcat -c 2>/dev/null
  dbg com.dodocomm.app.debug.CLEAR_DATA >/dev/null
  sleep 1
  local ts body pid_before pid_after row
  ts=$(date +%s); body="未配置验证 ${ts}"
  pid_before=$("$ADB" shell pidof "$APP_ID" 2>/dev/null | tr -d '\r')
  "$ADB" emu sms send 95555 "$body" >/dev/null 2>&1
  sleep 6
  dbg com.dodocomm.app.debug.DUMP_HISTORY >/dev/null
  dbg com.dodocomm.app.debug.DUMP_STATE >/dev/null
  sleep 2
  row=$(json_row "$body")
  echo "  history row: $row"
  echo "  state: $(logcat_dbg | grep ' STATE ' | tail -1)"
  if echo "$row" | grep -q '"status":"FAILED"'; then ok "B: history row FAILED when not configured"; else bad "B: expected FAILED row: $row"; fi
  if echo "$row" | grep -q "未配置 Bark 服务器"; then ok "B: error message is '未配置 Bark 服务器…'"; else bad "B: wrong/missing not-configured error: $row"; fi
  if logcat_pipe | grep -q "已收到短信但未配置 Bark 服务器"; then ok "B: pipeline logged 未配置 warning (no crash)"; else warn "B: warning log line not found"; fi
  pid_after=$("$ADB" shell pidof "$APP_ID" 2>/dev/null | tr -d '\r')
  [ -n "$pid_after" ] && ok "B: app process alive after unconfigured SMS (pid=$pid_after)" || bad "B: app process died"
  if "$ADB" logcat -d -b crash 2>/dev/null | grep -q "$APP_ID"; then bad "B: crash buffer contains app crash"; else ok "B: no crash in crash buffer"; fi
}

# ---------------------------------------------------------------- scenario C
scenario_c() {
  hr; echo "C) reboot self-heal"
  # make sure we reboot with a configured app + forwarding enabled + FGS expected
  dbg com.dodocomm.app.debug.SET_CONFIG --es server "http://10.0.2.2:$MOCK_PORT" --es key testdevicekey >/dev/null
  sleep 1
  local before_lines; before_lines=$(mock_count); before_lines=${before_lines:-0}
  echo "  rebooting..."
  "$ADB" reboot >/dev/null 2>&1
  sleep 5
  if wait_boot; then ok "C: emulator rebooted, sys.boot_completed=1"; else bad "C: boot timeout after reboot"; return; fi
  ensure_ready
  sleep 8   # let BOOT_COMPLETED receivers + FGS start settle
  local svc; svc=$("$ADB" shell dumpsys activity services "$APP_ID" 2>/dev/null | tr -d '\r')
  if echo "$svc" | grep -q "ForwardService"; then
    ok "C: ForwardService restored after reboot"
    echo "$svc" | grep -E "ServiceRecord|isForeground" | head -2 | sed 's/^/  /'
  else
    bad "C: ForwardService NOT running after reboot"
  fi
  "$ADB" logcat -d -s "$PIPE_TAG" 2>/dev/null | tr -d '\r' | grep -iE "boot|服务|FGS|恢复" | head -3 | sed 's/^/  boot: /'
  dbg com.dodocomm.app.debug.DUMP_STATE >/dev/null
  sleep 2
  local state; state=$(logcat_dbg | grep ' STATE ' | tail -1)
  echo "  state: $state"
  if echo "$state" | grep -q "serviceRunning=true"; then ok "C: DUMP_STATE serviceRunning=true after reboot"; else warn "C: serviceRunning not true in DUMP_STATE"; fi
  dbg com.dodocomm.app.debug.TEST_PUSH >/dev/null
  sleep 6
  local res; res=$(logcat_dbg | grep "TEST_PUSH_RESULT " | tail -1)
  echo "  $res"
  if echo "$res" | grep -q "success=true"; then ok "C: TEST_PUSH works after reboot"; else bad "C: TEST_PUSH failed after reboot: $res"; fi
  local after; after=$(mock_count); after=${after:-0}
  [ "$after" -gt "$before_lines" ] && ok "C: mock bark got push after reboot" || warn "C: no new mock request after reboot test push"
}

MODE="${1:-all}"
case "$MODE" in
  a|A) scenario_a ;;
  b|B) scenario_b ;;
  c|C) scenario_c ;;
  all) scenario_a; scenario_b ;;
  *) echo "usage: resilience.sh [a|b|c|all]"; exit 2 ;;
esac
hr
echo "RESILIENCE($MODE): PASS=$PASS FAIL=$FAIL WARN=$WARN"
[ "$FAIL" -eq 0 ] && { echo "RESULT: PASS"; exit 0; } || { echo "RESULT: FAIL"; exit 1; }
