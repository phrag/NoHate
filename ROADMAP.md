# NoHate Roadmap

Phased plan for the incremental rewrite. Each phase is meant to ship independently. Check items off as they land.

See also: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/MODELS.md`](docs/MODELS.md), [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md), [`docs/DECISIONS.md`](docs/DECISIONS.md), [`CHANGELOG.md`](CHANGELOG.md).

## Phase 1 — Foundations
- [x] Add tracking markdown docs (this file + `docs/`)
- [ ] Add `Classifier` interface + `ClassifierRegistry` + `ClassifierManager` under `app/src/main/java/com/nohate/app/classify/`
- [ ] Wrap existing backends behind the interface (Rules, TFLite, LLM)
- [ ] Refactor `ScanWorker` to call `ClassifierManager` (no behavior change)
- [ ] Bump Compose BOM to `2024.10.01`, `material3` to `1.3.1`

## Phase 2 — ONNX classifier (primary)
- [ ] Add ORT + ORT Extensions deps to `app/build.gradle.kts`
- [ ] `scripts/export_onnx.py` and `scripts/quantize_onnx.py` (conversion recipes)
- [ ] Bundle `toxic-distilbert-int8.onnx` + tokenizer in `app/src/main/assets/models/`
- [ ] Implement `OnnxClassifier`; register as default primary
- [ ] Generalize `LlmDownloader` → `ModelDownloader`; parse `assets/models/registry.json`
- [ ] Improve Rust rules engine: NFKC normalization, leet/zero-width stripping, identity-term gating

## Phase 3 — Benchmark harness
- [ ] `com.nohate.app.bench` package: `BenchSuite`, `BenchRunner`, `BenchResult`
- [ ] Bundle ETHOS eval CSV at `app/src/main/assets/bench/eval.csv` with attribution
- [ ] `BenchScreen` Compose UI under Settings
- [ ] Export results as JSON; share intent
- [ ] Populate first `docs/BENCHMARKS.md` results

## Phase 4 — UI/UX refresh (Material 3 Expressive)
- [ ] Dynamic color theme on API ≥ 31, fallback palette on lower
- [ ] `Type.kt`, `Shape.kt`, `Motion.kt` tokens
- [ ] Extract `HomeScreen.kt` from `MainActivity.kt`; refresh all screens
- [ ] Empty/loading skeletons; motion polish
- [ ] Accessibility pass (TalkBack, contrast, large text)
- [ ] Model Manager UI under Settings

## Phase 5 — Polish & docs
- [ ] Unit tests (`app/src/test/`): tokenizer parity, manager logic, calibration band, score normalization
- [ ] Benchmark instrumented test
- [ ] Finalize tracking docs
- [ ] Cut v0.2.0; update `CHANGELOG.md`

## Status legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
