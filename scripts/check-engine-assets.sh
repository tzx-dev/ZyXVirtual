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

if [ -f "$JNI/libzyx-qemu-jni.so" ] && [ -f "$JNI/libqemu-system-aarch64.so" ]; then
  echo "PRESENT: JNI/QEMU libraries exist; ABI symbol and runtime boot still require separate validation."
else
  echo "PENDING: JNI/QEMU system-mode libraries are not both packaged; Android 16 boot is unavailable."
fi

exit "$fail"
