# Decisions

Lightweight ADRs for the NoHate rewrite. Each entry: context, choice, consequences. Append-only — supersede with a new entry rather than rewriting history.

---

## ADR-001 — Incremental rewrite, not ground-up

**Status:** Accepted (2026-05-22)

**Context:** The existing Kotlin + Compose + Rust JNI app is sound at the platform layer; the weaknesses are concentrated in the ML pipeline and UI polish.

**Decision:** Keep the Android + Kotlin + Compose + Rust JNI foundation. Rewrite the ML internals behind a new `Classifier` interface, refresh the UI, add a benchmark module. Don't replatform.

**Consequences:** Faster delivery; lower regression risk; existing instrumented tests stay relevant. Forecloses (for now) cross-platform via Kotlin Multiplatform / Flutter — revisit if there's demand.

---

## ADR-002 — Pluggable `Classifier` interface

**Status:** Accepted (2026-05-22)

**Context:** Today the scan pipeline hard-codes rules + TFLite + LLM. We want to swap in ONNX as the primary backend, keep the others as alternates, and let the user choose.

**Decision:** Introduce a `Classifier` interface (`probability`, `latencyNanos`, `backend`, plus an `info` struct). A `ClassifierRegistry` lazy-instantiates available backends; a `ClassifierManager` holds the user's primary + borderline picks. `ScanWorker` talks only to the manager.

**Consequences:** Adding a new model becomes a registry entry + a thin `Classifier` subclass. Users get a model picker. Benchmarking becomes trivial — just iterate the registry.

---

## ADR-003 — ONNX Runtime Mobile as the primary NLP backend

**Status:** Accepted (2026-05-22)

**Context:** The current TFLite preprocessing is a stub (`text.length / 512f`); we need a real transformer classifier. The candidates were MediaPipe Text Classifier, TFLite + a real preprocessor, and ONNX Runtime Mobile.

**Decision:** ONNX Runtime Mobile. The Hugging Face ecosystem is the broadest for hate-speech models; `optimum-cli` makes ONNX export a one-liner; ORT supports INT8 dynamic quantization out of the box. MediaPipe was rejected for its smaller model selection; raw TFLite was rejected because tokenizer support is weaker than ORT Extensions.

**Consequences:** APK grows by ~+45 MB (ORT ~6 MB, ORT Extensions ~3 MB, bundled INT8 model ~35 MB). Mitigation: a `lite` (bundled) and `core` (download-only) build flavor planned for Phase 5 if size becomes a friction point.

---

## ADR-004 — Tokenizer via ORT Extensions (with Rust fallback)

**Status:** Accepted (2026-05-22)

**Context:** Tokenization must match the model's training tokenizer exactly. Options: (a) embed BertTokenizer / SentencePiece as an ONNX op via ORT Extensions; (b) call HuggingFace `tokenizers` (Rust) over JNI.

**Decision:** Start with ORT Extensions for simpler runtime code. Validate parity with a golden-vector test (10 inputs whose `input_ids` must byte-equal the Python reference). If parity ever breaks, switch the affected models to the Rust `tokenizers` JNI path.

**Consequences:** Single ONNX file per model; no tokenizer code on-device. Roughly +3 MB APK for the Extensions library. If we end up needing the JNI fallback for some models, we'll be in a mixed world for a while — acceptable.

---

## ADR-005 — Bundle a small primary model; offer larger ones as downloads

**Status:** Accepted (2026-05-22)

**Context:** Offline-first UX wants a working classifier on first launch. APK-size discipline wants nothing bundled. Tension.

**Decision:** Bundle `toxic-distilbert-int8` (~35 MB) as the default primary so the app works offline immediately. Make larger / multilingual models (`dehatebert-en`, `twitter-roberta-hate`) downloads via the generalized `ModelDownloader`.

**Consequences:** New users get sensible classification with no download step. Power users opt in to larger models. Users on tight storage can delete the bundled file (Phase 4 Model Manager) and re-download later.

---

## ADR-006 — ETHOS as the bundled evaluation set

**Status:** Accepted (2026-05-22)

**Context:** The benchmark module needs labeled data for accuracy metrics. Options included HateXplain, Davidson, ETHOS, and OLID.

**Decision:** ETHOS (binary). ~998 rows is small enough to bundle, MIT-licensed for redistribution, balanced binary labels match the app's primary signal, and the manual annotation quality is high. Attribution lives in `docs/BENCHMARKS.md` and `LICENSE-THIRD-PARTY.md`.

**Consequences:** Accuracy numbers will be ETHOS-domain (YouTube comment style) — that's a known limitation we document in `BENCHMARKS.md`. Power users can drop additional CSVs into `assets/bench/` and select them in the Benchmark screen.

---

## ADR-007 — Material 3 Expressive + dynamic color for the UI refresh

**Status:** Accepted (2026-05-22)

**Context:** Current UI is Material 3 but generic. Options were Expressive + dynamic color, a custom design system, or "just polish".

**Decision:** Material 3 Expressive with dynamic color on API ≥ 31 and an expressive seed palette on lower. Bump Compose BOM to `2024.10.01`, `material3` to `1.3.1`.

**Consequences:** Native look that adapts to user wallpaper on modern devices; consistent fallback on older. Avoids the maintenance burden of a custom design system. Phase 4 will extract `HomeScreen.kt` from `MainActivity.kt` and apply the new tokens screen-by-screen.

---

## ADR template (use this for new entries)

```
## ADR-NNN — <short title>

**Status:** Proposed | Accepted | Superseded by ADR-MMM (<date>)

**Context:** <what forced the decision>

**Decision:** <what we chose>

**Consequences:** <tradeoffs, follow-ups, things we're now committed to>
```
