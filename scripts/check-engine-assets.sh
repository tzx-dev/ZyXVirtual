#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JNI="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSETS="$ROOT/app/src/main/assets/engines"

fail=0
for file in "$JNI"/libqemu_gb.so "$JNI"/libqemu_kk.so "$JNI"/libbinderd_kk.so "$JNI"/libglserverd_kk.so "$JNI"/libaemuhost.so; do
  if [ ! -f "$file" ]; then
    echo "MISSING $file" >&2
    fail=1
    continue
  fi
  printf 'OK %-70s ' "${file#"$ROOT"/}"
  file "$file" | sed 's#^# #'
done

for file in "$ASSETS/common/libaemushim.so" "$ASSETS/kk/libGLES.so" "$ASSETS/kk/audio.primary.mtk.so"; do
  if [ ! -f "$file" ]; then
    echo "MISSING $file" >&2
    fail=1
  else
    printf 'OK %-70s ' "${file#"$ROOT"/}"
    file "$file" | sed 's#^# #'
  fi
done

exit "$fail"
