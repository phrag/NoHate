# Changelog

All notable changes to NoHate. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Tracking docs: `ROADMAP.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `docs/MODELS.md`, `docs/BENCHMARKS.md`, `docs/DECISIONS.md`, `docs/MODERATION.md`.
- `Classifier` interface + `ClassifierRegistry` + `ClassifierManager` under `app/src/main/java/com/nohate/app/classify/`. Backends (Rules, ONNX transformer, TinyLlama LLM) are wrapped behind the new surface.
- `OnnxClassifier` backed by ONNX Runtime Mobile + ORT Extensions; loads tokenizer-embedded models from `assets/models/` or `filesDir/models/`.
- `ModelRegistry` and `ModelDownloader`: a JSON-driven catalogue (`assets/models/registry.json`) with resume + checksum HTTP downloads.
- Conversion recipes: `scripts/export_onnx.py`, `scripts/quantize_onnx.py`.
- Rust rules core: NFKC normalization, zero-width / bidi stripping, run-collapse, and identity-term gating. Backed by unit tests.
- CI: `android-ci.yml` now runs on every branch and supports `workflow_dispatch` so phone-driven builds produce APK artifacts.
- Benchmark harness (`com.nohate.app.bench`): runs every available classifier through warmup + cold/warm latency (p50/p95/p99) + throughput + PSS memory + accuracy (precision/recall/F1/AUC) against a bundled eval CSV. Exposed via a new **Benchmark classifiers** entry under Settings.
- Placeholder evaluation CSV at `app/src/main/assets/bench/eval.csv` (ETHOS-shaped; swap in the real ETHOS file for proper F1 numbers).

### Changed
- `ScanWorker` drives classification through `ClassifierManager`; max-of-primaries + LLM borderline semantics and user lexicon overrides preserved.
- Compose BOM bumped to `2024.10.01`; `material3` to `1.3.1`.
- CI: `gradle/wrapper-validation-action@v2` → `gradle/actions/wrapper-validation@v4` (renamed upstream; old path has flaky checksum lookups).
- CI now installs Rust + `cargo-ndk` and runs `./scripts/build_rust_android.sh` before Gradle so APKs ship with `libnohcore.so`.
- `./build.sh` invokes the Rust build automatically (skip with `SKIP_RUST_BUILD=1`).
- `NativeClassifier` no longer throws when `libnohcore.so` is missing — exposes `isLibraryLoaded`; `ClassifierRegistry` skips the rules backend cleanly when the .so isn't bundled.

### Removed
- Legacy TFLite stub classifier (`com.nohate.app.ml.TfliteClassifier` + `LegacyTfliteClassifier` wrapper) and the `org.tensorflow:tensorflow-lite` dependency. The stub returned `text.length / 512f` and contributed nothing to accuracy; the real on-device transformer is now the ONNX DistilBERT primary. Removed associated `isUseQuantizedModel` / `setUseQuantizedModel` from `SecureStore`, the "Quantized on-device model" toggle from Settings, and the "Enable fast model" button from Onboarding.

### Deferred
- `material3-adaptive` dep dropped from Phase 2; the right coordinate sits under the `androidx.compose.material3.adaptive` group and will be added in Phase 4 when adaptive layouts actually land.

### Planned
- Phase 5 polish: unit tests, v0.2.0 cut.

## [0.1.0] — Initial release
- Privacy-first design: no cloud, no telemetry; encrypted storage.
- Instagram connectors (scaffold): Business/Creator (OAuth + PKCE) and personal (opt-in session).
- Local AI training: label hate/not-hate; encrypted on-device lexicon.
- Core: Android (Compose + WorkManager) + Rust JNI rules classifier.
- Optional TinyLlama (GGUF) via llama.cpp for borderline cases.
- Home, Review, Train, Console, Settings, Onboarding screens.
- Utilities: scripts to build Rust, open Android Studio, run on emulator.
- Instrumented tests for classifier, storage, and worker.
