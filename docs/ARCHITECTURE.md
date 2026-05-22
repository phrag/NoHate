# Architecture

NoHate is an offline-first Android app. Everything — fetching comments, classifying them, learning from labels — runs on-device. No comment text or label leaves the phone.

## Module map

```
app/                                  Android app (Kotlin + Compose)
├── MainActivity.kt                   Entry point, navigation
├── NativeClassifier.kt               JNI bridge to Rust core
├── auth/                             OAuth + session handling
├── data/SecureStore.kt               EncryptedSharedPreferences-backed store
├── classify/                         (Phase 1+) pluggable classifier backends
│   ├── Classifier.kt                 Common interface
│   ├── ClassifierRegistry.kt         Lazy backend factory
│   ├── ClassifierManager.kt          Holds active primary + borderline
│   ├── RulesClassifier.kt            Wraps NativeClassifier (Rust)
│   ├── OnnxClassifier.kt             (Phase 2) ONNX Runtime Mobile
│   ├── LlmClassifier.kt              Wraps LlamaEngine
│   └── ModelDownloader.kt            (Phase 2) registry-driven downloader
├── bench/                            (Phase 3) benchmark harness
├── llm/                              llama.cpp JNI bridge + downloader
├── platform/                         Instagram providers, PostImporter
├── ui/                               Compose screens + theme
└── work/ScanWorker.kt                WorkManager scan pipeline
rust/core/                            Rust JNI classifier
scripts/                              Build + model-export scripts
```

## Data flow (scan)

```
[Provider | PostImporter] -> [ScanWorker]
                                 |
                                 v
                       [ClassifierManager]
                       /        |        \
              primary       borderline    overrides
            (Onnx|Rules|     (Llm, only   (user hate/safe
             Tflite)         if score in   phrases)
                             calibration
                             band)
                                 |
                                 v
                          [SecureStore]
                                 |
                                 v
                  [UI: Review / Console / Home]
```

`ScanWorker` (`app/src/main/java/com/nohate/app/work/ScanWorker.kt`):
1. Resolves comments from manual input, a single URL, or providers + monitored URLs.
2. Snapshots them into `SecureStore.lastComments` so the "All last scan" view works.
3. Calls `ClassifierManager.classifyPrimary(text)` per comment.
4. If `score ∈ [threshold − band, threshold]`, calls `ClassifierManager.classifyBorderline(text)` and combines (max).
5. Applies user-lexicon overrides last.
6. Dedupes against existing flagged / hidden, persists, fires notifications.

## Classifier interface

See [`MODELS.md`](MODELS.md) for backend-specific details.

```kotlin
data class Score(
    val probability: Float,
    val label: String,
    val latencyNanos: Long,
    val backend: String,
)
data class ClassifierInfo(
    val id: String, val displayName: String, val version: String,
    val sizeBytes: Long, val estPeakMemMb: Int, val license: String,
    val languages: List<String>, val source: String, // bundled|downloaded
)
interface Classifier {
    val info: ClassifierInfo
    suspend fun warmup()
    fun classify(text: String): Score
    fun close()
}
```

## Storage

`SecureStore` (`app/src/main/java/com/nohate/app/data/SecureStore.kt`) wraps `EncryptedSharedPreferences` (AES-256, hardware-backed where available). It holds:

- Thresholds, LLM band width, calibration counters.
- Flagged / hidden items, last-scan snapshot.
- User hate / safe lexicons, training queue.
- Monitored URLs, scan history, metrics, console log.
- Selected primary + borderline classifier ids (Phase 1+).

## Threading

- Compose UI on the main thread; collects state from `SecureStore` snapshots.
- `ScanWorker` runs on `Dispatchers.Default` (via `CoroutineWorker`) with a `dataSync` foreground service.
- Classifier `classify()` calls are synchronous and CPU-bound; called from the worker.
- LLM and ONNX inference release native memory on `close()`; lifecycle owned by `ClassifierManager`.

## Privacy

- No network egress for classification, labels, or comments.
- Model downloads (LLM and Phase 2 ONNX large variants) are explicit user actions in Settings.
- Stored models live in app-private `filesDir/models/<id>/` and can be deleted from Settings.
- No analytics, no crash reporting, no remote logs.
