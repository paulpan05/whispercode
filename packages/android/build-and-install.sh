#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -d "$JAVA_HOME" ]; then
  echo "ERROR: JDK 21 not found. Install with: brew install openjdk@21"
  exit 1
fi

if ! command -v adb &>/dev/null; then
  echo "ERROR: adb not found. Install Android SDK platform-tools."
  exit 1
fi

if ! adb devices 2>/dev/null | grep -q "device$"; then
  echo "ERROR: No Android device connected. Connect via USB and enable USB debugging."
  exit 1
fi

echo "==> Building frontend..."
bun run build

echo "==> Building APK..."
cd android-app
chmod +x gradlew 2>/dev/null || true

if [ ! -f gradlew ]; then
  echo "==> Generating Gradle wrapper..."
  gradle wrapper --gradle-version 8.11.1
fi

./gradlew assembleDebug 2>&1

APK="$SCRIPT_DIR/android-app/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: APK not found at $APK"
  exit 1
fi

echo "==> Installing on device..."
adb install -r "$APK"

echo "==> Done! Launching app..."
adb shell am start -n com.devgriffin.whispercode/ai.opencode.app.MainActivity
