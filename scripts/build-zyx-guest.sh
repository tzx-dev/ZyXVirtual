#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
LINEAGE_DIR=${LINEAGE_DIR:-${AOSP_DIR:-}}
OUT_DIR=${OUT_DIR:-$ROOT/artifacts/zyxos}
OVERLAY_SOURCE=$ROOT/guest/zyx-os/device/zyx/zyx_virtual
DEVICE=zyx_virtual
PRODUCT=lineage_zyx_virtual
BRANCH=lineage-23.2

if [ -z "$LINEAGE_DIR" ]; then
  echo "Build do ZyX OS bloqueado: defina LINEAGE_DIR para uma árvore LineageOS 23.2 completa." >&2
  exit 2
fi
LINEAGE_DIR=$(CDPATH= cd -- "$LINEAGE_DIR" 2>/dev/null && pwd) || {
  echo "Build do ZyX OS bloqueado: LINEAGE_DIR não existe." >&2
  exit 2
}
case "$OUT_DIR" in
  /*) ;;
  *) OUT_DIR=$ROOT/$OUT_DIR ;;
esac
if [ ! -f "$LINEAGE_DIR/build/envsetup.sh" ] || [ ! -d "$LINEAGE_DIR/.repo" ]; then
  echo "Build do ZyX OS bloqueado: $LINEAGE_DIR não é uma árvore LineageOS/repo completa." >&2
  exit 2
fi
if ! "$ROOT/scripts/check-zyx-build-host.sh" "$LINEAGE_DIR"; then
  echo "Build do ZyX OS bloqueado antes de alterar a árvore ou iniciar m." >&2
  exit 2
fi
if [ ! -f "$OVERLAY_SOURCE/AndroidProducts.mk" ] || [ ! -f "$OVERLAY_SOURCE/lineage.dependencies" ]; then
  echo "Build do ZyX OS bloqueado: overlay ZyX incompleto." >&2
  exit 2
fi
python3 -m json.tool "$OVERLAY_SOURCE/lineage.dependencies" >/dev/null

DEVICE_DEST=$LINEAGE_DIR/device/zyx/zyx_virtual
if [ -e "$DEVICE_DEST" ]; then
  if ! diff -qr "$OVERLAY_SOURCE" "$DEVICE_DEST" >/dev/null 2>&1; then
    echo "Build do ZyX OS bloqueado: $DEVICE_DEST já existe e difere do overlay versionado." >&2
    echo "Preserve suas alterações; sincronize/revise o overlay manualmente antes de tentar novamente." >&2
    exit 3
  fi
else
  mkdir -p "$(dirname "$DEVICE_DEST")"
  cp -a "$OVERLAY_SOURCE" "$DEVICE_DEST"
fi

mkdir -p "$OUT_DIR"
BUILD_META=$OUT_DIR/.zyx-build-meta
(
  cd "$LINEAGE_DIR"
  # shellcheck disable=SC1091
  . build/envsetup.sh
  # breakfast resolves the public LineageOS dependencies declared by lineage.dependencies.
  breakfast "$DEVICE"
  if [ ! -f device/virt/virtio_arm64only/BoardConfig.mk ]; then
    echo "Árvore base device/virt/virtio_arm64only ausente após breakfast." >&2
    exit 4
  fi
  if [ ! -f device/virt/virtio_arm64/BoardConfig.mk ]; then
    echo "Árvore dependente device/virt/virtio_arm64 ausente após breakfast." >&2
    exit 4
  fi
  if [ ! -d device/virt/virtio-common ]; then
    echo "Árvore dependente device/virt/virtio-common ausente após breakfast." >&2
    exit 4
  fi
  lunch "$PRODUCT-userdebug"
  actual_product=$(get_build_var PRODUCT_NAME)
  if [ "$actual_product" != "$PRODUCT" ]; then
    echo "Produto inesperado: $actual_product (esperado $PRODUCT)." >&2
    exit 4
  fi
  product_out=$(get_build_var PRODUCT_OUT)
  case "$product_out" in
    /*) ;;
    *) product_out=$LINEAGE_DIR/$product_out ;;
  esac
  build_id=$(get_build_var BUILD_ID)
  printf '%s\n%s\n' "$product_out" "$build_id" > "$BUILD_META"
  m "-j${JOBS:-4}" vm-utm-zip
)

PRODUCT_OUT=$(sed -n '1p' "$BUILD_META")
BUILD_ID=$(sed -n '2p' "$BUILD_META")
rm -f "$BUILD_META"
if [ ! -d "$PRODUCT_OUT" ]; then
  echo "Build terminou sem diretório de produto: $PRODUCT_OUT" >&2
  exit 5
fi
UTM_ZIP=$(find "$PRODUCT_OUT" -type f -path '*/VirtualMachine/UTM/*' -name 'UTM-VM-lineage-*.zip' -print | sort | tail -n 1)
if [ -z "$UTM_ZIP" ] || [ ! -s "$UTM_ZIP" ]; then
  echo "Build terminou, mas não gerou o pacote UTM esperado em $PRODUCT_OUT." >&2
  echo "Nenhum artefato ZyX será declarado pronto." >&2
  exit 5
fi
STAMP=$(date -u +%Y%m%d-%H%M%S)
PACKAGE=$OUT_DIR/zyxos-lineage-23.2-arm64-$STAMP.zyxos
python3 "$ROOT/scripts/package-zyx-guest.py" --source "$UTM_ZIP" --output "$PACKAGE" --lineage-branch "$BRANCH" --product "$PRODUCT" --build-id "${BUILD_ID:-unknown}"
printf 'Pacote ZyX OS: %s\n' "$PACKAGE"
