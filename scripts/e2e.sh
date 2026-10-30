#!/usr/bin/env bash
# End-to-end verification for sms2bark on the headless Android 16 emulator.
#
# What it proves:
#   1. debug APK installs and MainActivity launches
#   2. debug harness applies Bark config (server http://10.0.2.2:8787, key testdevicekey)
#   3. a REAL SMS injected through the emulator console reaches the app receiver
#   4. the app POSTs the expected Bark V2 JSON to the mock server (title/body/device_key/group)
#   5. history row is written with status SENT
#   6. the specialUse foreground service is running
#   7. the "test push" path (ForwardEngine.sendTestPush via debug action) also delivers
#
# Env overrides: APK, TEST_SENDER, TEST_BODY, WAIT_PUSH_S, REQUIRE_HISTORY, MOCK_PORT, INSTALL
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh

PASS=0; FAIL=0; WARN=0
ok()   { echo "PASS: $*"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $*"; FAIL=$((FAIL+1)); }
warn() { echo "WARN: $*"; WARN=$((WARN+1)); }
hr()   { echo "----------------------------------------------------------------"; }

APK="${APK:-$SMS2BARK/app/build/outputs/apk/debug/app-debug.apk}"
WAIT_PUSH_S="${WAIT_PUSH_S:-15}"
REQUIRE_HISTORY="${REQUIRE_HISTORY:-1}"
INSTALL="${INSTALL:-1}"
TS=$(date +%s)
TEST_SENDER="${TEST_SENDER:-10086}"
TEST_BODY="${TEST_BODY:-E2E 验证码 ${TS} 你好}"
DEVICE_KEY="${DEVICE_KEY:-testdevicekey}"
LOG="$VERIFY/bark_requests.log"
DBG_TAG="SMS2BARK_DEBUG"

echo "== sms2bark E2E @ $(date '+%F %T') =="
echo "app=$APP_ID apk=$APK sender=$TEST_SENDER body=$TEST_BODY"

dbg() { # dbg <action> [am extras...]
  # NOTE: explicit component is REQUIRED. On Android 16 an implicit broadcast to a
  # manifest receiver is "skipped by policy: Background execution not allowed"
  # unless the app is in the foreground; `-n <pkg>/<receiver>` bypasses that.
  local action="$1"; shift
  "$ADB" shell am broadcast -n "$APP_ID/.debug.DebugReceiver" -f 0x00000020 -a "$action" "$@" 2>&1 | tr -d '\r' | tail -2
}
logcat_dbg() { "$ADB" logcat -d -s "$DBG_TAG" 2>/dev/null | tr -d '\r'; }
wait_log() { # wait_log <ere-pattern> <timeout_s>
  local pat="$1" timeout="${2:-10}" t=0
  while [ "$t" -lt "$timeout" ]; do
    if logcat_dbg | grep -qE "$pat"; then return 0; fi
    sleep 1; t=$((t+1))
  done
  return 1
}
mock_count() { wc -l < "$LOG" 2>/dev/null | tr -d ' '; }

# ---------------------------------------------------------------- 1. device
hr; echo "1) emulator / adb"
if ! "$ADB" devices | grep -q "emulator-.*device"; then
  bad "no adb emulator device"; echo "RESULT: E2E FAIL"; exit 1
fi
ok "adb device: $("$ADB" devices | grep emulator | head -1 | tr -d '\r')"
BOOT=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
[ "$BOOT" = "1" ] && ok "sys.boot_completed=1" || bad "sys.boot_completed=$BOOT"
# API36 virtio-wifi quirk: wifi path cannot reach host 10.0.2.2; disable it so the
# default network is cellular/eth0 (classic slirp) where 10.0.2.2 maps to the host.
"$ADB" shell svc wifi disable >/dev/null 2>&1
"$ADB" shell svc power stayon true >/dev/null 2>&1
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1

# ---------------------------------------------------------------- 2. mock
hr; echo "2) mock Bark server"
if ! curl -s -m 3 "http://127.0.0.1:$MOCK_PORT/health" | grep -q '"ok"'; then
  warn "mock server not running; starting it"
  ( "$PY" "$SMS2BARK/scripts/mock_bark_server.py" >"$VERIFY/mock_bark_server.log" 2>&1 & )
  sleep 2
fi
HEALTH=$(curl -s -m 3 "http://127.0.0.1:$MOCK_PORT/health")
if echo "$HEALTH" | grep -q '"ok"'; then ok "mock bark healthy: $HEALTH"; else bad "mock bark unhealthy: $HEALTH"; echo "RESULT: E2E FAIL"; exit 1; fi

# ---------------------------------------------------------------- 3. install
hr; echo "3) install / permissions"
if [ "$INSTALL" = "1" ] && [ -f "$APK" ]; then
  INSTALL_OUT=$("$ADB" install -r "$APK" 2>&1 | tr -d '\r' | tail -2)
  echo "$INSTALL_OUT"
  if echo "$INSTALL_OUT" | grep -q "Success"; then ok "installed $APK"; else bad "install failed: $INSTALL_OUT"; fi
else
  warn "skip install (APK missing or INSTALL=$INSTALL)"
fi
if "$ADB" shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$APP_ID"; then
  ok "package present: $APP_ID"
else
  bad "package missing: $APP_ID"; echo "RESULT: E2E FAIL"; exit 1
fi
for perm in android.permission.RECEIVE_SMS android.permission.POST_NOTIFICATIONS android.permission.READ_PHONE_STATE; do
  if "$ADB" shell pm grant "$APP_ID" "$perm" 2>/dev/null; then ok "granted ${perm##*.}"; else warn "grant failed ${perm##*.}"; fi
done

# launch once (leaves stopped state, legal FGS start from foreground, refreshes UI)
if "$ADB" shell am start -W -n "$APP_ID/.ui.MainActivity" 2>&1 | tr -d '\r' | grep -qE "Status: ok|Activity:"; then
  ok "MainActivity launched"
else
  warn "MainActivity launch output unexpected"
fi
sleep 2
"$ADB" logcat -c 2>/dev/null

# ---------------------------------------------------------------- 4. config
hr; echo "4) debug harness config"
before_lines=$(mock_count); before_lines=${before_lines:-0}
dbg com.dodocomm.app.debug.CLEAR_DATA
sleep 1
SERVER_URL="http://10.0.2.2:$MOCK_PORT"
dbg com.dodocomm.app.debug.SET_CONFIG --es server "$SERVER_URL" --es key "$DEVICE_KEY" --es theme LIGHT
sleep 1
dbg com.dodocomm.app.debug.DUMP_STATE
if wait_log "STATE forwarding=true server=$SERVER_URL" 8; then
  ok "config applied (forwarding=true, server=$SERVER_URL)"
else
  bad "config not confirmed: $(logcat_dbg | grep STATE | tail -1)"
fi
logcat_dbg | grep -E "SET_CONFIG" | tail -2 | sed 's/^/  /'

# ---------------------------------------------------------------- 5. SMS in
hr; echo "5) inject real SMS via emulator console"
"$ADB" emu sms send "$TEST_SENDER" "$TEST_BODY" >"$TMPDIR/emu_sms_out" 2>&1
INJECT_RC=$?
if [ $INJECT_RC -eq 0 ]; then ok "adb emu sms send -> OK"; else bad "sms inject failed rc=$INJECT_RC: $(cat "$TMPDIR/emu_sms_out")"; fi

# ---------------------------------------------------------------- 6. push
hr; echo "6) mock server received push?"
FOUND=0
for i in $(seq 1 "$WAIT_PUSH_S"); do
  sleep 1
  NOW=$(mock_count); NOW=${NOW:-0}
  if [ "$NOW" -gt "$before_lines" ]; then FOUND=1; break; fi
done
if [ "$FOUND" = "1" ]; then ok "mock bark received request after ${i}s"; else bad "no request within ${WAIT_PUSH_S}s"; fi
NEW=$(tail -n +"$((before_lines+1))" "$LOG" 2>/dev/null)
echo "--- new bark requests:"; echo "${NEW:-<none>}"

PUSH_CHECK=1
if "$PY" - "$LOG" "$before_lines" "$TEST_BODY" "$DEVICE_KEY" <<'PYEOF'
import json, sys
log, before, body, key = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
try:
    lines = open(log, encoding="utf-8").read().splitlines()
except Exception:
    lines = []
found = None
for ln in lines[before:]:
    try:
        e = json.loads(ln)
    except Exception:
        continue
    if e.get("method") == "POST" and e.get("path", "").rstrip("/") == "/push":
        found = e
if not found:
    print("PUSH_CHECK FAIL no POST /push"); sys.exit(1)
j = found.get("json") or {}
problems = []
if body not in (j.get("body") or ""):
    problems.append("body missing injected text")
if j.get("device_key") != key:
    problems.append("device_key=%r != %r" % (j.get("device_key"), key))
if not (j.get("title") or ""):
    problems.append("title empty")
if problems:
    print("PUSH_CHECK FAIL " + "; ".join(problems)); sys.exit(1)
print("PUSH_CHECK OK title=%r device_key=%r group=%r body=%r" % (
    j.get("title"), j.get("device_key"), j.get("group"), (j.get("body") or "")[:80]))
PYEOF
then PUSH_CHECK=0; fi
if [ "$PUSH_CHECK" -eq 0 ]; then ok "POST /push payload fields correct"; else bad "POST /push payload check failed"; fi

# ---------------------------------------------------------------- 7. history
hr; echo "7) history shows SENT"
dbg com.dodocomm.app.debug.DUMP_HISTORY >/dev/null
if wait_log "HISTORY_COUNT=" 10; then
  ok "DUMP_HISTORY responded: $(logcat_dbg | grep HISTORY_COUNT | tail -1)"
else
  bad "DUMP_HISTORY no response"
fi
logcat_dbg > "$TMPDIR/history_dump.log"
if [ "$REQUIRE_HISTORY" = "1" ]; then
  HIST_CHECK=1
  if "$PY" - "$TMPDIR/history_dump.log" "$TEST_BODY" <<'PYEOF'
import json, sys
path, body = sys.argv[1], sys.argv[2]
rows = []
try:
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
except Exception:
    lines = []
for ln in lines:
    if "HISTORY_ROW " in ln:
        raw = ln.split("HISTORY_ROW ", 1)[1].strip()
        try:
            rows.append(json.loads(raw))
        except Exception:
            pass
match = [r for r in rows if body in (r.get("body") or "")]
if not match:
    print("HISTORY_CHECK FAIL no row containing injected SMS body (rows=%d)" % len(rows)); sys.exit(1)
r = match[0]
if r.get("status") != "SENT":
    print("HISTORY_CHECK FAIL status=%r error=%r" % (r.get("status"), r.get("error"))); sys.exit(1)
print("HISTORY_CHECK OK id=%s status=SENT title=%r sender=%r" % (
    r.get("id"), r.get("title"), r.get("sender")))
PYEOF
  then HIST_CHECK=0; fi
  if [ "$HIST_CHECK" -eq 0 ]; then ok "history row SENT with injected SMS"; else bad "history SENT check failed"; fi
fi

# ---------------------------------------------------------------- 8. test push
hr; echo "8) test push path"
before_push=$(mock_count); before_push=${before_push:-0}
dbg com.dodocomm.app.debug.TEST_PUSH >/dev/null
if wait_log "TEST_PUSH_RESULT " 25; then
  RES=$(logcat_dbg | grep "TEST_PUSH_RESULT " | tail -1 | tr -d '\r')
  echo "  $RES"
  if echo "$RES" | grep -q "success=true"; then ok "TEST_PUSH success=true"; else bad "TEST_PUSH not successful: $RES"; fi
else
  bad "TEST_PUSH no result within 25s"
fi
sleep 2
after_push=$(mock_count); after_push=${after_push:-0}
if [ "$after_push" -gt "$before_push" ]; then
  ok "mock bark received test push ($((after_push-before_push)) new request(s))"
else
  warn "no new mock request after TEST_PUSH"
fi

# ---------------------------------------------------------------- 9. FGS
hr; echo "9) foreground service + receiver evidence"
SVC_DUMP=$("$ADB" shell dumpsys activity services "$APP_ID" 2>/dev/null | tr -d '\r')
if echo "$SVC_DUMP" | grep -q "$APP_ID"; then
  ok "service record present: $(echo "$SVC_DUMP" | grep -E "ServiceRecord" | head -1 | cut -c1-140)"
  if echo "$SVC_DUMP" | grep -qE "isForeground=true|foreground=true"; then ok "FGS isForeground=true"; else warn "FGS foreground flag not found in dumpsys"; fi
else
  warn "no service record in dumpsys (may have been stopped)"
fi
if logcat_dbg | grep -qE "SmsReceiver|SMS_RECEIVED|handleIncoming|ForwardEngine"; then
  ok "pipeline logcat markers present"
  logcat_dbg | grep -E "SmsReceiver|SMS_RECEIVED|handleIncoming|ForwardEngine" | tail -3 | sed 's/^/  /'
else
  warn "no pipeline logcat markers in SMS2BARK_DEBUG tag (pipeline may use another tag)"
fi
echo "--- last debug log lines:"
logcat_dbg | tail -6 | sed 's/^/  /'

# ---------------------------------------------------------------- summary
hr
echo "SUMMARY: PASS=$PASS FAIL=$FAIL WARN=$WARN"
if [ "$FAIL" -eq 0 ]; then echo "RESULT: E2E PASS"; exit 0; else echo "RESULT: E2E FAIL"; exit 1; fi
