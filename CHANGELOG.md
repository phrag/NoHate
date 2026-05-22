# Changelog

All notable changes to NoHate. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `ROADMAP.md`, `CONTRIBUTING.md`, and `docs/` (`ARCHITECTURE.md`, `MODELS.md`, `BENCHMARKS.md`, `DECISIONS.md`) to track the incremental rewrite.

### Planned
- Pluggable `Classifier` interface with Rules / ONNX / TFLite / LLM backends.
- ONNX Runtime Mobile primary classifier (real transformer; replaces stub TFLite preprocessing).
- On-device benchmark suite (latency / throughput / memory / F1).
- Material 3 Expressive UI refresh with dynamic color.

## [0.1.0] — Initial release
- Privacy-first design: no cloud, no telemetry; encrypted storage.
- Instagram connectors (scaffold): Business/Creator (OAuth + PKCE) and personal (opt-in session).
- Local AI training: label hate/not-hate; encrypted on-device lexicon.
- Core: Android (Compose + WorkManager) + Rust JNI rules classifier.
- Optional TinyLlama (GGUF) via llama.cpp for borderline cases.
- Home, Review, Train, Console, Settings, Onboarding screens.
- Utilities: scripts to build Rust, open Android Studio, run on emulator.
- Instrumented tests for classifier, storage, and worker.
