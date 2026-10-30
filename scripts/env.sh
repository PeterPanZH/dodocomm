#!/usr/bin/env bash
# Shared environment for DodoComm build & verification scripts.
# Usage: source scripts/env.sh
# All paths can be overridden by the caller; no personal paths are hard-coded.
set -a
SMS2BARK="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WS="$(cd "$SMS2BARK/.." && pwd)"
export SMS2BARK WS

# Prefer a workspace-local toolchain when present (development checkouts);
# otherwise fall back to the standard Android/JDK environment variables.
if [ -d "$WS/toolchain/android-sdk" ]; then
  export ANDROID_HOME="${ANDROID_HOME:-$WS/toolchain/android-sdk}"
fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

JAVA_CANDIDATE="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
if [ -d "$JAVA_CANDIDATE" ] && [ -z "${JAVA_HOME:-}" ]; then
  export JAVA_HOME="$JAVA_CANDIDATE"
fi

if [ -d "$WS/.home" ] && [ -z "${DODOCOMM_KEEP_HOME:-}" ]; then
  export HOME="$WS/.home"
fi
if [ -d "$WS/.android-tools" ]; then
  export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$WS/.android-tools}"
  export ANDROID_EMULATOR_HOME="${ANDROID_EMULATOR_HOME:-$WS/.android-tools}"
fi
if [ -d "$WS/.android-avd" ]; then
  export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$WS/.android-avd}"
fi
if [ -d "$WS/toolchain/gradle-home" ]; then
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$WS/toolchain/gradle-home}"
fi
if [ -d "$WS/tmp" ]; then
  export TMPDIR="${TMPDIR:-$WS/tmp}"
fi

if [ -x "$WS/toolchain/gradle/gradle-9.6.0/bin/gradle" ]; then
  export GRADLE="${GRADLE:-$WS/toolchain/gradle/gradle-9.6.0/bin/gradle}"
else
  export GRADLE="${GRADLE:-$SMS2BARK/gradlew}"
fi

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
export ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
export EMU="${EMU:-$ANDROID_HOME/emulator/emulator}"
export SOME_PY="$(command -v python3 || true)"
export PY="${PY:-$SOME_PY}"

export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
export APP_ID=com.dodocomm.app
export MOCK_PORT="${MOCK_PORT:-8787}"
export AVD_NAME="${AVD_NAME:-dodocomm_api36}"
export VERIFY="${VERIFY:-$SMS2BARK/verify}"

mkdir -p "$ANDROID_SDK_ROOT" "$VERIFY" 2>/dev/null || true
set +a
