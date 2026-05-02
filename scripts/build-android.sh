#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if [ ! -d "$JAVA_HOME" ]; then
  echo "ERROR: JDK 21 not found. Install with: brew install openjdk@21"
  exit 1
fi

echo "==> Building frontend..."
cd packages/android
bun run build

echo "==> Building release APK..."
cd android-app
chmod +x gradlew 2>/dev/null || true
./gradlew assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"

if [ -f "$APK" ]; then
  OUT_NAME="app-release.apk"
elif [ -f "$APK_UNSIGNED" ]; then
  OUT_NAME="app-release-unsigned.apk"
  echo "WARNING: Built unsigned APK. Create keystore.properties for signed builds."
  APK="$APK_UNSIGNED"
else
  echo "ERROR: APK not found"
  exit 1
fi

cd ../../..
cp "packages/android/android-app/$APK" "./$OUT_NAME"

echo "Built: $OUT_NAME"
ls -lh "./$OUT_NAME"
shasum -a 256 "./$OUT_NAME"
