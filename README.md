
# NoHate (Android + Rust, fully on-device)


Privacy-first mobile app that scans your own social media comments on-device and helps you flag and remove hate speech. No comment text leaves the device; all inference and incremental learning stay local.

## Quick start
- Open in Android Studio (macOS): `./scripts/open_android_studio.sh`
- Run on emulator/device: `./scripts/run_on_emulator.sh`
- Build native Rust libs: `./scripts/build_rust_android.sh`

## How we do it
- Instagram Business/Creator (official, compliant)
  - Auth: OAuth + PKCE in a secure browser (Custom Tabs) → deep link back.
  - Scopes: Minimal (read/manage comments on your media only).
  - Tokens: Stored in hardware-backed encrypted storage; no app secret bundled; re-login when needed (no server-side token exchange).
  - Network: Only instagram/facebook Graph endpoints; cleartext disabled.

- Instagram Personal (session-only, opt-in)
  - Auth: Isolated WebView login using an ephemeral cookie store; extract only required session cookies for `instagram.com` and purge WebView storage afterward.
  - Storage: Session cookies sealed with hardware-backed keys; access gated by device unlock/biometric.
  - Network: Strict allowlist of endpoints; no third-party calls; no analytics/telemetry.
  - Transparency: Clear ToS warning and explicit user consent before enabling; one-tap disable + secure wipe.

## Security & privacy guarantees
- On-device only: No tokens, cookies, comments, or labels leave the device.
- Hardware-backed encryption: Android EncryptedSharedPreferences with StrongBox where available; biometric gate for sensitive actions.
- Process isolation: Auth flows in an isolated process; WebView data cleared post-login; least-privilege API surfaces.
- Network hardening: Cleartext off; strict endpoint allowlist; optional TLS pinning for platform endpoints (with safe rollover).
- Integrity checks: Option to block or degrade sensitive features on compromised devices.
- Data minimization: Fetch only your post comments; store only what’s needed (tokens, minimal metadata); local-only logs with redaction.

## Architecture
- Android app (Kotlin, Jetpack Compose, WorkManager, Navigation Compose)
- Rust core crate (`rust/core`) compiled to `.so` with `cargo-ndk`
- Kotlin JNI wrapper `NativeClassifier` loads `libnohcore.so`
- Provider abstraction for platforms (Instagram Business/Creator via Graph; Instagram Personal via on-device session)

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

## Roadmap
- Replace stub with tiny LLM or small classifier (e.g., on-device quantized model)
- Add per-user incremental learning/fine-tuning signals stored locally
- Implement Instagram login/session capture and comment fetcher (on-device)
- Add report/delete flows invoking platform APIs with your user confirmation

## Build scripts
- `scripts/build_rust_android.sh` builds the Rust core for `arm64-v8a`, `armeabi-v7a`, `x86_64` and places `.so` files into `app/src/main/jniLibs/…`

## Licenses
- You are responsible for complying with each platform's terms of service and API usage policies.
