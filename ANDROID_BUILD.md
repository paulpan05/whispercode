# Android Release Build Guide

This is the exact process used to produce the working Android test build.

## What worked best

- Use an arm64 release build: `--target aarch64`
- This produced an APK around `20 MB` (good for GitHub branch download)
- Building all targets created an APK around `185 MB`, which is too large for GitHub's 100 MB file limit

## Prerequisites

- Bun installed
- Android SDK installed at `$HOME/Library/Android/sdk`
- Android NDK installed (any recent version; Tauri auto-detects)
- JDK 21 installed at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Release keystore at `packages/android/release.jks`
- Keystore config at `packages/android/src-tauri/gen/android/keystore.properties`

```properties
storeFile=../../../../release.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_password
```

## Build steps

> **Important:** JDK 21 is required. JDK 26+ is incompatible with Gradle 8.14.3 and will cause build failures. If `java -version` shows 26 or higher, ensure JAVA_HOME points to JDK 21.

Run from repo root:

```bash
cd packages/android
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
bun run tauri android build --apk --target aarch64
```

> **Note:** Tauri auto-detects available NDK versions. The `NDK_HOME` environment variable is set by Tauri automatically and does not need to be specified manually.

## Output APK

Build output path:

`packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk`

> **Note on `beforeBuildCommand`:** The build runs a `beforeBuildCommand` defined in `src-tauri/tauri.conf.json` that requires `tsgo` to be in PATH. If you see `Command not found: tsgo`, run the vite build manually first from `packages/app`: `bun run build`. The tauri build will then use the pre-built web assets.

## Optional verification

```bash
ls -lh packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk
shasum -a 256 packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk
```

If Java is not in your shell path, verify signature with:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk
```

## Copy APK to repo root (for easy GitHub download)

From repo root:

```bash
mv packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk app-universal-release.apk
```

## Upload branch with APK

```bash
git switch android-build
git add app-universal-release.apk
git commit -m "add android release APK for phone testing"
git push -u origin android-build
```
