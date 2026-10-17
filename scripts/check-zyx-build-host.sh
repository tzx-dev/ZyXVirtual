#!/usr/bin/env sh
# Preflight de recursos para checkout/build do ZyX OS (LineageOS 23.2).
set -u

MIN_RAM_KIB=$((64 * 1024 * 1024))
MIN_DISK_KIB=$((400 * 1024 * 1024))
TARGET_DIR=${1:-${LINEAGE_DIR:-.}}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  cat <<'EOF'
Uso: scripts/check-zyx-build-host.sh [diretorio]

Verifica os requisitos mínimos recomendados para checkout/build do ZyX OS:
Linux x86_64, pelo menos 64 GiB de RAM e 400 GiB livres no filesystem do
checkout. O diretório informado precisa existir. O script não baixa fontes,
não altera a árvore e não inicia uma compilação.
EOF
  exit 0
fi

if [ "$#" -gt 1 ]; then
  echo "Uso: scripts/check-zyx-build-host.sh [diretorio]" >&2
  exit 2
fi
if [ ! -d "$TARGET_DIR" ]; then
  echo "Preflight falhou: diretório de destino não existe: $TARGET_DIR" >&2
  echo "Crie o diretório e rode o preflight antes de sincronizar as fontes." >&2
  exit 2
fi

HOST_OS=$(uname -s 2>/dev/null || echo desconhecido)
HOST_ARCH=$(uname -m 2>/dev/null || echo desconhecido)
CPU_COUNT=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo desconhecido)
printf 'Sistema: %s\nArquitetura: %s\nCPUs lógicas: %s\n' "$HOST_OS" "$HOST_ARCH" "$CPU_COUNT"
printf 'Diretório/filesystem verificado: %s\n' "$TARGET_DIR"

FAIL=0
if [ "$HOST_OS" != "Linux" ]; then
  echo "ERRO: o build LineageOS requer um host Linux suportado." >&2
  FAIL=1
fi
case "$HOST_ARCH" in
  x86_64|amd64) ;;
  *)
    echo "ERRO: arquitetura $HOST_ARCH; é necessário host x86_64." >&2
    FAIL=1
    ;;
esac

if [ -r /proc/meminfo ]; then
  RAM_KIB=$(awk '/^MemTotal:/ { print $2; exit }' /proc/meminfo)
else
  RAM_KIB=
fi
CGROUP_LIMIT_BYTES=
if [ -r /sys/fs/cgroup/memory.max ]; then
  CGROUP_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
elif [ -r /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
  CGROUP_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
fi
case "$CGROUP_LIMIT_BYTES" in
  ''|max|*[!0-9]*) ;;
  *)
    CGROUP_RAM_KIB=$((CGROUP_LIMIT_BYTES / 1024))
    case "$RAM_KIB" in
      ''|*[!0-9]*) RAM_KIB=$CGROUP_RAM_KIB ;;
      *)
        if [ "$CGROUP_RAM_KIB" -lt "$RAM_KIB" ]; then
          RAM_KIB=$CGROUP_RAM_KIB
        fi
        ;;
    esac
    ;;
esac
case "$RAM_KIB" in
  ''|*[!0-9]*)
    echo "ERRO: não foi possível medir a RAM total do host." >&2
    FAIL=1
    ;;
  *)
    printf 'RAM efetiva (host/limite cgroup): %s GiB\n' "$((RAM_KIB / 1024 / 1024))"
    if [ "$RAM_KIB" -lt "$MIN_RAM_KIB" ]; then
      echo "ERRO: são necessários pelo menos 64 GiB de RAM efetiva." >&2
      FAIL=1
    fi
    ;;
esac

DISK_KIB=$(df -Pk "$TARGET_DIR" 2>/dev/null | awk 'NR == 2 { print $4 }')
case "$DISK_KIB" in
  ''|*[!0-9]*)
    echo "ERRO: não foi possível medir o espaço livre em $TARGET_DIR." >&2
    FAIL=1
    ;;
  *)
    printf 'Espaço livre: %s GiB\n' "$((DISK_KIB / 1024 / 1024))"
    if [ "$DISK_KIB" -lt "$MIN_DISK_KIB" ]; then
      echo "ERRO: são necessários pelo menos 400 GiB livres no filesystem do checkout." >&2
      FAIL=1
    fi
    ;;
esac

if [ "$FAIL" -ne 0 ]; then
  echo "Host não aprovado. Nenhum checkout ou build foi iniciado." >&2
  exit 1
fi

echo "Host aprovado para iniciar a preparação do build ZyX OS."
