#!/usr/bin/env bash
# Exporta todas las imágenes del stack a un único tar para llevarlas a otra PC.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

OUT="${1:-nexus-antivirus-images.tar}"
NEXUS_IMAGE="${NEXUS_IMAGE:-nexus-fork:3.98.1-01-local}"
BRIDGE_IMAGE="${BRIDGE_IMAGE:-nexus-malware-bridge:local}"
CLAMAV_IMAGE="${CLAMAV_IMAGE:-docker.m.daocloud.io/clamav/clamav:1.4.7-debian}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-docker.m.daocloud.io/library/postgres:15-alpine}"

echo "Guardando imágenes en $OUT ..."
docker save -o "$OUT" "$NEXUS_IMAGE" "$BRIDGE_IMAGE" "$CLAMAV_IMAGE" "$POSTGRES_IMAGE"
ls -lh "$OUT"
