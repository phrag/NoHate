#!/usr/bin/env bash
set -euo pipefail

# Runs the NoHate app on an Android emulator or connected device.
# - Starts the first available AVD if no device is connected
# - Waits for boot completion
# - Builds and installs :app:debug
# - Launches MainActivity

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Locate Android SDK tools
ANDROID_HOME_DEFAULT="$HOME/Library/Android/sdk"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

require_cmd() {
	command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1"; exit 1; }
}

require_cmd adb
require_cmd emulator || true

start_emulator_if_needed() {
	local devices
	devices=$(adb devices | tail -n +2 | awk '$2=="device" {print $1}')
	if [[ -n "$devices" ]]; then
		echo "Device(s) already connected: $devices"
		return 0
	fi
	if ! command -v emulator >/dev/null 2>&1; then
		echo "Android emulator not found in PATH. Ensure SDK is installed and PATH includes $ANDROID_HOME/emulator"
		exit 1
	fi
	local avd
	avd=$(emulator -list-avds | head -n1 || true)
	if [[ -z "$avd" ]]; then
		echo "No AVDs found. Create one in Android Studio (AVD Manager) and re-run."
		exit 1
	fi
	echo "Starting emulator: $avd"
	# Launch emulator in background
	emulator -avd "$avd" -no-snapshot -no-boot-anim -netdelay none -netspeed full >/dev/null 2>&1 &
}

wait_for_boot() {
	echo "Waiting for device..."
	adb wait-for-device
	# Wait for sys.boot_completed=1
	local booted="0"; local tries=0
	while [[ "$booted" != "1" && $tries -lt 120 ]]; do
		booted=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') || true
		sleep 2
		tries=$((tries+1))
		echo -n "."
	done

echo
	if [[ "$booted" != "1" ]]; then
		echo "Timed out waiting for emulator to boot."
		exit 1
	fi
	echo "Device is ready."
}

build_and_install() {
	cd "$PROJ_ROOT"
	./gradlew :app:installDebug
}

launch_app() {
	adb shell am start -n com.nohate.app/.MainActivity || {
		echo "Failed to launch activity."
		exit 1
	}
}

start_emulator_if_needed
wait_for_boot
build_and_install
launch_app

echo "App launched on device/emulator."

cp app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk

echo "App debug APK copied to ./app-debug.apk"