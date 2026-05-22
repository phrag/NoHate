#!/usr/bin/env bash
# Regenerate Paparazzi golden PNGs after an intentional UI change.
# Goldens land in app/src/test/snapshots/ and should be committed.
#
# Usage:
#   ./scripts/record_screenshots.sh            # debug variant
#   ./scripts/record_screenshots.sh release    # release variant

set -euo pipefail
cd "$(dirname "$0")/.."

VARIANT="${1:-Debug}"
# Capitalize first letter (lowercase->Title) without bashism.
VARIANT="$(echo "${VARIANT:0:1}" | tr 'a-z' 'A-Z')${VARIANT:1}"

./gradlew ":app:recordPaparazzi${VARIANT}" --no-daemon --stacktrace

echo
echo "Goldens updated under app/src/test/snapshots/."
echo "Review the diff and commit the new PNGs."
