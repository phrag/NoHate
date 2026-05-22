# Benchmarks

NoHate ships an in-app benchmark suite so you can compare classifier backends on your own device. This page collects representative results.

## Methodology

Implemented in `app/src/main/java/com/nohate/app/bench/` (Phase 3). All measurements are taken on-device, classification only — no I/O, no UI work.

- **Warmup**: 10 classifications discarded before measurement.
- **Latency (cold)**: 200 unique inputs, fresh interpreter, single-threaded. Reported as `p50 / p95 / p99 / mean` (milliseconds).
- **Latency (warm)**: 1 000 cycling inputs, same instance. Same percentile reporting.
- **Throughput**: comments-per-second over the warm run.
- **Memory**: `Debug.MemoryInfo` PSS delta around the warmup + warm run; also `Runtime.totalMemory()-freeMemory()` snapshot.
- **Accuracy**: bundled labeled set at `app/src/main/assets/bench/eval.csv` (see _Dataset_ below). For each model, we sweep thresholds in `[0.05, 0.95]` step `0.05`, then report:
  - Default-threshold (the user's current setting): precision, recall, F1.
  - Best-F1 threshold: precision, recall, F1, ROC-AUC.

Results can be exported as JSON from the Benchmark screen (`filesDir/bench/run-<ts>.json`) and shared.

## Dataset

We bundle a small held-out evaluation set for accuracy measurement only — it's never used for training and never leaves the device.

- **ETHOS (binary)** — Mollas et al., 2020. ~998 manually-labeled YouTube comments, balanced binary hate-speech label.
- Source: <https://github.com/intelligence-csd-auth-gr/Ethos-Hate-Speech-Dataset>
- License: MIT (see `LICENSE-THIRD-PARTY.md` once added).
- Path in repo: `app/src/main/assets/bench/eval.csv`.

If you need stronger / multi-lingual evaluation, additional CSVs can be dropped under `assets/bench/` and selected from the Benchmark screen.

## Results

Results will be filled in once Phase 3 lands. Template:

### Pixel 7 — Android 14 — _placeholder_

| Model | Latency p50 (ms) | p95 (ms) | Throughput (c/s) | Peak mem (MB) | F1 (default) | F1 (best thr) | AUC |
|---|---|---|---|---|---|---|---|
| `rules-v1` | — | — | — | — | — | — | — |
| `toxic-distilbert-int8` | — | — | — | — | — | — | — |
| `dehatebert-en` | — | — | — | — | — | — | — |
| `tinyllama-1.1b-q4km` | — | — | — | — | — | — | — |

### Acceptance bars
- `toxic-distilbert-int8` F1 on ETHOS ≥ 0.78.
- `rules-v1` F1 on ETHOS treated as documentation-only baseline (~0.55–0.65 expected).
- Primary classifier p95 latency on Pixel 7 < 80 ms / comment.

## How to run

1. Install a debug build on a physical device (emulator numbers are noisy).
2. Open **Settings → Benchmark**.
3. Tick the models you want to compare; tap **Run all**.
4. After the run, tap **Export** to save / share the JSON.
5. Append the result row to this file under the appropriate device heading.

## Notes on noise

- Thermal throttling on phones is real. Plug the device in, run with the screen on, and prefer a cool ambient.
- Background work (sync, indexing) skews PSS. Close other apps before benchmarking.
- The bundled eval set is small enough that single-flip swings can shift F1 by ~0.005 — don't over-interpret tiny deltas.
