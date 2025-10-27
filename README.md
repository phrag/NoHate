
# NoHate – Private, On-Device social media comment scanning and training (Android)

NoHate is a privacy-first Android app that helps you monitor Instagram post comments for harmful content and teach the app how to get better over time. Everything runs on your device: fetching, detection, review, and learning. No servers, no analytics, no comment text leaves your phone.

## How it works (for everyone)

- What it does
  - Scan comments on a post (your own or a public post) and surface likely harmful ones.
  - Let you review results quickly, copy/share/open the source, or hide/delete/report when appropriate.
  - Teach the app as you go: mark items as harmful or okay. It learns your preferences on-device.

- Where to find things in the app
  - Home: See connection status, quick actions, last scan stats, and model progress. Live progress shows while a scan runs.
  - Review: Browse flagged comments or switch to “All last scan” to see everything found. Take actions (copy, share, open, report, hide/delete on owned posts) and label as Not hate / Flag as hate / Mark safe.
  - Train: Enter your own test text or paste a public Instagram URL to fetch comments for training. Progress bars and live status guide you.
  - Console: A simple in‑app log view so you can see what’s happening during scans (providers used, counts, decisions).
  - Settings: Enable/disable the on-device LLM, download the model, set the flagging threshold, configure scan options like max comments per URL, start the setup wizard, and more.

- Scanning a post
  - Paste a public Instagram post or reel URL in Train to fetch comments. The app fetches in pages and respects a configurable cap (default 200, adjustable in Settings).
  - On Home, you can also “Run now” to scan according to your configured providers or monitored URLs.
  - After a scan, you’re guided to Review to see results.

- Teaching the app
  - If a flagged item isn’t harmful, tap “Not hate” (prevents re-flagging and updates the safe list).
  - In “All last scan”, use “Flag as hate” to promote an unflagged item to flagged (and enqueue for training), or “Mark safe” to teach that phrase is okay.
  - A real-time popup can appear when items are flagged so you can label immediately.

- On-device privacy
  - Your labels, comments, and models are stored locally with hardware-backed encryption where available.
  - No comment text or labels are uploaded anywhere. The app has no telemetry.

- About the on-device LLM
  - Optionally use a tiny local language model (TinyLlama GGUF) to double‑check borderline cases.
  - If enabled and downloaded, the LLM runs fully on-device via llama.cpp (no internet). You’ll be prompted to download it in onboarding or Settings with size estimates and Wi‑Fi guidance.

### LLM model details (what, why, and when it runs)
- Model: TinyLlama (1.1B) converted to GGUF and quantized (typically Q4_K_M) for mobile CPUs.
  - Size on disk: ~180–230 MB depending on quantization.
  - Memory at runtime: ~350–600 MB plus context; suitable for mid‑range 2022+ devices.
- Why a tiny LLM?
  - It is not the primary classifier. The small TFLite model is faster and runs first.
  - The LLM only “sanity‑checks” comments whose scores fall inside a narrow band near your threshold to reduce false positives without scanning everything.
- When it runs:
  - The TFLite/rules score is computed first.
  - If the final score is within a band around your flagging threshold (computed from your calibration counters), we ask TinyLlama for a second opinion and combine the scores.
  - This keeps battery impact low while improving precision on ambiguous phrasing.
- Privacy guarantees:
  - Prompts and comments never leave the device; llama.cpp runs locally.
  - The downloaded model file is stored in the app’s private storage and can be removed at any time in Settings.
- Prompt strategy and calibration:
  - The prompt instructs the LLM to answer with a compact numeric “risk” score informed by brief examples. It avoids subjective explanations to keep latency low and outputs deterministic‑ish structure.
  - The “calibration band” around the threshold widens or narrows based on how often you mark things as hate vs safe, so the LLM is consulted only where it historically helps you.
- Performance & tips:
  - Expect ~15–60 ms/token on modern devices; most queries finish in under a second because we use very short prompts and few output tokens.
  - If your device feels slow or warm, you can disable the LLM in Settings; the core classifier continues to work.
  - If storage is tight, you can delete the model and re‑download later.

## Technical details (for developers)

### Architecture overview
- Kotlin + Jetpack Compose UI; Navigation Compose + bottom navigation.
- WorkManager scan pipeline with Android 14+ foreground service type `dataSync`.
- `SecureStore` (EncryptedSharedPreferences) holds thresholds, flags/hidden, user hate/safe lexicons, training queue, scan history, last-scan comments, monitored URLs, console logs, metrics (including cumulative total processed), and progress.
- Models: small TFLite classifier first; optional on-device LLM (llama.cpp) only for borderline scores.

### Comment sources
- Public URL importer `PostImporter` paginates comments (GraphQL pattern) up to a configurable cap (Settings → Max comments per URL). Falls back to parsing embedded JSON if needed.
- Provider abstraction scaffolds Business/Creator Graph and personal-session sources.

### ScanWorker flow
- Accepts manual text and/or `source_url` or uses providers + monitored URLs.
- Saves last-scan snapshot for the Review “All last scan” tab and updates live progress.
- Per-comment decisions combine user lexicons, TFLite score, optional LLM assist near threshold, and explicit overrides.
- Dedupes new flags against existing flags/hidden; enqueues training; writes stats and history.

### Review actions
- Copy, Share, Open (source URL), Report (deep-links to Instagram app or browser).
- Not hate, Hide, Delete (flagged tab). In “All last scan”: Flag as hate, Mark safe.
- When authorized and a `commentId` is known, Hide/Delete via Graph API (encrypted token).

### On-device LLM
- JNI bridge (`llamabridge.cpp`) to llama.cpp; Kotlin `LlamaEngine` mediates availability and calls.
- `LlmDownloader` resolves TinyLlama on Hugging Face and downloads with resume and checksum to `files/llm/`.

## Local dev setup
1) Prereqs
- Android Studio + SDK + NDK (set `ANDROID_NDK_HOME`)
- JDK 17+
- Rust + cargo (`rustup`), targets for Android
- `cargo-ndk` (`cargo install cargo-ndk`)

2) Install Android Rust targets
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

3) Build Rust for Android and copy into `app/src/main/jniLibs`
```bash
./scripts/build_rust_android.sh
```

4) Open the project in Android Studio and run on a device (prefer a physical device for NN performance). If you don't have the Gradle wrapper yet, run:
```bash
gradle wrapper
```

## Tests
- Instrumented tests on emulator/device:
```bash
./scripts/run_on_emulator.sh
./gradlew :app:connectedAndroidTest
```

## Changelog

### v0.1.0
- Privacy: No cloud, no telemetry; encrypted storage (hardware-backed)
- Instagram connectors (scaffold): Business/Creator (OAuth+PKCE) and Personal (opt‑in session)
- Local AI Training: label examples as hate/not hate; encrypted on-device lexicon
- Core: Android app (Compose, WorkManager) + Rust JNI classifier (fully on-device)
- Home: modern Material 3 layout, FAB + top-bar shortcut to training
- Utilities: scripts to build Rust, open Android Studio, run on emulator
- Tests: instrumented tests for classifier, storage, and worker

## Roadmap
- Replace stub with tiny LLM or small classifier (e.g., on-device quantized model)
- Add per-user incremental learning/fine-tuning signals stored locally
- Implement Instagram login/session capture and comment fetcher (on-device)
- Add report/delete flows invoking platform APIs with your user confirmation

## Build scripts
- `scripts/build_rust_android.sh` builds the Rust core for `arm64-v8a`, `armeabi-v7a`, `x86_64` and places `.so` files into `app/src/main/jniLibs/…`

## Licenses
GPLv3
