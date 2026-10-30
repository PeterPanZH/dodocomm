#!/usr/bin/env bash
# Prepare the headless API36 emulator for App E2E + screenshots.
# Discovered quirk: with virtio-wifi enabled (default), the WiFi network's
# virtio netsim path does NOT forward 10.0.2.2 to the host (connection refused)
# and has no outbound TCP. Disabling WiFi makes cellular/LTE via eth0 the
# default network, where classic slirp maps 10.0.2.2 -> host loopback. Verified:
# mock Bark /health reachable from device at http://10.0.2.2:8787.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source scripts/env.sh

echo "== prepare_emulator =="
"$ADB" wait-for-device
"$ADB" shell svc wifi disable 2>/dev/null
"$ADB" shell settings put global wifi_on 0 2>/dev/null
sleep 3
# The emulated cellular/eth0 network only reports PARTIAL_CONNECTIVITY (IPv6
# probes fail), which leaves NET_CAPABILITY_VALIDATED unset. WorkManager's
# network-constrained retry job would then never become runnable. Ignoring
# captive-portal probing makes ConnectivityService validate it immediately.
"$ADB" shell settings put global captive_portal_mode 0 2>/dev/null
"$ADB" shell settings put global captive_portal_detection_enabled 0 2>/dev/null
"$ADB" shell svc data disable 2>/dev/null
sleep 1
"$ADB" shell svc data enable 2>/dev/null
sleep 5
# keep screen on / unlock
"$ADB" shell svc power stayon true 2>/dev/null
"$ADB" shell settings put system screen_off_timeout 2147483647 2>/dev/null
"$ADB" shell input keyevent KEYCODE_WAKEUP 2>/dev/null
"$ADB" shell wm dismiss-keyguard 2>/dev/null
"$ADB" shell input keyevent 82 2>/dev/null
# animation scale off = faster UI transitions for uiautomator (optional)
"$ADB" shell settings put global window_animation_scale 0 2>/dev/null
"$ADB" shell settings put global transition_animation_scale 0 2>/dev/null
"$ADB" shell settings put global animator_duration_scale 0 2>/dev/null
# drop stale reverse tunnels; used only as fallback
"$ADB" reverse --remove-all 2>/dev/null

echo "-- connectivity:"
"$ADB" shell 'dumpsys connectivity | grep -m1 "Active default network"'
echo "-- reachability of host mock bark from device:"
RES=$("$ADB" shell 'printf "GET /health HTTP/1.0\r\nHost: 10.0.2.2\r\n\r\n" | toybox nc -w 3 10.0.2.2 8787' 2>&1 | head -8)
if echo "$RES" | grep -q '"ok"'; then
  echo "HOST_REACHABLE 10.0.2.2:8787 OK"
  exit 0
fi
echo "HOST_UNREACHABLE via 10.0.2.2:8787: $RES"
# fallback: adb reverse tunnel, app URL would be http://127.0.0.1:8787
echo "-- trying adb reverse fallback:"
"$ADB" reverse tcp:8787 tcp:8787 >/dev/null 2>&1
RES2=$("$ADB" shell 'printf "GET /health HTTP/1.0\r\n\r\n" | toybox nc -w 3 127.0.0.1 8787' 2>&1 | head -8)
if echo "$RES2" | grep -q '"ok"'; then
  echo "HOST_REACHABLE via adb reverse 127.0.0.1:8787 OK (use http://127.0.0.1:8787)"
  exit 0
fi
echo "HOST_UNREACHABLE fallback: $RES2"
exit 1
