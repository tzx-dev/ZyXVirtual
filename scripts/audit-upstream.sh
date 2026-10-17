#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC=${1:-$ROOT/upstream/AEmulator}
OUT=${2:-$ROOT/build/reports/upstream-audit.txt}
mkdir -p "$(dirname "$OUT")"
{
  echo "ZyX Virtual upstream audit"
  echo "source=$SRC"
  echo "commit=$(git -C "$SRC" rev-parse HEAD 2>/dev/null || echo unknown)"
  echo
  echo "tracked files: $(git -C "$SRC" ls-files | wc -l)"
  echo "Kotlin lines: $(find "$SRC/app/src/main/java" -name '*.kt' -print0 | xargs -0 cat | wc -l)"
  echo "native lines: $(find "$SRC/native" -type f \( -name '*.c' -o -name '*.S' -o -name '*.h' \) -print0 | xargs -0 cat | wc -l)"
  echo
  echo "engine binaries:"
  find "$SRC/app/src/main/jniLibs" "$SRC/app/src/main/assets/engines" -type f -name '*.so' -printf '%P %s bytes\n' | sort
  echo
  echo "architecture markers:"
  rg -n "patched user-mode QEMU|Android 2.3|arm64-v8a|nativeLibraryDir|ANDROID_PROPERTY_WORKSPACE|DHD_BINDER|DHD_GL|/dev/eac" "$SRC/README.md" "$SRC/app/src/main/java" "$SRC/native" | head -n 160 || true
} > "$OUT"
printf '%s\n' "$OUT"
