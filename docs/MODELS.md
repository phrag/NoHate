# Models

NoHate supports multiple classifier backends behind a common [`Classifier`](ARCHITECTURE.md#classifier-interface) interface. Users pick a **primary** (runs on every comment) and a **borderline** model (runs only when the primary score sits near the user's flagging threshold).

## Registry

The shipped registry lives at `app/src/main/assets/models/registry.json`. Each entry has:

| Field | Meaning |
|---|---|
| `id` | Stable identifier used by `SecureStore` and the UI. |
| `kind` | `onnx` \| `tflite` \| `rules` \| `llm`. |
| `primary` | True if this model is selectable as the primary backend. |
| `bundled` | True if shipped in the APK under `assets/models/`. |
| `asset` / `url` | Asset path (bundled) or HTTPS URL (downloadable). |
| `tokenizer` | Asset / URL for the tokenizer artifact (vocab.txt, sentencepiece.model, etc.). |
| `sha256` | Hex digest of the model file; verified after download. |
| `sizeBytes` | On-disk size (used in the model picker). |
| `languages` | ISO-639-1 codes the model supports. |
| `license` | SPDX identifier. |
| `version` | Model version string. |

`ModelDownloader` (Phase 2) verifies sha256, supports HTTP resume, and stores downloaded files under `filesDir/models/<id>/`.

## Catalog

| id | Backend | Kind | Default | Size (INT8) | Languages | License | Source |
|---|---|---|---|---|---|---|---|
| `rules-v1` | `RulesClassifier` | rules | yes (fallback) | tiny | en (extensible) | GPL-3.0 (own code) | Rust core |
| `tflite-stub` | `LegacyTfliteClassifier` | tflite | no | n/a | en | varies | Legacy; kept during transition |
| `toxic-distilbert-int8` | `OnnxClassifier` | onnx | **yes (default)** | ~35 MB | en | Apache-2.0 | [`martin-ha/toxic-comment-model`](https://huggingface.co/martin-ha/toxic-comment-model) |
| `dehatebert-en` | `OnnxClassifier` | onnx | downloadable | ~110 MB | en | Apache-2.0 | [`Hate-speech-CNERG/dehatebert-mono-english`](https://huggingface.co/Hate-speech-CNERG/dehatebert-mono-english) |
| `twitter-roberta-hate` | `OnnxClassifier` | onnx | downloadable | ~110 MB | en | MIT | [`cardiffnlp/twitter-roberta-base-hate-latest`](https://huggingface.co/cardiffnlp/twitter-roberta-base-hate-latest) |
| `tinyllama-1.1b-q4km` | `LlmClassifier` | llm | borderline-only | ~640 MB | en (best) | Apache-2.0 | TinyLlama via llama.cpp |

> Sizes are approximate and post-quantization where applicable.

## Conversion recipe (ONNX)

Full runbook lives in [`BUNDLING_MODELS.md`](BUNDLING_MODELS.md). Summary:

```bash
pip install -U optimum[exporters] onnx onnxruntime onnxruntime-extensions transformers

# 1. Export to ONNX
optimum-cli export onnx \
  --model martin-ha/toxic-comment-model \
  --task text-classification \
  build/toxic-distilbert/

# 2. Quantize to INT8 (dynamic)
python scripts/quantize_onnx.py \
  --input build/toxic-distilbert/model.onnx \
  --output app/src/main/assets/models/toxic-distilbert-int8.onnx

# 3. Update registry
python scripts/update_registry.py toxic-distilbert-int8 \
  --asset models/toxic-distilbert-int8.onnx \
  --tokenizer models/toxic-distilbert-vocab.txt
```

## Tokenizer strategy

Default: **ONNX Runtime Extensions** — the tokenizer is embedded as a graph op (BertTokenizer / SentencePieceTokenizer). No on-device tokenizer code; one ONNX file per model.

Fallback if reference parity fails: **HuggingFace `tokenizers` via Rust JNI** (the existing `rust/core/` already has a JNI surface to extend).

A golden-vector parity test will live at `app/src/test/java/com/nohate/app/classify/TokenizerParityTest.kt` (Phase 5) — 10 inputs whose `input_ids` must be byte-equal to the Python reference produced during export.

## Updating a model

1. Re-run the export + quantize recipe.
2. Bump the model's `version` in `registry.json` and `MODELS.md`.
3. Recompute and record sha256.
4. Run the in-app **Benchmark** screen; append the row to [`BENCHMARKS.md`](BENCHMARKS.md).
5. Note any user-visible accuracy / latency delta in [`CHANGELOG.md`](../CHANGELOG.md).
