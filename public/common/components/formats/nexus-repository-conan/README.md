# nexus-repository-conan

Formato **conan** para el fork Nexus Repository (EPL-1.0).

Recetas: `conan-hosted`, `conan-proxy`, `conan-group`.

Implementa el protocolo público de la API HTTP de Conan (v1 y v2) sobre el motor de
contenido de Nexus: recetas alojadas con subida de binarios, proxy con caché y reescritura
de `download_urls`, y grupo con fusión de listados.

---

## 1. Tipos de repositorio

| Receta | Tipo | Facets |
|---|---|---|
| `conan-hosted` | hosted | `ConanSecurityFacet`, `ConanContentFacet`, `SingleAssetMaintenanceFacet`, `SearchFacet`, `BrowseFacet` |
| `conan-proxy` | proxy | + `HttpClientFacet`, `NegativeCacheFacet`, `ConanProxyFacet`, `PurgeUnusedFacet` |
| `conan-group` | group | `GroupFacet`, `ConanContentFacet`, `SearchFacet`, `BrowseFacet` |

Ninguno necesita configuración de formato propia: el bloque `"conan"` de la API REST está
vacío (solo se declara para que el esquema sea estable).

---

## 2. Endpoints HTTP soportados

Todas las rutas son relativas al repositorio, es decir a `/repository/<nombre-repositorio>`.
`<ref>` = `conans/<name>/<version>/<user>/<channel>`; si el cliente omite `user`/`channel`
(conan 2) se usan `_` y `_`.

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/ping`, `/v1/ping`, `/v2/ping` | `200` + cabecera `X-Conan-Server-Version: 2.0` |
| `GET` | `/v1/conans/search?q=<glob>` | `200` `{"results":["name/version@user/channel", …]}` (siempre 200, puede venir vacío) |
| `GET` | `/<v1\|v2>/<ref>/revisions` | lista de revisiones de receta, la más reciente primero |
| `GET` | `/<v2>/<ref>/revisions/{rrev}/files` | `{"conanfile":{"reference":…, "revision":…, "files":{"/conanfile.py":{"size":n,"md5":"…"}}}}` |
| `GET` | `/<v1\|v2>/<ref>/revisions/{rrev}/files/{fichero}` | contenido del fichero (con `PartialFetchHandler`, admite `Range`) |
| `PUT` | `/<v1\|v2>/<ref>/revisions/{rrev}/files/{fichero}` | subida de un fichero de receta → `201` |
| `GET` | `/<v2>/<ref>/revisions/{rrev}/packages/{pkgid}/revisions` | lista de revisiones de paquete |
| `GET` | `/<v2>/<ref>/revisions/{rrev}/packages/{pkgid}/revisions/{prev}/files` | listado de binarios con `md5` |
| `GET` | `/<v1\|v2>/…/revisions/{prev}/files/{fichero}` | contenido del binario |
| `PUT` | `/<v1\|v2>/…/revisions/{prev}/files/{fichero}` | subida de un binario → `201` |
| `GET` | `/v1/<ref>/download_urls` | `{"conanfile":{"files":{"/fichero":{"url":…,"lastModified":n}}}}` |
| `GET` | `/v1/<ref>/packages/{pkgid}/download_urls` | ídem para un `package_id` |

Notas:

* Las rutas `…/revisions`, `…/files` y `…/download_urls` se sirven **sin** distinción de
  `v1`/`v2`: el prefijo de versión solo se elimina para construir la clave de caché.
* Cualquier otra ruta del espacio `conans/…` (`digest`, listado `…/packages`, `PUT` sobre
  rutas que no son ficheros) devuelve `404` en `GET` y `400` en `PUT`.
* Las listas `revisions` y `files` se generan **al vuelo** a partir de los assets
  almacenados; si no hay contenido bajo el prefijo se devuelve `404` (es lo que permite
  que un grupo pase al siguiente miembro).
* El campo `time` es ISO-8601 con offset (`2024-06-01T00:00:00Z`).

---

## 3. Layout de almacenamiento

```text
conans/{name}/{version}/{user}/{channel}/revisions/{rrev}/exports/{fichero}            # receta
conans/{name}/{version}/{user}/{channel}/revisions/{rrev}/packages/{pkgid}/revisions/{prev}/{fichero}   # binario
```

* Componente: `namespace = "{user}/{channel}"`, `name`, `version`.
* Atributos de componente: `attributes.conan.{baseVersion,user,channel,revision}`
  (visibles en la UI y usados por `SearchConan.js`).
* Atributos de asset: `attributes.conan.{filename,revision,md5,packageId,packageRevision}`.
* El `md5` se calcula en la subida (`TempBlob` con `md5/sha1/sha256/sha512`) y se guarda en
  `attributes.conan.md5`; es el que aparece en los listados `files`.

Un proxy además cachea los documentos JSON de la API bajo la misma ruta **sin** el prefijo
de versión (`conans/{…}/revisions`, `conans/{…}/download_urls`), de modo que la caché de un
grupo coincide con el layout de un hosted.

---

## 4. Cómo probarlo por REST

### 4.1 Crear los repositorios

```bash
NEXUS=http://localhost:8081
AUTH="admin:admin123"

curl -u "$AUTH" -X POST "$NEXUS/service/rest/v1/repositories/conan/hosted" \
  -H 'Content-Type: application/json' \
  -d '{"name":"conan-hosted","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":true}}'

curl -u "$AUTH" -X POST "$NEXUS/service/rest/v1/repositories/conan/proxy" \
  -H 'Content-Type: application/json' \
  -d '{"name":"conan-proxy","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":true},
       "proxy":{"remoteUrl":"https://center.conan.io"},
       "negativeCache":{"enabled":true,"timeToLive":1440},
       "httpClient":{"blocked":false,"autoBlock":false}}'

curl -u "$AUTH" -X POST "$NEXUS/service/rest/v1/repositories/conan/group" \
  -H 'Content-Type: application/json' \
  -d '{"name":"conan-group","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":true},
       "group":{"memberNames":["conan-hosted","conan-proxy"]}}'
```

Consultar / actualizar / listar:

```bash
curl -u "$AUTH" -i "$NEXUS/service/rest/v1/repositories/conan/hosted/conan-hosted"
curl -u "$AUTH" -X PUT "$NEXUS/service/rest/v1/repositories/conan/hosted/conan-hosted" \
  -H 'Content-Type: application/json' \
  -d '{"name":"conan-hosted","online":true,"conan":{},
       "storage":{"blobStoreName":"default","strictContentTypeValidation":true}}'
curl -u "$AUTH" "$NEXUS/service/rest/v1/repositories?repositoryType=hosted&format=conan"
```

### 4.2 Ping

```bash
curl -i "$NEXUS/repository/conan-hosted/v2/ping"
curl -i "$NEXUS/repository/conan-hosted/v1/ping"
curl -i "$NEXUS/repository/conan-proxy/v2/ping"
```

### 4.3 Subir y leer una receta (hosted)

```bash
RREV=0a1b2c3d
printf 'from conan import ConanFile\n' > /tmp/conanfile.py

curl -i -X PUT --data-binary @/tmp/conanfile.py \
  "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/files/conanfile.py"

curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions"
curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/files"
curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/files/conanfile.py"
```

### 4.4 Subir y leer un binario de paquete

```bash
PKGID=da39a3ee5e6b4b0d3255bfef95601890afd80709
PREV=1234abcd
echo "fake-binary" > /tmp/libz.so

curl -i -X PUT --data-binary @/tmp/libz.so \
  "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/packages/$PKGID/revisions/$PREV/files/libz.so"

curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/packages/$PKGID/revisions"
curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/packages/$PKGID/revisions/$PREV/files"
curl -i "$NEXUS/repository/conan-hosted/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/packages/$PKGID/revisions/$PREV/files/libz.so"
```

### 4.5 Endpoints v1

```bash
curl -i "$NEXUS/repository/conan-hosted/v1/conans/search?q=zlib*"
curl -i "$NEXUS/repository/conan-hosted/v1/conans/search?q=zlib*%20lib*"
curl -i "$NEXUS/repository/conan-hosted/v1/conans/zlib/1.2.13/_/_/download_urls"
curl -i "$NEXUS/repository/conan-hosted/v1/conans/zlib/1.2.13/_/_/packages/$PKGID/download_urls"
```

### 4.6 Proxy

```bash
curl -i "$NEXUS/repository/conan-proxy/v2/ping"
curl -i "$NEXUS/repository/conan-proxy/v2/conans/zlib/1.2.13/_/_/revisions"
curl -i "$NEXUS/repository/conan-proxy/v2/conans/zlib/1.2.13/_/_/revisions/$RREV/files"
curl -i "$NEXUS/repository/conan-proxy/v1/conans/search?q=zlib*"
```

El cuerpo de `download_urls` devuelto por el proxy tiene las URLs reescritas para que
apunten a `/repository/conan-proxy/…` en lugar del host remoto.

### 4.7 Grupo

```bash
curl -i "$NEXUS/repository/conan-group/v2/ping"
curl -i "$NEXUS/repository/conan-group/v2/conans/zlib/1.2.13/_/_/revisions"
curl -i "$NEXUS/repository/conan-group/v1/conans/search?q=zlib*"
curl -i "$NEXUS/repository/conan-group/v1/conans/zlib/1.2.13/_/_/download_urls"
```

* `revisions` y `search` se **fusionan** de todos los miembros (unión, más reciente primero).
* `files`, ficheros individuales y `download_urls` los sirve el **primer miembro** que
  responda; en `download_urls` las URLs del miembro se reescriben a la del grupo.

---

## 5. Tests unitarios

`mvn -o -B -pl public/common/components/formats/nexus-repository-conan test`

| Clase | Nº | Cubre |
|---|---|---|
| `ConanApiPathTest` | 17 | parseo de rutas, defaults `_/_`, revisiones/paquetes/ficheros, `filePath`/`contentPrefix`, endpoints no implementados, utilidades de `ConanCoordinates` |
| `ConanMetadataBuilderTest` | 12 | JSON de `revisions` (orden y `content_url`), `files` con `md5` y claves `/…`, `download_urls`, `search`, fusión de documentos de grupo |
| `ConanDownloadUrlsRewriterTest` | 9 | reescritura de raíz remota, *fallback* por cambio de origen, prefijo miembro → grupo, extracción de URLs |
| `ConanSearchMatcherTest` | 8 | wildcards `*` y `?` de `/v1/conans/search`, varias expresiones, escaping de regex |
| **Total** | **46** | sin red, sin Spring |

---

## 6. Limitaciones

1. **Endpoints no implementados** de la API Conan: `digest`, listado de `package_id`
   (`…/revisions/{rrev}/packages`), `PUT` sobre rutas que no son ficheros y cualquier
   operación de borrado (`DELETE`). Devuelven `404`/`400`.
2. **Sin `digest` ni `content_url` reales en v1**: las URLs de `download_urls` se construyen
   apuntando a los propios endpoints `files` de Nexus.
3. **Búsqueda en hosted por escaneo**: `GET /v1/conans/search` recorre los assets del
   repositorio (`path LIKE 'conans/%'`). Correcto pero lineal; no usa el índice de
   componentes de la UI.
4. **Sin paginación** de listados.
5. **Grupo**: los ficheros (`files` y descarga) se resuelven con el primer miembro que
   responde; no se fusionan contenidos de distintos miembros para un mismo fichero. Un
   `download_urls` servido por un miembro offline/otros no se reescribe si ese miembro no
   está en la lista del grupo.
6. **Proxy y ping**: el `ping` se reenvía al remoto; si el remoto no lo contesta, el
   cliente recibe el error del remoto (y puede degradar a v1). Las respuestas de `search`
   **no** se cachean (dependen de la query) y los documentos JSON se piden con
   `Accept-Encoding: identity`.
7. **Reescritura de URLs con origen distinto**: si el remoto devuelve una URL fuera de la
   `remoteUrl` configurada (CDN, host alternativo), solo se sustituye el origen, de modo que
   la ruta original se conserva pero puede no ser servida por Nexus.
8. **`time` en ISO-8601 con offset** en lugar del formato numérico que usan algunos
   servidores Conan 1.x.
9. **Sin configuración de formato**: no hay campos propios en el formulario del repositorio
   (no existe equivalente a `conanVersion` en el backend).
10. **Sin tareas de mantenimiento específicas**: se usan las genéricas de
    `SingleAssetMaintenanceFacet` y las políticas de limpieza
    (`LAST_BLOB_UPDATED`, `LAST_DOWNLOADED`, `REGEX`).
