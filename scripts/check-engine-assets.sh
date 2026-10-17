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

for file in "$ROOT/native/qemu-system/zyx_qemu_api.h" "$ROOT/native/qemu-system/zyx_qemu_jni.c" "$ROOT/native/qemu-system/CMakeLists.txt"; do
  if [ ! -f "$file" ]; then
    echo "MISSING system-mode source: $file" >&2
    fail=1
  fi
done

RUNTIME="$ROOT/app/build/generated/zyx-qemu-runtime/assets/zyx-qemu"
if [ -s "$RUNTIME/runtime.json" ] && [ -x "$RUNTIME/bin/qemu-system-aarch64" ]; then
  printf 'PRESENT: QEMU system-mode ARM64 runtime assets generated (%s files). Boot on Galaxy A07 remains untested.\n' \
    "$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["files"]))' "$RUNTIME/runtime.json")"
else
  echo "MISSING: execute python3 scripts/prepare_qemu_runtime.py to generate the pinned QEMU ARM64 runtime assets." >&2
  fail=1
fi

exit "$fail"
