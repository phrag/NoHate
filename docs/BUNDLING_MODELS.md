# Bundling an ONNX classifier — step by step

This page is the runbook for converting a Hugging Face text-classification model into an INT8 ONNX artefact and shipping it inside NoHate.

## Prereqs (Mac, one-time)

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -U pip
pip install -U "optimum[exporters]" onnx onnxruntime onnxruntime-extensions transformers
```

About 1.5 GB of dependencies. Re-activate the venv (`source .venv/bin/activate`) in each new shell.

## Step 1 — Export

From the repo root:

```bash
mkdir -p build/toxic-distilbert
python scripts/export_onnx.py \
  --model martin-ha/toxic-comment-model \
  --output build/toxic-distilbert/
```

The script:
1. Calls `optimum-cli export onnx --task text-classification` to produce `build/toxic-distilbert/model.onnx`.
2. Wraps the model with a BertTokenizer custom op (via `onnxruntime-extensions`) so the on-device runtime can feed a raw string tensor.

If you'd rather avoid the custom op (you'll tokenize on the Kotlin side later), add `--skip-tokenizer`:

```bash
python scripts/export_onnx.py \
  --model martin-ha/toxic-comment-model \
  --output build/toxic-distilbert/ \
  --skip-tokenizer
```

## Step 2 — Quantize (INT8 dynamic)

```bash
python scripts/quantize_onnx.py \
  --input build/toxic-distilbert/model.onnx \
  --output app/src/main/assets/models/toxic-distilbert-int8.onnx
```

At the end the script prints two lines you'll need:

```
[done]    sha256=<hex digest>
[done]    sizeBytes=<int>
```

## Step 3 — Update the registry

Open `app/src/main/assets/models/registry.json` and replace the matching entry's `sha256` and `sizeBytes`:

```json
{
  "id": "toxic-distilbert-int8",
  …
  "sha256": "<the hex you just printed>",
  "sizeBytes": <the int you just printed>,
  …
}
```

## Step 4 — Build and install

```bash
./build.sh
```

The new APK gains about 30–40 MB of asset weight. On launch the app:

- Mmaps `assets/models/toxic-distilbert-int8.onnx`.
- Tries to register the ORT Extensions custom op via reflection. If `onnxruntime-extensions-android` isn't on the classpath yet (it's been deferred — see ADR-008 in `DECISIONS.md`), session creation will fail and `ClassifierRegistry` will simply hide the ONNX backend from `available()`. No crashes.
- Once the Extensions dep lands at a verified version, the ONNX classifier becomes the live primary.

## Step 5 — Verify

- **Benchmark screen** (Settings → Benchmark classifiers): the new model should appear in the checklist as `toxic-distilbert-int8`. If it doesn't, the runtime tokenizer op isn't resolving — fall back to the `--skip-tokenizer` path above and we'll add a Kotlin tokenizer.
- **Console** during a scan: `scan:decision` lines include `backend=<id>`. Look for `backend=toxic-distilbert-int8`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `optimum-cli: command not found` | Venv not activated | `source .venv/bin/activate` |
| `ImportError: No module named onnxruntime_extensions.tools.pre_post_processing` | Older `onnxruntime-extensions` (<0.10) | `pip install -U onnxruntime-extensions` |
| Quantize step fails on `nn.functional.linear` | Model uses a layer dynamic quant can't handle | Try `--per-channel`, or use `quantize_static` (write your own script) |
| APK installs but no ONNX backend shows in Benchmark | Custom op not resolved at runtime | Re-export with `--skip-tokenizer`; add a Kotlin tokenizer (planned follow-up) |
| Build error: `Could not find com.microsoft.onnxruntime:onnxruntime-extensions-android` | Wrong version | Look up a published version on Maven Central, update `app/build.gradle.kts` |

## Alternative models

The registry already lists two downloadable alternates. They can be exported the same way:

- `Hate-speech-CNERG/dehatebert-mono-english` — `--model Hate-speech-CNERG/dehatebert-mono-english`
- `cardiffnlp/twitter-roberta-base-hate-latest` — `--model cardiffnlp/twitter-roberta-base-hate-latest`

Both are larger (~110 MB INT8). Mark them `bundled: false` in `registry.json` and host the file somewhere with a stable URL — `ModelDownloader` will pull on first use.
