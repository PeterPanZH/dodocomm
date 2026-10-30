#!/usr/bin/env bash
# Release APK smoke test (R8 / resource-shrink correctness) WITHOUT the debug harness.
#
# App: 嘟嘟通讯 (internal SMS2Bark). The release build has no DebugReceiver, so everything is driven through the real UI
# with uiautomator:
#   1. uninstall debug build (different signature), install release APK
#   2. cold start, screenshot home
#   3. Settings tab -> type Bark server URL (http://10.0.2.2:8787) + Device Key,
#      commit with focus traversal (TAB) + dismiss the IME, tap 测试连接 -> assert
#      mock Bark gets the test push (proves UI/settings/OkHttp under R8)
#   4. inject a real SMS via emulator console -> assert mock Bark gets the forwarded
#      push AND the release receiver works (manifest/SmsReceiver survive R8)
#   5. navigate 首页/记录/设置/保活向导 for screenshots release_*.png
#
# IME handling matters: after `adb input text` the soft keyboard stays up and covers
# the bottom navigation, so every tab tap must first dismiss the IME (dismiss_ime).
#
# Usage: scripts/release_smoke.sh [apk-path]
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh

PASS=0; FAIL=0; WARN=0
ok()   { echo "PASS: $*"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $*"; FAIL=$((FAIL+1)); }
warn() { echo "WARN: $*"; WARN=$((WARN+1)); }
hr()   { echo "----------------------------------------------------------------"; }

APK="${1:-$SMS2BARK/release/SMS2Bark-1.0.0.apk}"
UI="$SMS2BARK/scripts/ui.py"
LOG="$VERIFY/bark_requests.log"
SHOTS="$SMS2BARK/screenshots"
DBG_TAG="SMS2BARK"
TEST_BODY="嘟嘟通讯 验收短信 $(date +%s) 验证码 666888"
mkdir -p "$SHOTS"

ui() { "$PY" "$UI" "$@"; }
mock_count() { wc -l < "$LOG" 2>/dev/null | tr -d ' '; }

ime_shown() { "$ADB" shell dumpsys input_method 2>/dev/null | tr -d '\r' | grep -q "mInputShown=true"; }
dismiss_ime() {
  local i=0
  while ime_shown && [ "$i" -lt 4 ]; do
    "$ADB" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
    sleep 0.6; i=$((i+1))
  done
}
commit_focus() { "$ADB" shell input keyevent KEYCODE_TAB >/dev/null 2>&1; sleep 0.4; }  # TAB -> focus loss -> field commit
ui_tab() { dismiss_ime; ui tap-tab "$@" >/dev/null 2>&1; }

# wait_push <before-line-count> <body-substring> <timeout_s>
wait_push() {
  local before="$1" needle="$2" timeout="${3:-25}" i=0 now
  while [ "$i" -lt "$timeout" ]; do
    sleep 1; i=$((i+1))
    now=$(mock_count); now=${now:-0}
    if [ "$now" -gt "$before" ] && tail -n +"$((before+1))" "$LOG" | grep -qF "$needle"; then
      return 0
    fi
  done
  return 1
}

# wait_ui <substring> <timeout_s>
wait_ui() {
  local needle="$1" timeout="${2:-8}" i=0
  while [ "$i" -lt "$timeout" ]; do
    ui nodes 2>/dev/null | grep -qF "$needle" && return 0
    sleep 1; i=$((i+1))
  done
  return 1
}

echo "== release smoke @ $(date '+%F %T') =="
echo "apk=$APK"

# ---------------------------------------------------------------- 0. preflight
hr; echo "0) preflight"
echo "$APK" | grep -q "release" && ok "release APK path looks right" || warn "APK path does not contain 'release'"
[ -f "$APK" ] || { bad "release APK not found: $APK"; echo "RESULT: FAIL"; exit 1; }
echo "  apk size: $(wc -c < "$APK") bytes"
echo "  apk sha256: $(shasum -a 256 "$APK" | cut -d' ' -f1)"
APK_LABEL=$("$ANDROID_HOME/build-tools/37.0.0/aapt2" dump badging "$APK" 2>/dev/null | grep -m1 "application-label:" | cut -d: -f2- | tr -d "'")
echo "  apk label: $APK_LABEL"
[ "$APK_LABEL" = "嘟嘟通讯" ] && ok "release APK label is 嘟嘟通讯" || warn "release APK label unexpected: $APK_LABEL"
"$ADB" devices | grep -q "emulator-.*device" && ok "emulator connected" || { bad "no emulator"; echo "RESULT: FAIL"; exit 1; }
"$ADB" shell svc wifi disable >/dev/null 2>&1
"$ADB" shell settings put global captive_portal_mode 0 >/dev/null 2>&1
"$ADB" shell svc power stayon true >/dev/null 2>&1
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
curl -s -m 3 "http://127.0.0.1:$MOCK_PORT/health" | grep -q '"ok"' && ok "mock bark healthy" || { bad "mock bark down"; echo "RESULT: FAIL"; exit 1; }

# ---------------------------------------------------------------- 1. install
hr; echo "1) install release APK (uninstall debug first)"
"$ADB" uninstall "$APP_ID" 2>&1 | tr -d '\r' | tail -1
INSTALL_OUT=$("$ADB" install -r "$APK" 2>&1 | tr -d '\r' | tail -2)
echo "$INSTALL_OUT"
echo "$INSTALL_OUT" | grep -q "Success" && ok "release APK installed" || { bad "release install failed"; echo "RESULT: FAIL"; exit 1; }
"$ADB" shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$APP_ID" && ok "package present" || bad "package missing"
if "$ADB" shell dumpsys package "$APP_ID" 2>/dev/null | tr -d '\r' | grep -q "DEBUGGABLE"; then
  bad "installed package is DEBUGGABLE (release expected)"
else
  ok "installed package is not debuggable"
fi
for perm in android.permission.RECEIVE_SMS android.permission.POST_NOTIFICATIONS android.permission.READ_PHONE_STATE; do
  "$ADB" shell pm grant "$APP_ID" "$perm" 2>/dev/null && ok "granted ${perm##*.}" || warn "grant failed ${perm##*.}"
done

# ---------------------------------------------------------------- 2. launch
hr; echo "2) launch + cold start"
"$ADB" shell am force-stop "$APP_ID"
sleep 1
LAUNCH=$("$ADB" shell am start -W -n "$APP_ID/.ui.MainActivity" 2>&1 | tr -d '\r')
echo "$LAUNCH" | grep -E "Status|TotalTime|WaitTime" | sed 's/^/  /'
echo "$LAUNCH" | grep -q "Status: ok" && ok "release MainActivity launched" || bad "release launch failed"
sleep 3
dismiss_ime
ui screenshot "$SHOTS/release_home.png" >/dev/null
ui nodes 2>/dev/null | head -5 | sed 's/^/  ui: /'
wait_ui "首页" 10 && ok "home UI rendered (首页 tab visible)" || warn "home tab label not found"
"$ADB" logcat -c 2>/dev/null

# ---------------------------------------------------------------- 3. configure
hr; echo "3) configure Bark server through the real UI"
dismiss_ime
ui_tab "设置"
sleep 2
if wait_ui "BARK 服务器" 8 || wait_ui "服务器地址" 3; then
  ok "settings screen opened"
else
  warn "settings screen markers not found; trying coordinate tap"
  "$ADB" shell input tap 907 2272 >/dev/null 2>&1
  sleep 2
fi
echo "  edit fields before fill:"; ui edits | sed 's/^/  /'
# field 0 = 服务器地址, field 1 = Device Key (settings screen order)
ui fill-field 0 "http://10.0.2.2:$MOCK_PORT" 40 >/dev/null 2>&1
commit_focus
ui fill-field 1 "testdevicekey" 40 >/dev/null 2>&1
commit_focus
dismiss_ime
sleep 1
echo "  edit fields after fill:"; ui edits | sed 's/^/  /'
if ui nodes 2>/dev/null | grep -qF "http://10.0.2.2:$MOCK_PORT"; then
  ok "server URL text is in the field"
else
  warn "server URL not visible in dump (may be committed/awaited)"
fi
if ui nodes 2>/dev/null | grep -q "••••"; then ok "Device Key committed (masked)"; else warn "Device Key masked text not found"; fi
dismiss_ime
ui screenshot "$SHOTS/release_settings.png" >/dev/null

# ---------------------------------------------------------------- 4. test push
hr; echo "4) 测试连接 -> mock Bark"
dismiss_ime
ui scroll-to "测试连接" 4 >/dev/null 2>&1
BEFORE=$(mock_count); BEFORE=${BEFORE:-0}
ui tap-text "测试连接" 2>&1 | sed 's/^/  /'
sleep 1
if wait_push "$BEFORE" "嘟嘟通讯 测试推送" 25; then
  ok "release test push reached mock Bark (settings/OkHttp/R8 OK)"
  tail -n +"$((BEFORE+1))" "$LOG" | tail -1 | cut -c1-460 | sed 's/^/  push: /'
else
  bad "release test push NOT received by mock Bark"
  echo "  last UI state:"; ui nodes 2>/dev/null | head -10 | sed 's/^/  ui: /'
fi
dismiss_ime
ui screenshot "$SHOTS/release_settings_configured.png" >/dev/null

# ---------------------------------------------------------------- 5. SMS E2E
hr; echo "5) real SMS -> release receiver -> mock Bark"
ui_tab "首页"
sleep 2
BEFORE=$(mock_count); BEFORE=${BEFORE:-0}
"$ADB" logcat -c 2>/dev/null
"$ADB" emu sms send 10086 "$TEST_BODY" >/dev/null 2>&1
echo "  sms inject rc=$? body=$TEST_BODY"
if wait_push "$BEFORE" "$TEST_BODY" 25; then
  ok "release build forwarded the real SMS to mock Bark"
  tail -n +"$((BEFORE+1))" "$LOG" | tail -1 | cut -c1-460 | sed 's/^/  push: /'
else
  bad "release build did NOT forward the SMS within 25s"
fi
echo "  pipeline logcat (release):"
"$ADB" logcat -d -s "$DBG_TAG" 2>/dev/null | tr -d '\r' | tail -6 | sed 's/^/  /'
if "$ADB" shell dumpsys activity services "$APP_ID" 2>/dev/null | tr -d '\r' | grep -q "ForwardService"; then
  ok "release foreground service running"
else
  warn "release ForwardService not visible in dumpsys"
fi
N_TITLES=$("$ADB" shell dumpsys notification --noredact 2>/dev/null | tr -d '\r' | grep -E "android.title=String" | head -4)
echo "  notification titles: $N_TITLES"
if echo "$N_TITLES" | grep -q "嘟嘟通讯"; then ok "FGS notification title uses 嘟嘟通讯"; else warn "notification title missing 嘟嘟通讯"; fi
sleep 2
ui_tab "首页"
sleep 2
dismiss_ime
ui screenshot "$SHOTS/release_home.png" >/dev/null

# ---------------------------------------------------------------- 6. screenshots/tour
hr; echo "6) UI tour (release screenshots)"
ui_tab "记录"
sleep 2
if wait_ui "$TEST_BODY" 8; then ok "history row visible in release UI"; else warn "history row text not visible in dump"; fi
if ui nodes 2>/dev/null | grep -qF "已送达"; then ok "history shows 已送达"; else warn "history 已送达 not visible"; fi
ui screenshot "$SHOTS/release_history.png" >/dev/null
ui_tab "首页"
sleep 2
ui tap-text "保活向导" >/dev/null 2>&1
sleep 2
ui screenshot "$SHOTS/release_keepalive.png" >/dev/null
if wait_ui "向导机型" 6; then ok "keep-alive guide opens in release build (incl. 向导机型 switcher)"; else warn "guide screen markers not found"; fi
"$ADB" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
sleep 1
hr; echo "7) brand audit (嘟嘟通讯 / no old brand)"
ui_tab "首页"
sleep 2
HOME_DUMP=$(ui nodes 2>/dev/null)
if echo "$HOME_DUMP" | grep -qF "嘟嘟通讯"; then ok "home hero shows 嘟嘟通讯"; else bad "home hero missing 嘟嘟通讯"; fi
if echo "$HOME_DUMP" | grep -qF "短信转 Bark"; then bad "old brand 短信转 Bark still visible on home"; else ok "no old brand string on screen"; fi

# ---------------------------------------------------------------- summary
hr
echo "SUMMARY: PASS=$PASS FAIL=$FAIL WARN=$WARN"
if [ "$FAIL" -eq 0 ]; then echo "RESULT: RELEASE SMOKE PASS"; exit 0; else echo "RESULT: RELEASE SMOKE FAIL"; exit 1; fi
