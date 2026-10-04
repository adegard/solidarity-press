#!/usr/bin/env bash
# Build and install Solidarity on a phone/tablet over adb.
# Usage: ./install.sh [device-serial]     (FORCE=1 to allow TV boxes)
set -euo pipefail

cd "$(dirname "$0")"

DEFAULT_JDK="$HOME/.local/opt/jdk-17.0.20.1+1"
if [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  echo "JAVA_HOME unusable, falling back to $DEFAULT_JDK"
  export JAVA_HOME="$DEFAULT_JDK"
fi
GRADLE="${GRADLE:-$HOME/.local/opt/gradle-8.7/bin/gradle}"
APK="app/build/outputs/apk/debug/app-debug.apk"

"$GRADLE" --quiet assembleDebug

target="${1:-}"
if [ -z "$target" ]; then
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [ "${#devices[@]}" -eq 0 ]; then
    echo "No device connected. Plug in the phone, enable USB debugging, then re-run." >&2
    exit 1
  elif [ "${#devices[@]}" -gt 1 ]; then
    echo "Several devices connected, pick one:" >&2
    printf '  %s\n' "${devices[@]}" >&2
    exit 1
  fi
  target="${devices[0]}"
fi

chars="$(adb -s "$target" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
model="$(adb -s "$target" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "Target: $target ($model, characteristics: $chars)"

if [ "${FORCE:-0}" != "1" ]; then
  case "$chars $model" in
    *television*|*TVBOX*|*box*|*BOX*)
      echo "Refusing to install on what looks like a TV box. Pass a phone serial, or FORCE=1." >&2
      exit 1
      ;;
  esac
fi

adb -s "$target" install -r -t "$APK"
adb -s "$target" shell am start -n org.solidarity.press/.MainActivity
echo "Started. Watch logs with: adb -s $target logcat --pid=\$(adb -s $target shell pidof org.solidarity.press)"
