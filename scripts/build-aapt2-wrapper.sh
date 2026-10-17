#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/tools/aapt2"
CC=${CC:-aarch64-linux-gnu-gcc}

if [ "$(uname -m)" != "aarch64" ] && [ "$(uname -m)" != "arm64" ]; then
  echo "AAPT2 wrapper is only needed on ARM64 hosts; skip on $(uname -m)."
  exit 0
fi
command -v "$CC" >/dev/null 2>&1 || {
  echo "Missing compiler: $CC" >&2
  exit 127
}
"$CC" -O2 -Wall -Wextra -Werror "$ROOT/tools/aapt2-wrapper.c" -o "$OUT"
chmod +x "$OUT"
file "$OUT"
