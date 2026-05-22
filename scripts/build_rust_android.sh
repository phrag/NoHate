#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JNI_LIBS_DIR="${PROJ_ROOT}/app/src/main/jniLibs"

mkdir -p "${JNI_LIBS_DIR}"

# Resolve NDK: prefer ANDROID_NDK_HOME, then ANDROID_NDK_ROOT (set by GitHub-hosted runners),
# then the default SDK location used by Android Studio.
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
    export ANDROID_NDK_HOME="${ANDROID_NDK_ROOT}"
  elif [[ -d "${HOME}/Library/Android/sdk/ndk" ]]; then
    ANDROID_NDK_HOME="$(ls -d "${HOME}/Library/Android/sdk/ndk"/*/  2>/dev/null | sort -V | tail -n1)"
    export ANDROID_NDK_HOME="${ANDROID_NDK_HOME%/}"
  fi
fi
echo "Using NDK: ${ANDROID_NDK_HOME:-<not set, cargo-ndk will auto-detect>}"

pushd "${PROJ_ROOT}/rust/core" >/dev/null
if ! command -v cargo-ndk >/dev/null 2>&1; then
	echo "cargo-ndk is required. Install with: cargo install cargo-ndk"
	exit 1
fi

cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "${JNI_LIBS_DIR}" build --release
popd >/dev/null

echo "Built Rust core and copied .so files to ${JNI_LIBS_DIR}"
