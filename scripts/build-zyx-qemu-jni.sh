#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
  echo "Defina ANDROID_NDK_HOME ou ANDROID_NDK_ROOT para compilar a bridge JNI arm64." >&2
  exit 2
fi
BUILD=$ROOT/build/native-qemu-jni-arm64
cmake -S "$ROOT/native/qemu-system" -B "$BUILD" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" --parallel "${JOBS:-4}"
DEST=$ROOT/app/src/main/jniLibs/arm64-v8a
mkdir -p "$DEST"
cp "$BUILD/libzyx-qemu-jni.so" "$DEST/libzyx-qemu-jni.so"
echo "Bridge JNI instalada em $DEST/libzyx-qemu-jni.so"
echo "Isso não inclui libqemu-system-aarch64.so nem o adaptador QEMU ZyX; o boot permanece indisponível."
