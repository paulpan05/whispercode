# Android Release Build Guide

## Architecture

WhisperCode for Android uses a native WebView shell (not Tauri). The app:
- Builds frontend assets with Vite into `android-app/app/src/main/assets/WebAssets/`
- Runs them in an Android WebView via `file:///android_asset/WebAssets/index.html`
- Bridges JS↔native via `@JavascriptInterface` (mirrors the iOS `WKScriptMessageHandler` pattern)

## Prerequisites

- Bun installed
- Android SDK at `$HOME/Library/Android/sdk` with:
  - `platforms/android-36`
  - `build-tools/36.0.0` (or similar)
- JDK 21 at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
  - JDK 26+ is incompatible with Gradle 8.x and will fail silently
- Release keystore at `packages/android/release.jks`
- Keystore config at `packages/android/android-app/keystore.properties`

## Keystore setup

Copy the example and fill in your values:

```bash
cp packages/android/android-app/keystore.properties.example packages/android/android-app/keystore.properties
```

`keystore.properties` format:

```properties
storeFile=../../release.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_password
```

`storeFile` is relative to `android-app/app/`, so `../../release.jks` resolves to `packages/android/release.jks`.

Without `keystore.properties`, the release build produces an unsigned APK (`app-release-unsigned.apk`). You'll need to sign it manually or create the properties file for automatic signing.

## Signed release build

From repo root:

```bash
./scripts/build-android.sh
```

This sets environment variables, builds the frontend, runs `assembleRelease`, and copies the APK to `app-release.apk` in the repo root.

### Manual release build

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

cd packages/android
bun run build
cd android-app
./gradlew assembleRelease
```

Output: `packages/android/android-app/app/build/outputs/apk/release/app-release.apk`

## Debug build (for device testing)

```bash
cd packages/android
./build-and-install.sh
```

This builds a debug APK and installs it on a connected device via `adb`. The debug variant uses application ID `com.devgriffin.whispercode.debug` so it can coexist with a release install.

## Verify signature

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app-release.apk
```

## Upload branch with APK

```bash
git switch android-build
git add app-release.apk
git commit -m "add android release APK for phone testing"
git push -u origin android-build
```
