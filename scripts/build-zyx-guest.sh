#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
LINEAGE_DIR=${LINEAGE_DIR:-${AOSP_DIR:-}}
OUT_DIR=${OUT_DIR:-$ROOT/build/guest}

if [ -z "$LINEAGE_DIR" ]; then
  echo "ZyX OS guest build bloqueado: defina LINEAGE_DIR para uma árvore LineageOS/AOSP completa." >&2
  exit 2
fi
if [ ! -f "$LINEAGE_DIR/build/envsetup.sh" ]; then
  echo "ZyX OS guest build bloqueado: $LINEAGE_DIR não parece uma árvore AOSP/LineageOS (build/envsetup.sh ausente)." >&2
  exit 2
fi
if [ ! -f "$ROOT/guest/zyx-os/device/zyx/virtual/AndroidProducts.mk" ]; then
  echo "ZyX OS guest build bloqueado: overlay de produto ausente." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
cd "$LINEAGE_DIR"
# shellcheck disable=SC1091
. build/envsetup.sh
lunch zyx_virtual-userdebug
printf '%s\n' "Building ZyX OS from $LINEAGE_DIR into $OUT_DIR"
# The product overlay must be copied/linked into the source tree by the integrator.
# We refuse to guess a source layout or claim an image exists when the product is not registered.
if ! get_build_var PRODUCT_NAME 2>/dev/null | grep -qx zyx_virtual; then
  echo "ZyX OS guest build bloqueado: o overlay não está registrado nesta árvore." >&2
  exit 3
fi
m -j"${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}" systemimage bootimage vendorbootimage
cp -f out/target/product/zyx_virtual/system.img "$OUT_DIR/" 2>/dev/null || true
cp -f out/target/product/zyx_virtual/boot.img "$OUT_DIR/" 2>/dev/null || true
cp -f out/target/product/zyx_virtual/vendor_boot.img "$OUT_DIR/" 2>/dev/null || true
printf '%s\n' "Guest artifacts copied to $OUT_DIR"
