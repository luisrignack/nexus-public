# Integración con Kaspersky (licencia oficial)

El bridge de antivirus ya está preparado para tres motores. Nexus no cambia: sigue llamando a
`POST http://malware-bridge:8082/scan` y recibe el mismo JSON. Solo se cambia `SCAN_ENGINE`.

| `SCAN_ENGINE` | Cuándo usarlo | Configuración |
|---|---|---|
| `clamav` | Valor actual; ClamAV en el contenedor `clamav` | `CLAMAV_HOST`, `CLAMAV_PORT` |
| `kaspersky-cli` | Servidor Linux con Kaspersky ejecutable local (`kavscan`, `kesl-control`, etc.) | `KASPERSKY_SCAN_CMD` con placeholder `{file}` |
| `kaspersky-rest` | Kaspersky Security Center u otra API HTTPS con token | `KASPERSKY_API_URL`, `KASPERSKY_TOKEN` o `KASPERSKY_TOKEN_FILE` |

## Modo CLI

Ejemplo para Kaspersky Anti-Virus for Linux File Server:

```env
SCAN_ENGINE=kaspersky-cli
KASPERSKY_SCAN_CMD=kavscan --scan-file {file}
```

Para Endpoint Security for Linux:

```env
SCAN_ENGINE=kaspersky-cli
KASPERSKY_SCAN_CMD=/opt/kaspersky/kesl/bin/kesl-control --scan-file {file}
```

El bridge escribe el payload en un fichero temporal dentro del contenedor, ejecuta el comando y
borra el fichero. El contenedor `malware-bridge` debe tener el binario y la licencia disponibles.
Para ello, mounts opcionales en `docker-compose.yml` (sección `malware-bridge`):

```yaml
volumes:
  - ./kaspersky:/opt/kaspersky:ro
  - ./kaspersky-license:/etc/kaspersky/license:ro
```

La licencia **nunca** se copia dentro de la imagen ni se sube a Git.

## Modo REST (Kaspersky Security Center)

```env
SCAN_ENGINE=kaspersky-rest
KASPERSKY_API_URL=https://ksc.ejemplo.local:443/api/v1/scan
KASPERSKY_TOKEN_FILE=/run/secrets/kaspersky_token
KASPERSKY_VERIFY_TLS=true
```

- Si el certificado es interno, montar la CA o usar `KASPERSKY_VERIFY_TLS=false` solo en pruebas.
- El token se pasa como `Authorization: Bearer ...`. Usar un fichero montado como secreto es preferible
  a ponerlo en `.env`.
- El bridge acepta respuestas JSON con claves `verdict`/`status`/`result` y `signature`/`threat`/
  `detection`/`virus`. Si la API del equipo usa otros nombres, se ajusta el parseo en `app.py`.

## Verificación

```bash
curl -s http://127.0.0.1:8082/healthz
# {"status":"ok","engine":"kaspersky-cli"}

printf 'X5O!P%%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\n' > /tmp/eicar.txt
./scripts/scan-file.sh /tmp/eicar.txt
```

## Qué pedir al equipo de Ciberseguridad

1. Producto y versión exactos (Security Center, Anti-Virus for Linux, Endpoint Security for Linux…).
2. Si hay API REST: URL base, endpoint de escaneo, modelo de respuesta y usuario/token de servicio.
3. Si solo hay CLI: ruta exacta del binario, sintaxis del comando y cómo se aprovisiona la licencia.
4. Red: IP/puerto del servidor Kaspersky y reglas de firewall desde el host de Nexus.
5. Política: qué hacer con un positivo (solo marcar, bloquear, borrar) y quién revisa los falsos positivos.

Con esos datos solo hay que rellenar `.env` (y, si aplica, los mounts); no hace falta recompilar Nexus.
