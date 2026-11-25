# Nexus Repository fork + antivirus (Docker Compose)

Paquete desplegable en otra PC. Levanta tres servicios:

| Servicio | Qué hace | Puerto |
|---|---|---|
| `nexus` | Fork de Nexus Repository Core 3.98.1-01 compilado desde el código fuente | `8081` |
| `clamav` | Motor antivirus ClamAV con firmas persistentes y `clamd` por TCP | interno `3310` |
| `malware-bridge` | API HTTP `POST /scan` que envía bytes a `clamd` (protocolo INSTREAM) | `127.0.0.1:8082` |

No hace falta instalar ClamAV ni Trivy/Grype en el host: todo corre en contenedores.

## Requisitos

- Docker 24+ con Compose v2 (o Podman + `podman-compose`).
- ~4 GB de RAM libres (Nexus 2 GB + ClamAV).
- Puertos `8081` (Nexus) y, opcionalmente, `8082` (bridge de escaneo en localhost).
- Salida a internet para la primera compilación (Maven/npm) y para descargar firmas de ClamAV.

## Opción A — llevar solo esta carpeta (imagen preconstruida)

En la PC donde compilaste:

```bash
cd docker/nexus-antivirus
cp .env.example .env
docker compose build
docker image save nexus-fork:3.98.1-01-local nexus-malware-bridge:local \
  -o nexus-antivirus-images.tar
```

Copia a la otra PC **la carpeta `docker/nexus-antivirus` y el `.tar`**:

```bash
docker load -i nexus-antivirus-images.tar
cd docker/nexus-antivirus
cp .env.example .env        # si no la copiaste
# Comenta el bloque `build:` de docker-compose.yml para usar solo las imágenes cargadas
docker compose up -d
```

## Opción B — llevar el repositorio y compilar allí

Copia el repositorio completo (o el `nexus-public` con el código fuente) y ejecuta:

```bash
cd nexus-public/docker/nexus-antivirus
cp .env.example .env
docker compose up -d --build
```

El `Dockerfile` hace la compilación completa (JDK 21, Node 24, `corepack yarn install`, Maven con
`-Denforcer.skip=true`) y copia solo la assembly a una imagen con JRE 21.

## Primera puesta en marcha

```bash
docker compose ps                       # espera a que nexus esté healthy
curl -i http://localhost:8081/service/rest/v1/status
docker compose exec nexus cat /nexus-data/admin.password
```

- Usuario: `admin`; contraseña inicial: la del fichero anterior. Cámbiala al entrar.
- La primera arrancada de ClamAV descarga las firmas: puede tardar varios minutos. El `healthcheck`
  tiene `start_period: 300s` y Nexus espera a que esté sano antes de arrancar.

## Probar el antivirus

Desde la PC donde está el stack (no necesita `clamdscan` instalado):

```bash
./scripts/scan-file.sh /ruta/al/fichero
```

Respuesta esperada:

```json
{"verdict":"CLEAN","signature":null,"engine":"clamd","raw":"OK"}
```

Para probar una detección, usa el fichero de prueba EICAR:

```bash
printf 'X5O!P%%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\n' > /tmp/eicar.txt
./scripts/scan-file.sh /tmp/eicar.txt
```

También puedes llamar al bridge desde otro contenedor de la red `nexus-net`:

```bash
curl --data-binary @fichero -H 'Content-Type: application/octet-stream' \
  http://malware-bridge:8082/scan
```

## Datos y copias de seguridad

- `nexus-data`: base de datos H2, blobs y configuración (`etc/nexus.properties`).
- `clamav-db`: firmas descargadas (evita volver a descargarlas).
- `clamav-log`: logs de `clamd`/`freshclam`.

```bash
docker compose down
docker run --rm -v nexus-antivirus_nexus-data:/data -v "$PWD:/backup" \
  alpine tar czf /backup/nexus-data.tgz -C /data .
docker compose up -d
```

## Configuración

- `.env`: puertos, tamaños de JVM, tags de imagen y `MAX_SCAN_BYTES`.
- `entrypoint.sh` añade a `$KARAF_DATA/etc/nexus.properties` (sin sobrescribir lo existente):
  - `nexus.previewui.audit.enabled=true`
  - `nexus.webresources.cachebuster=${NEXUS_CACHEBUSTER}`
  - `nexus.health.check.encryption=false` (evita el health check de clave por defecto)
- `clamav/clamd.conf` y `clamav/freshclam.conf`: parámetros del motor antivirus.

## Estado de la integración

El fork ya puede escanear **bajo demanda desde la propia REST de Nexus**:

```bash
# Login previo y cookie/sesión de admin
curl -u admin:<password> -H 'Content-Type: application/json' \
  -d '{"repository":"maven-releases","path":"/com/ejemplo/x.jar"}' \
  http://localhost:8081/service/rest/internal/ui/malware/scan

curl -u admin:<password> \
  http://localhost:8081/service/rest/internal/ui/malware/counts
```

El veredicto se guarda en los atributos del asset (`nxMalwareVerdict`, `nxMalwareSignature`) y los
recuentos ya no son un stub. Pendiente para la Fase 2: escaneo automático en `AssetUploadedEvent`,
bloqueo/cuarentena de assets `INFECTED` y re-escaneo cuando se actualicen las firmas.

## Solución de problemas

- `nexus` arranca pero `curl` da 401: es normal, la REST exige sesión; comprueba el estado con el
  usuario `admin` recién creado.
- ClamAV tarda en estar healthy: es la primera descarga de firmas; revisa `docker compose logs -f clamav`.
- El bridge devuelve 503: no puede conectar con `clamd`; comprueba la red y `docker compose logs clamav`.
- Cambios de `nexus.properties`: se conservan en el volumen; el entrypoint solo añade claves ausentes.
