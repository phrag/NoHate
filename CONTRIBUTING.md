# Contributing to NoHate

## Dev setup

Prereqs:
- Android Studio + Android SDK + NDK (set `ANDROID_NDK_HOME`)
- JDK 17+
- Rust + cargo (`rustup`)
- `cargo-ndk` (`cargo install cargo-ndk`)

Initialize submodules (llama.cpp lives under `app/src/main/cpp/third_party/`):
```bash
git submodule update --init --recursive
```

Install Android Rust targets:
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

Build the Rust core and copy `.so` files into `app/src/main/jniLibs/`:
```bash
./scripts/build_rust_android.sh
```

Open in Android Studio and run on a device. Physical device is preferred for NN performance. If the Gradle wrapper is missing:
```bash
gradle wrapper
```

## Tests

Instrumented tests on an emulator or device:
```bash
./scripts/run_on_emulator.sh
./gradlew :app:connectedAndroidTest
```

Unit tests (when available):
```bash
./gradlew :app:testDebugUnitTest
```

## Project layout

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the module map and data flow.

Key directories:
- `app/src/main/java/com/nohate/app/classify/` — pluggable classifier backends (added in Phase 1).
- `app/src/main/java/com/nohate/app/bench/` — benchmark harness (Phase 3).
- `app/src/main/java/com/nohate/app/ui/` — Compose screens.
- `app/src/main/java/com/nohate/app/work/` — WorkManager pipeline.
- `app/src/main/assets/models/` — bundled models + `registry.json`.
- `app/src/main/assets/bench/` — bundled eval datasets.
- `rust/core/` — Rust JNI classifier core.

## Adding a new classifier model

1. Add an ONNX export recipe to `scripts/export_onnx.py` and quantize with `scripts/quantize_onnx.py`.
2. Record the model in [`docs/MODELS.md`](docs/MODELS.md): id, size, license, languages, sha256.
3. Append an entry to `app/src/main/assets/models/registry.json` with the same fields. Mark `bundled: false` unless you've also added the file under `app/src/main/assets/models/`.
4. Implement a new `Classifier` subclass under `app/src/main/java/com/nohate/app/classify/` if the model needs a backend that isn't already supported.
5. Run the in-app **Benchmark** screen on a device and append the row to [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).

## Style

- Kotlin: idiomatic; coroutines for async; prefer immutable data.
- Compose: one screen per file under `ui/`; theme tokens in `ui/theme/`.
- Rust: keep the JNI surface small and stable; prefer pure functions for testability.

## Building from your phone

`android-ci.yml` builds debug + release APKs on every push to any branch and on `workflow_dispatch`. From a phone:

1. Push a change (via the GitHub web UI, a mobile git client, or Claude Code on the web).
2. Open the **GitHub mobile app** → repo → Actions → the running workflow.
3. When green, scroll to **Artifacts** and download `NoHate-debug-apk`.
4. Open the downloaded zip, tap the APK to install (you'll be asked to allow installs from your browser / Files app the first time).

To kick a build without a push, tap **Run workflow** on the Actions tab — that's the `workflow_dispatch` trigger.

## Roadmap & decisions

See [`ROADMAP.md`](ROADMAP.md) for the phased plan and [`docs/DECISIONS.md`](docs/DECISIONS.md) for architectural choices.
