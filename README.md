
# NoHate (Android + Rust, fully on-device)


Privacy-first mobile app that scans your own social media comments on-device and helps you flag and remove hate speech. No comment text leaves the device; all inference and incremental learning stay local.

## Key principles
- On-device inference only (no cloud calls for ML)
- Minimal permissions; only Internet for fetching your own comments
- Secure local storage (Android EncryptedSharedPreferences)
- Rust core for safety and performance, exposed via JNI

## Architecture
- Android app (Kotlin, Jetpack Compose, WorkManager)
- Rust core crate (`rust/core`) compiled to `.so` with `cargo-ndk`
- Kotlin JNI wrapper `NativeClassifier` loads `libnohcore.so`
- Provider abstraction for platforms (start with Instagram stub)

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

## Privacy & security notes
- Comments are fetched via your authenticated session, processed entirely on-device.
- No telemetry, analytics, or remote logging.
- Flag decisions and user feedback are stored locally using Android encrypted storage.
- The initial classifier is a rule-based stub; replace with an on-device model (e.g., GGUF via `llama.cpp` bindings) when ready.

## Roadmap
- Replace stub with tiny LLM or small classifier (e.g., on-device quantized model)
- Add per-user incremental learning/fine-tuning signals stored locally
- Implement Instagram login/session capture and comment fetcher (on-device)
- Add report/delete flows invoking platform APIs with your user confirmation

## Build scripts
- `scripts/build_rust_android.sh` builds the Rust core for `arm64-v8a`, `armeabi-v7a`, `x86_64` and places `.so` files into `app/src/main/jniLibs/…`

## Licenses
- You are responsible for complying with each platform's terms of service and API usage policies.
