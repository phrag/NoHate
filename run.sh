#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Ensure a device is connected
adb start-server >/dev/null 2>&1 || true

if ! adb get-state >/dev/null 2>&1; then
  echo "No device/emulator detected. Start one in Android Studio or run 'emulator -avd <name>'"
  exit 1
fi

APP_ID="com.nohate.app"
MAIN_ACTIVITY=".MainActivity"

./gradlew :app:installDebug
adb shell am start -n ${APP_ID}/${MAIN_ACTIVITY}

echo "App launched. Use: adb logcat | grep -i nohate"

