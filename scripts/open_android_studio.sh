#!/usr/bin/env bash
set -euo pipefail

# Opens this project in Android Studio on macOS.
# Usage: ./scripts/open_android_studio.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if ! command -v open >/dev/null 2>&1; then
	echo "This helper requires macOS 'open' command."
	exit 1
fi

open -a "Android Studio" "${PROJ_ROOT}" || {
	echo "Failed to launch Android Studio. Ensure it is installed in /Applications."
	exit 1
}
