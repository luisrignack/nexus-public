#!/usr/bin/env bash
# Escanea un archivo con el ClamAV del stack sin instalar clamdscan en el host.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Uso: $0 <fichero>" >&2
  exit 2
fi

PORT="${MALWARE_BRIDGE_PORT:-8082}"
curl -fsS --max-time 300 \
  -H 'Content-Type: application/octet-stream' \
  --data-binary "@$1" \
  "http://127.0.0.1:${PORT}/scan"
echo
