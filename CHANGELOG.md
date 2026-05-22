# Changelog

All notable changes to NoHate. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Tracking docs: `ROADMAP.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `docs/MODELS.md`, `docs/BENCHMARKS.md`, `docs/DECISIONS.md`, `docs/MODERATION.md`.
- `Classifier` interface + `ClassifierRegistry` + `ClassifierManager` under `app/src/main/java/com/nohate/app/classify/`. Backends (Rules, TFLite legacy, TinyLlama LLM) are wrapped behind the new surface.
- `OnnxClassifier` backed by ONNX Runtime Mobile + ORT Extensions; loads tokenizer-embedded models from `assets/models/` or `filesDir/models/`.
- `ModelRegistry` and `ModelDownloader`: a JSON-driven catalogue (`assets/models/registry.json`) with resume + checksum HTTP downloads.
- Conversion recipes: `scripts/export_onnx.py`, `scripts/quantize_onnx.py`.
- Rust rules core: NFKC normalization, zero-width / bidi stripping, run-collapse, and identity-term gating. Backed by unit tests.
- CI: `android-ci.yml` now runs on every branch and supports `workflow_dispatch` so phone-driven builds produce APK artifacts.

### Changed
- `ScanWorker` drives classification through `ClassifierManager`; max-of-primaries + LLM borderline semantics and user lexicon overrides preserved.
- Compose BOM bumped to `2024.10.01`; `material3` to `1.3.1`; `material3-adaptive 1.0.0` added.

### Planned
- Bundle `toxic-distilbert-int8.onnx` in `assets/models/` (Phase 2 tail).
- On-device benchmark suite (Phase 3).
- Material 3 Expressive UI refresh with dynamic color (Phase 4).
- Moderation actions: hide / delete / block via Instagram Graph (Phase 4.5).

## [0.1.0] — Initial release
- Privacy-first design: no cloud, no telemetry; encrypted storage.
- Instagram connectors (scaffold): Business/Creator (OAuth + PKCE) and personal (opt-in session).
- Local AI training: label hate/not-hate; encrypted on-device lexicon.
- Core: Android (Compose + WorkManager) + Rust JNI rules classifier.
- Optional TinyLlama (GGUF) via llama.cpp for borderline cases.
- Home, Review, Train, Console, Settings, Onboarding screens.
- Utilities: scripts to build Rust, open Android Studio, run on emulator.
- Instrumented tests for classifier, storage, and worker.
