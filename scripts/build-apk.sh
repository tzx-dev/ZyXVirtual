#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
export ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$ANDROID_HOME}
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_BIN=$(readlink -f "$(command -v java)")
  export JAVA_HOME=${JAVA_BIN%/bin/java}
fi

restore_gradle_properties() {
  if [ "${AAPT2_PATCHED:-0}" = 1 ] && [ -f "$ROOT/build/gradle.properties.before-aapt2" ]; then
    mv "$ROOT/build/gradle.properties.before-aapt2" "$ROOT/gradle.properties"
  fi
}
trap restore_gradle_properties EXIT HUP INT TERM

if [ "$(uname -m)" = "aarch64" ] || [ "$(uname -m)" = "arm64" ]; then
  "$ROOT/scripts/build-aapt2-wrapper.sh"
  mkdir -p "$ROOT/build"
  cp "$ROOT/gradle.properties" "$ROOT/build/gradle.properties.before-aapt2"
  printf '\nandroid.aapt2FromMavenOverride=%s\n' "$ROOT/tools/aapt2" >> "$ROOT/gradle.properties"
  AAPT2_PATCHED=1
fi

mkdir -p "$ROOT/build/reports"
bash ./gradlew --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" "$@"
