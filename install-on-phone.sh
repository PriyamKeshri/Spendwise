#!/bin/bash
# Builds the app and installs it on the Android phone connected over USB.
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"

if [ "$(adb devices | grep -cw device)" -eq 0 ]; then
  echo "No phone found. Plug it in with USB debugging on, and tap Allow on the phone's prompt."
  exit 1
fi

./gradlew --console=plain installDebug
adb shell am start -n com.expensetracker/.MainActivity
echo "Installed. Open Spendwise on your phone."
