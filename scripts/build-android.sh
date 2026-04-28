#!/bin/bash
set -e

cd "$(dirname "$0")/.."

export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

cd packages/android
bun run tauri android build --apk --target aarch64

cd ../..
cp packages/android/src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release.apk ./app-universal-release.apk

echo "Built: app-universal-release.apk"
ls -lh ./app-universal-release.apk
shasum -a 256 ./app-universal-release.apk
