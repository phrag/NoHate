#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Usage: ./build.sh [debug|release]
MODE="${1:-debug}"

if [[ "$MODE" == "release" ]]; then
  ./gradlew :app:assembleRelease :app:bundleRelease
else
  ./gradlew :app:assembleDebug :app:installDebug
fi

echo "Build complete ($MODE)."

