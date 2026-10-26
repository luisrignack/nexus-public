# nexus-repository-alpine

Formato **Alpine Linux (`apk`)** para el fork Nexus Repository, licencia EPL-1.0.
Implementado desde el formato público de paquetes APK v2 y del índice `APKINDEX.tar.gz`
(no se ha portado código de las ediciones comerciales de Sonatype).

Recetas: `alpine-hosted`, `alpine-proxy`, `alpine-group`.

## Layout

Alpine publica un árbol de directorios plano:

```
<branch>/<repository>/<arch>/<paquete>-<version>.apk
<branch>/<repository>/<arch>/APKINDEX.tar.gz
```

Ejemplo real: `v3.20/main/x86_64/musl-1.2.5-r4.apk`.
El repositorio de Nexus no impone el layout: acepta cualquier prefijo y genera el índice de cada
directorio que contenga `.apk`.

## Endpoints

Bajo la base del repositorio (`http://host:8081/repository/<repo>/`):

| Método | Ruta | Respuesta |
|---|---|---|
| GET/HEAD | `/<prefijo>/<arch>/APKINDEX.tar.gz` | índice generado (hosted/group) o cacheado (proxy) |
| GET/HEAD | `/<prefijo>/<arch>/<paquete>.apk` | paquete |
| PUT/POST | `/<prefijo>/<arch>/<paquete>.apk` | alta de paquete (solo hosted) |
| DELETE | `/<prefijo>/<arch>/<paquete>.apk` | borrado (solo hosted) |
| GET | `/` | página de aterrizaje `text/html` |

## Hosted

1. `PUT` del `.apk` (APK v2: gzip con miembros *signature* + control `.PKGINFO` + datos).
2. El módulo infla **solo el miembro de control**, lee `.PKGINFO` y calcula el `C:` del índice
   como SHA1 de los bytes comprimidos de ese miembro, en Base64 con prefijo `Q1`
   (exactamente lo que hace `apk index`).
3. Guarda el paquete y deja como atributo del asset la estancia completa del `APKINDEX`
   (`alpine.indexEntry`), de modo que generar el índice no vuelve a abrir los `.apk`.
4. `GET …/APKINDEX.tar.gz` empaqueta las estancias de los `.apk` del mismo directorio en un
   tar.gz con el miembro `APKINDEX`. El índice se regenera en cada petición: nunca queda obsoleto
   tras un alta o un borrado.

Las estancias generadas son **idénticas byte a byte** a las de `apk index` (comprobado con
`musl-1.2.5-r4.apk` y `aaudit-0.7.2-r3.apk` de `v3.20/main/x86_64`).

## Proxy

Reenvía `proxy.remoteUrl` + la ruta tal cual. No hay reescritura: `APKINDEX.tar.gz` no contiene
URLs autorreferentes y los `.apk` deben conservarse byte a byte. Se pide
`Accept-Encoding: identity` para cachear el binario sin recodificar.

## Group

- `APKINDEX.tar.gz`: descarga el índice de cada miembro, concatena las estancias y elimina
  duplicados por `P:`+`V:` (gana el primer miembro).
- `.apk`: se sirve del primer miembro que lo tenga.

## Uso con apk

```bash
BASE=http://localhost:8081/repository/alpine-hosted
# hosted: subir un paquete
curl -u admin:$NEXUS_PASSWORD -X PUT -H 'Content-Type: application/octet-stream' \
  --data-binary @musl-1.2.5-r4.apk \
  "$BASE/v3.20/main/x86_64/musl-1.2.5-r4.apk"

# consumir el repositorio
apk add --repository "$BASE/v3.20/main" --allow-untrusted musl
```

> El `Content-Type` debe ser binario (`application/octet-stream` o similar). Si se omite, `curl`
> envía `application/x-www-form-urlencoded` y Jetty intenta parsear el cuerpo como formulario,
> rechazándolo antes de que llegue al handler.

En `/etc/apk/repositories` se apunta al directorio que contiene `<arch>/APKINDEX.tar.gz`:

```
http://localhost:8081/repository/alpine-group/v3.20/main
```

## Tests

```bash
mvn -o -pl public/common/components/formats/nexus-repository-alpine test
```

- `AlpinePackageMetadataTest` (4): lectura de `.PKGINFO`, cálculo de `C:` (= SHA1 del miembro de
  control comprimido), estancia de índice, salto de cabeceras PAX y rechazo de payloads que no son
  APK v2.
- `AlpineIndexBuilderTest` (5): empaquetado tar.gz, parseo, deduplicación por nombre+versión,
  tolerancia a índices corruptos.

## Verificación E2E

Validado sobre la assembly real en `http://localhost:8081` con `apk-tools` 2.14.4 estático y
paquetes de `v3.20/main/x86_64` (26 comprobaciones, todas en verde):

- hosted: alta `201`, rechazo `400` de payloads no APK y de `PUT` sobre `APKINDEX.tar.gz`; índice
  idéntico a `apk index`; `.apk` byte a byte.
- `GET /` en hosted, proxy y group devuelve `200 text/html`; `HEAD` del índice devuelve `200`.
- proxy: índice y paquete byte a byte contra `https://dl-cdn.alpinelinux.org/alpine`.
- group: índice fusionado con paquetes de hosted y proxy, deduplicado por `P:`+`V:`.
- `apk add musl` desde hosted y `apk add busybox` desde group (`busybox` del proxy y `musl` del
  hosted, demostrando el merge de miembros).
- `DELETE 204`, descarga posterior `404` e índice vacío válido.

## Limitaciones

- Solo **APK v2** (gzip + tar + `.PKGINFO`). APK v3 / ADB (`apk-tools` 3.x) no está soportado.
- El índice generado **no va firmado** (`/.SIGN.RSA.…`): los paquetes conservan su firma y
  `apk` acepta índices sin firmar servidos por HTTP(S). Para un despliegue que exija índice
  firmado haría falta añadir la firma con la clave del repositorio.
- El proxy no reescribe `APKINDEX.tar.gz`; si el upstream publica índices firmados, se sirven tal cual.
