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

## ADR-008 — Defer `onnxruntime-extensions-android` until a real model is bundled

**Status:** Accepted (2026-05-22) — refines ADR-004

**Context:** First CI run on the rewrite branch failed: `com.microsoft.onnxruntime:onnxruntime-extensions-android:0.12.0` doesn't exist at that coordinate on Maven Central. ORT Extensions has had several artifact-id and version shifts; without an actual tokenizer-embedded model to test against, picking the right version was a guess.

**Decision:** Remove the Extensions dep from `app/build.gradle.kts` for now. Keep `OnnxClassifier`'s reflective `registerCustomOpLibrary` — it's already a no-op when the class isn't on the classpath. Re-add the dependency at a verified published version in the same PR that bundles the first tokenizer-embedded `.onnx` model.

**Consequences:** Build is green on a transformer-less Phase 2. No functional regression: `ClassifierRegistry.readyOnnxIds()` skips any ONNX backend whose session fails to initialise, so the rules core remains the live primary until a real model lands. Net: ADR-004 holds in spirit; only the timing of the dependency adoption changed.

**Update (2026-05):** Resolved. Maven Central does publish `com.microsoft.onnxruntime:onnxruntime-extensions-android` (versions `0.6.0` through `0.13.0`); the original `0.12.0` lookup was bad data — the actual line is `0.12.4` → `0.13.0`. Restored at `0.13.0` as a regular `implementation` dependency. Reflective registration in `OnnxClassifier` stays in place as belt-and-braces.

---

## ADR-009 — Migrate to Kotlin 2.0 and the Compose Compiler Gradle plugin

**Status:** Accepted (2026-05)

**Context:** Phase 6 added Paparazzi for Compose screenshot tests. Paparazzi 1.3.5 ships Kotlin 2.0 on its plugin classpath, which AGP 8.11.1 detects and refuses the build with: *"Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required when compose is enabled"*. Pinning Paparazzi to 1.3.4 (ADR-bypass commit) kept Kotlin 1.9.24 working but blocked the Paparazzi upgrade path and left us on a version line that's not getting new Compose support.

**Decision:** Upgrade Kotlin to 2.0.21, apply `org.jetbrains.kotlin.plugin.compose:2.0.21`, and drop the legacy `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }` block. Restore Paparazzi to 1.3.5.

**Consequences:** Compose Compiler version is now governed by the Kotlin plugin instead of an AGP option — fewer moving parts. K2 compiler is the default on Kotlin 2.0, generally faster but occasionally stricter (mostly around nullability inference); fix any new errors as they surface. Future Compose BOM bumps no longer need a paired `kotlinCompilerExtensionVersion` update. Paparazzi can track the latest line.

---

## ADR-010 — Rules core is an escalation signal, not a peer classifier

**Status:** Accepted (2026-08) — refines ADR-002

**Context:** Field testing flagged 31 of 34 comments on a real post. The neural model was not the cause — logit dumps showed `toxic-distilbert` scoring "Great post!" at ≈0.001. Two compounding defects in the rules layer were:

1. `compute_score_base` matched **substrings**, not words. `kill` fired on "skills", `die` fired on "ladies", "audience", "medieval", "foodie", "indie", "candies", "buddies". Replaying the scorer against 20 realistic benign comments produced **14 false positives** — and **0 of 8** hits on coded hate ("88", "1488", "6MWE", "RAHOWA", "(((…)))").
2. `ClassifierManager.classifyPrimary()` combined the rules core and the neural models with `max()`. Combining classifiers by max takes the **union of their false positives**, so the noisiest backend sets the floor for the entire pipeline. The lexicon alone could flag anything, whatever the model said.

**Decision:**

- Rewrite the Rust scorer around a space-padded **word field**, so every needle matches on word boundaries. Replace the flat weighted-substring list with explicit tiers: coded terms (ADL "Hate on Display"), slurs (leet-folded per token), targeted attacks (identity reference **+** hostile predicate), and harassment. Combine signals as `max + 0.05 per additional hit` rather than summing — two mid-weight hits no longer reach certainty.
- Demote the rules core in `ClassifierManager`: it may only override a neural verdict when it clears `RULES_ESCALATE_MIN` (0.85 — slurs, coded terms, explicit dehumanisation). Softer signals are advisory. When no neural model is ready, rules still stand in as the primary.
- Bare numeric codes ("88", "14", "109") count only when the comment is *nothing but* the code, which is the realistic dog-whistle shape and avoids firing on "88 likes already".

**Consequences:** The dominant false-positive source is gone, and coded/dog-whistle content — which neural hate-speech models reliably miss, because these are lexical conventions rather than linguistic patterns — is now caught by the layer actually suited to it. The lexicon becomes a precision instrument rather than a recall net. Costs: the slur list is a hardcoded seed and will drift; it should move to an updatable asset sourced from a maintained database, with the user lexicon (Settings) as the interim extension point. `fuse()` is extracted as a pure function so the fusion policy is unit-testable without a `Context`.

**Still open:** `max()` across the *neural* primaries has the same union-of-false-positives property. Superseding that with mean-or-agreement fusion, exposed as a Strict/Balanced/Sensitive control, is tracked separately alongside the move to a genuine hate-speech model (see ADR-011 when written).

---

## ADR template (use this for new entries)

```
## ADR-NNN — <short title>

**Status:** Proposed | Accepted | Superseded by ADR-MMM (<date>)

**Context:** <what forced the decision>

**Decision:** <what we chose>

**Consequences:** <tradeoffs, follow-ups, things we're now committed to>
```
