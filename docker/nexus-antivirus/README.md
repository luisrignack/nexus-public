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
- ~6 GB de RAM libres (Nexus 2 GB + PostgreSQL 1 GB + ClamAV).
- Puertos `8081` (Nexus) y, opcionalmente, `8082` (bridge de escaneo en localhost). PostgreSQL no se
  publica: solo escucha en la red interna `nexus-net`.
- Salida a internet para la primera compilación (Maven/npm), descargar imágenes y firmas de ClamAV.
- **Todas las imágenes se toman del mirror DaoCloud** (`docker.m.daocloud.io`), no de Docker Hub.
  Si la PC de destino todavía no lo tiene configurado, copia `docker/daemon.json.example` a
  `/etc/docker/daemon.json` y reinicia Docker (`sudo systemctl restart docker`). El tar de imágenes
  generado con `scripts/save-images.sh` evita tener que descargar nada en la PC remota.
- **Cambiar `POSTGRES_PASSWORD` en `.env`** antes del primer arranque.

## Opción A — llevar solo esta carpeta (imagen preconstruida)

En la PC donde compilaste:

En la PC donde compilaste (el script exporta **Nexus + bridge + ClamAV + PostgreSQL** en un tar):

```bash
cd docker/nexus-antivirus
cp .env.example .env
docker compose build
./scripts/save-images.sh
```

Copia a la otra PC **la carpeta `docker/nexus-antivirus` y el `.tar`**. Allí:

```bash
cd docker/nexus-antivirus
./scripts/load-and-deploy.sh nexus-antivirus-images.tar
```

El script carga las imágenes, crea `.env` si no existe, valida que hayas cambiado
`POSTGRES_PASSWORD` y levanta el stack. También puedes hacerlo a mano con `docker load -i` y
`docker compose up -d`.

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
docker compose ps                       # postgres, clamav y bridge deben estar healthy
curl -i http://localhost:8081/service/rest/v1/status
docker compose exec nexus cat /nexus-data/admin.password
```

- Usuario: `admin`; contraseña inicial: la del fichero anterior. Cámbiala al entrar.
- Nexus arranca solo cuando PostgreSQL responde `pg_isready`, por lo que el esquema se crea desde cero
  en el volumen `postgres-data` en el primer arranque.
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

| Volumen | Contenido |
|---|---|
| `nexus-data` | Blobs, configuración (`etc/nexus.properties`) y contraseña inicial de admin |
| `postgres-data` | Base de datos PostgreSQL (todo el metadata de repositorios, usuarios y tareas) |
| `clamav-db` | Firmas descargadas (evita volver a descargarlas) |
| `clamav-log` | Logs de `clamd`/`freshclam` |

Copia completa consistente:

```bash
docker compose exec -T postgres pg_dump -U "${POSTGRES_USER:-nexus}" "${POSTGRES_DB:-nexus}" \
  > nexus-postgres.sql
docker run --rm -v nexus-antivirus_nexus-data:/data -v "$PWD:/backup" \
  alpine tar czf /backup/nexus-data.tgz -C /data .
```

Restauración: `docker compose down`, restaurar `nexus-data.tgz`, `docker compose up -d postgres`,
cargar `psql -f nexus-postgres.sql` y después levantar el resto con `docker compose up -d`.

## Configuración

- `.env`: puertos, tamaños de JVM, tags de imagen, `MAX_SCAN_BYTES`, parámetros de PostgreSQL y
  motor de escaneo (`SCAN_ENGINE`; ver `KASPERSKY.md`).
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
recuentos ya no son un stub. El stack activa el escaneo automático al publicar con
`NEXUS_MALWARE_ON_UPLOAD=true`. Para re-escanear tras una actualización de firmas:

```bash
curl -u admin:<password> -X POST \
  http://localhost:8081/service/rest/internal/ui/malware/rescan
```

Si se define `NEXUS_MALWARE_DELETE_INFECTED=true`, los assets con veredicto `INFECTED` se eliminan
automáticamente (cuarentena por borrado). Por defecto solo se marcan.

## Solución de problemas

- `nexus` arranca pero `curl` da 401: es normal, la REST exige sesión; comprueba el estado con el
  usuario `admin` recién creado.
- ClamAV tarda en estar healthy: es la primera descarga de firmas; revisa `docker compose logs -f clamav`.
- El bridge devuelve 503: no puede conectar con `clamd`; comprueba la red y `docker compose logs clamav`.
- Cambios de `nexus.properties`: se conservan en el volumen; el entrypoint solo añade claves ausentes.
