#!/usr/bin/env bash
# Carga el tar de imágenes (si se pasa) y levanta el stack en la PC de destino.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

if [ $# -ge 1 ] && [ -f "$1" ]; then
  echo "Cargando imágenes desde $1 ..."
  docker load -i "$1"
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Creado .env desde .env.example. EDITA POSTGRES_PASSWORD ANTES DE CONTINUAR."
  exit 1
fi

if grep -q 'POSTGRES_PASSWORD=CHANGE_ME' .env; then
  echo "ERROR: cambia POSTGRES_PASSWORD en .env antes de desplegar." >&2
  exit 2
fi

docker compose up -d

echo "=== Estado ==="
docker compose ps
echo "=== Contraseña inicial de admin ==="
docker compose exec -T nexus cat /nexus-data/admin.password
