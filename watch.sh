#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v entr >/dev/null 2>&1; then
  echo "entr is not installed. On macOS: brew install entr"
  exit 1
fi

# Files to watch (Java/Kotlin/Compose, resources, C++ bridge, Gradle files)
watch_list=$(mktemp)
trap 'rm -f "$watch_list"' EXIT

find \
  app/src/main/java \
  app/src/main/kotlin \
  app/src/main/res \
  app/src/main/cpp \
  -type f -not -path '*/build/*' -not -path '*/.cxx/*' -not -path '*/.gradle/*' \
  -name '*.kt' -o -name '*.java' -o -name '*.xml' -o -name '*.cpp' -o -name '*.hpp' \
  > "$watch_list" || true

# Also watch key build files
printf '%s\n' \
  app/build.gradle.kts \
  build.gradle.kts \
  settings.gradle \
  settings.gradle.kts \
  >> "$watch_list"

echo "Watching for changes. Will build and run on each change..."
cat "$watch_list" | entr -r sh -c './build.sh && ./run.sh'


