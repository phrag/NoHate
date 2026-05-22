# NoHate Roadmap

Phased plan for the incremental rewrite. Each phase is meant to ship independently. Check items off as they land.

See also: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/MODELS.md`](docs/MODELS.md), [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md), [`docs/DECISIONS.md`](docs/DECISIONS.md), [`CHANGELOG.md`](CHANGELOG.md).

## Phase 1 — Foundations
- [x] Add tracking markdown docs (this file + `docs/`)
- [x] Add `Classifier` interface + `ClassifierRegistry` + `ClassifierManager` under `app/src/main/java/com/nohate/app/classify/`
- [x] Wrap existing backends behind the interface (Rules, TFLite, LLM)
- [x] Refactor `ScanWorker` to call `ClassifierManager` (no behavior change)
- [x] Bump Compose BOM to `2024.10.01`, `material3` to `1.3.1`

## Phase 2 — ONNX classifier (primary)
- [x] Add ORT + ORT Extensions deps to `app/build.gradle.kts`
- [x] `scripts/export_onnx.py` and `scripts/quantize_onnx.py` (conversion recipes)
- [ ] Bundle `toxic-distilbert-int8.onnx` + tokenizer in `app/src/main/assets/models/` _(run `scripts/export_onnx.py` + `scripts/quantize_onnx.py` then drop the file in)_
- [x] Implement `OnnxClassifier`; auto-registered when its model file is on disk
- [x] Generalize `LlmDownloader` → `ModelDownloader`; parse `assets/models/registry.json`
- [x] Improve Rust rules engine: NFKC normalization, zero-width stripping, run collapse, identity-term gating
- [x] Rust unit tests for the normalization + scoring

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

## Phase 4.5 — Moderation actions
Make it trivial to act on a flagged comment, scoped to what the user is authorized to do. Spec in [`docs/MODERATION.md`](docs/MODERATION.md).
- [ ] Monitor: post-row UI to add / remove monitored URLs and see last-scan stats per post
- [ ] Report: deep-link flow for any comment (already scaffolded; tighten copy + analytics-free fallbacks)
- [ ] Report user (not just comment): build the user-profile deep-link from the comment author when present
- [ ] Hide / Delete comment via Instagram Graph API when the user owns the post and a `commentId` is known
- [ ] Block author via Instagram Graph API (owner + authenticated session required)
- [ ] Confirm-before-destructive dialog with "undo" affordance where the API allows it
- [ ] Permission gating: every API-backed action checks `SecureStore.isFeatureEnabled("ig_graph"|"ig_session")` and the per-post `ownedByMe` flag

## Phase 5 — Polish & docs
- [ ] Unit tests (`app/src/test/`): tokenizer parity, manager logic, calibration band, score normalization
- [ ] Benchmark instrumented test
- [ ] Finalize tracking docs
- [ ] Cut v0.2.0; update `CHANGELOG.md`

## Status legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
