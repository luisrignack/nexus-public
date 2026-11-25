#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="${KARAF_DATA:-/nexus-data}"
PROPS_DIR="${DATA_DIR}/etc"
PROPS="${PROPS_DIR}/nexus.properties"

mkdir -p "${PROPS_DIR}"
touch "${PROPS}"

# Añade una propiedad solo si no está ya presente, para no sobrescribir cambios del operador.
ensure_property() {
  local key="$1"
  local value="$2"
  if ! grep -q "^${key}=" "${PROPS}"; then
    printf '%s=%s\n' "${key}" "${value}" >> "${PROPS}"
  fi
}

ensure_property "application-host" "0.0.0.0"
ensure_property "application-port" "8081"
ensure_property "nexus.webresources.cachebuster" "${NEXUS_CACHEBUSTER:-docker-1}"
ensure_property "nexus.previewui.audit.enabled" "true"
# Evita el health check unhealthy de la clave de cifrado por defecto en una instalación nueva.
ensure_property "nexus.health.check.encryption" "false"
ensure_property "nexus.malware.bridgeUrl" "${MALWARE_BRIDGE_URL:-http://malware-bridge:8082/scan}"
ensure_property "nexus.malware.enabled" "${NEXUS_MALWARE_ENABLED:-true}"
ensure_property "nexus.malware.maxBytes" "${MAX_SCAN_BYTES:-209715200}"

# Permite pasar opciones JVM adicionales desde el entorno del contenedor.
exec "${KARAF_HOME:-/opt/sonatype/nexus}/bin/nexus" run
