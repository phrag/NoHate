#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Usage: ./build.sh [debug|release]
MODE="${1:-debug}"

if [[ "$MODE" == "release" ]]; then
  ./gradlew :app:assembleRelease :app:bundleRelease
  APK_PATH=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -n1 || true)
  NAME="NoHate-release-$(date +%Y%m%d-%H%M%S).apk"
else
  ./gradlew :app:assembleDebug :app:installDebug
  APK_PATH=$(ls -t app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n1 || true)
  NAME="NoHate-debug-$(date +%Y%m%d-%H%M%S).apk"
fi

if [[ -n "${APK_PATH:-}" && -f "$APK_PATH" ]]; then
  cp -f "$APK_PATH" "./$NAME"
  echo "Copied APK to $NAME"
else
  echo "APK not found at expected location."
fi

echo "Build complete ($MODE)."

