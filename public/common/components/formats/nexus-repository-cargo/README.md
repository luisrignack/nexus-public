<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2008-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
# nexus-repository-cargo

Formato **cargo** (Rust crates) para el fork Nexus Repository (EPL-1.0).

- Format name: `cargo`
- Recipes: `cargo-hosted`, `cargo-proxy`, `cargo-group`
- ExtJS views `CargoHosted` / `CargoProxy` / `CargoGroup` (ya registradas en el UI) - no envían campos propios,
  por eso el modelo REST solo lleva los bloques estándar `storage` / `cleanup` / `proxy` / `negativeCache` /
  `httpClient` / `group` más el bloque vacío `cargo`.

Base de contenido: `http://host:8081/repository/<repo>/`

## Endpoints soportados

| Método | Ruta (relativa al repositorio) | Descripción |
|---|---|---|
| GET | `index/config.json` | Configuración del registro sparse: `{"dl":"<base>api/v1/crates","api":"<base>api"}` con URLs absolutas de este repositorio |
| GET | `index/{prefix}/{crate}` | Índice newline-delimited JSON, un registro de versión por línea, ordenado por versión ascendente |
| GET | `api/v1/crates/{crate}/{version}/download` | Descarga del `.crate` (`Content-Type: application/gzip`) |
| GET | `api/v1/crates?q=&page=&per_page=` | Búsqueda estilo crates.io: `{"crates":[...],"meta":{"total":N}}` |
| GET | `api/v1/crates/{crate}` | Detalle de crate (`cargo info`): `{"crate":{...},"versions":[...]}` |
| PUT | `api/v1/crates/{crate}/{version}` | Publish con cuerpo JSON = registro del índice **o** bytes del `.crate` |
| PUT | `api/v1/crates/{crate}/{version}/upload` | Publish con el `.crate` (ambas formas aceptadas en la misma ruta) |
| DELETE | `api/v1/crates/{crate}/{version}` | Borra el `.crate` y su registro del índice |
| GET/HEAD | cualquier otra ruta | 404 (raíz del repo: 302 a `index/config.json` en proxies) |

`prefix` (todo en minúsculas): longitud 1 → `1/{name}`; 2 → `2/{name}`; 3 → `3/{first}`; ≥ 4 →
`{f0}{f1}/{f2}{f3}`.

Los headers `cargo-protocol` y `User-Agent: cargo/...` se aceptan en silencio (no se valida nada específico).

### Publish (hosted)

Se aceptan las dos formas de cliente sobre la misma ruta:

1. **JSON**: `PUT api/v1/crates/{crate}/{version}` con `Content-Type: application/json` (o un cuerpo que empiece por
   `{`) conteniendo el registro del índice
   (`{"name","vers","deps","cksum","features","yanked","pubtime",...}`). El registro se valida contra la ruta:
   `name` (insensible a mayúsculas) y `vers` deben coincidir; si no, responde **400** con
   `{"errors":[{"detail":"..."}]}`.
2. **Tarball**: `PUT .../upload` (o la misma ruta sin sufijo) con `Content-Type: application/octet-stream` y los
   bytes del `.crate`. El servidor calcula el SHA-256 (`cksum`) y lo fusiona en el registro del índice (creando un
   registro mínimo si aún no existe).

Éxito: **200** con `{"ok":true}`.

## Layout de almacenamiento

| Ruta | Contenido |
|---|---|
| `crates/{crate}/{crate}-{version}.crate` | Archivo `.crate` (tar gzipped), adjunto al componente `{crate}/{version}` (nombre en minúsculas) |
| `index/{prefix}/{crate}` | **Fichero de registros hermano** en ndjson: una línea JSON por versión, ordenada por semver. Es a la vez el registro persistido y el documento sparse que se sirve en GET |
| atributos del asset `index/{prefix}/{crate}` | Metadatos de búsqueda: `cargo.name`, `cargo.max-version`, `cargo.description`, `cargo.downloads` (los lee la búsqueda sin abrir el payload) |
| atributos del asset `index/config.json` (proxy) | `cargo.remote-dl` / `cargo.remote-api`: `dl`/`api` del upstream, para enrutar llamadas a la API |

Decisión documentada: **el registro por versión se guarda como fichero de registros hermano** (`index/{prefix}/{crate}`,
ndjson) en lugar de como atributo de asset; se reescribe (merge + sort semver) en cada publish y es el mismo
documento que se sirve, así que GET es una lectura directa.

## Proxy

- `proxy.remoteUrl` = raíz de un registro sparse compatible con crates.io, p. ej. `https://index.crates.io`.
- `index/...` → `<remoteUrl>/...` (se elimina el prefijo `index/`): `index/config.json` → `<remoteUrl>/config.json`.
- El `index/config.json` cacheado **se reescribe** para que `dl` y `api` apunten a este repositorio; los valores
  upstream originales se recuerdan en atributos del asset.
- `api/v1/crates/...` (búsqueda, detalle y descarga del `.crate`) → base `api`/`dl` anunciadas por el
  `config.json` upstream (igual que hace el propio cliente cargo; p. ej. `https://crates.io/api`), de modo que la
  descarga acaba en `static.crates.io` vía el `dl` de crates.io. Hasta que el `config.json` upstream no esté
  cacheado se usa como alternativa `<remoteUrl>/api/v1/crates/...`.
- Caché: `index/` y `api/` usan `metadataMaxAge`; las descargas de `.crate` usan `contentMaxAge`; los 404 del
  upstream usan `negativeCache`. Las peticiones salen con `Accept-Encoding: identity` para poder reescribir JSON.

## Group

- `index/config.json` se sintetiza apuntando al propio grupo.
- Índice, detalle y descarga: **primer miembro que tenga el crate**.
- Búsqueda: se consultan todos los miembros y se fusionan los `crates` de-duplicando por `id` (gana el primer
  miembro).

## Probarlo por REST

Crear repositorios:

```bash
# hosted
curl -si -u admin:admin123 -H 'Content-Type: application/json' \
  -X POST http://localhost:8081/service/rest/v1/repositories/cargo/hosted \
  -d '{"name":"cargo-hosted","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":false,"writePolicy":"ALLOW"},
       "cleanup":{"policyNames":[]}}'

# proxy
curl -si -u admin:admin123 -H 'Content-Type: application/json' \
  -X POST http://localhost:8081/service/rest/v1/repositories/cargo/proxy \
  -d '{"name":"cargo-proxy","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":false},
       "proxy":{"remoteUrl":"https://index.crates.io","contentMaxAge":1440,"metadataMaxAge":1440},
       "negativeCache":{"enabled":true,"timeToLive":1440},
       "httpClient":{"connection":{"timeout":30,"retries":3}}}'

# group
curl -si -u admin:admin123 -H 'Content-Type: application/json' \
  -X POST http://localhost:8081/service/rest/v1/repositories/cargo/group \
  -d '{"name":"cargo-group","online":true,
       "storage":{"blobStoreName":"default","strictContentTypeValidation":false},
       "group":{"memberNames":["cargo-hosted","cargo-proxy"]}}'
```

Index sparse y descargas (hosted):

```bash
curl -s http://localhost:8081/repository/cargo-hosted/index/config.json
curl -s http://localhost:8081/repository/cargo-hosted/index/de/mo/demo   # prefix de "demo" (4 chars)
curl -s http://localhost:8081/repository/cargo-hosted/api/v1/crates?q=demo
curl -s 'http://localhost:8081/repository/cargo-hosted/api/v1/crates?q=demo&page=1&per_page=10'
curl -s http://localhost:8081/repository/cargo-hosted/api/v1/crates/demo
curl -si http://localhost:8081/repository/cargo-hosted/api/v1/crates/demo/0.1.0/download
```

Publish (registro JSON) y publish (tarball), con los headers de cargo aceptados en silencio:

```bash
curl -si -u admin:admin123 -H 'Content-Type: application/json' \
  -H 'User-Agent: cargo/1.80.0' -H 'cargo-protocol: 1' \
  -X PUT http://localhost:8081/repository/cargo-hosted/api/v1/crates/demo/0.1.0 \
  -d '{"name":"demo","vers":"0.1.0","deps":[],"cksum":"","features":{},"yanked":false,
       "pubtime":"2024-05-01T12:00:00Z"}'

printf 'demo' > /tmp/demo.txt && tar -czf /tmp/demo-0.1.0.crate -C /tmp demo.txt
curl -si -u admin:admin123 -H 'Content-Type: application/octet-stream' \
  -H 'User-Agent: cargo/1.80.0' -H 'cargo-protocol: 1' \
  --upload-file /tmp/demo-0.1.0.crate \
  http://localhost:8081/repository/cargo-hosted/api/v1/crates/demo/0.1.0/upload

# borrado
curl -si -u admin:admin123 -X DELETE http://localhost:8081/repository/cargo-hosted/api/v1/crates/demo/0.1.0
```

Grupo y proxy (mismas rutas):

```bash
curl -s http://localhost:8081/repository/cargo-group/index/config.json
curl -s 'http://localhost:8081/repository/cargo-group/api/v1/crates?q=demo'
curl -s http://localhost:8081/repository/cargo-group/api/v1/crates/demo
curl -s http://localhost:8081/repository/cargo-proxy/index/config.json   # reescrito hacia el proxy
curl -s 'http://localhost:8081/repository/cargo-proxy/api/v1/crates?q=tokio'
```

Cliente cargo (`~/.cargo/config.toml`):

```toml
[registries]
nexus = { index = "sparse+http://localhost:8081/repository/cargo-hosted/index/" }

[source.crates-io]
replace-with = "nexus-local"
[source.nexus-local]
registry = "sparse+http://localhost:8081/repository/cargo-group/index/"
```

`cargo publish --registry nexus ...` (con usuario/password de Nexus) publica en el hosted.

## Limitaciones conocidas

- Solo se implementa el protocolo **sparse** (`sparse+https://`); el índice git legado no está soportado.
- La búsqueda del hosted responde `description: ""`, `downloads: 0` y `updated_at` = fecha de publicación, salvo que
  el registro JSON publicado incluya los campos extra `description` / `downloads` (el protocolo de publicación de
  cargo no los envía).
- El publish bufferiza el `.crate` en memoria para calcular el SHA-256 (los crates son pequeños; no aplica a blobs
  gigabytes).
- El grupo fusiona la **página** que cada miembro devuelve en la búsqueda (`meta.total` es el tamaño de la fusión);
  no se re-paginan los miembros con un `per_page` mayor.
- El grupo no fusiona ficheros de índice entre miembros: gana el primer miembro que tenga el crate.
- No existen endpoints dedicados de yank/unpublish (`PUT` con registro `yanked:true` sí lo persiste) ni de
  `cargo login`; la autenticación es la de Nexus (Basic).
- `GET api/v1/crates/{crate}/{version}` (sin `/download`) responde 404.
- El proxy necesita leer primero el `config.json` upstream para enrutar `api/v1/crates/...`; si el upstream no lo
  publica, esas llamadas caen en `<remoteUrl>/api/v1/crates/...`.

## Tests

`src/test/java`:

- `CargoCoordinatesHelperTest` (11 tests): prefijos sparse para nombres de 1, 2, 3 y ≥4 caracteres, paths de
  almacenamiento, parsing de rutas de download/publish/búsqueda/detalle, codificación de query.
- `CargoIndexBuilderTest` (14 tests): orden semver (incl. pre-release), generación/merge/orden del ndjson,
  SHA-256/`cksum`, validación de registro contra la ruta, `config.json` generado y reescrito (campos desconocidos
  conservados), filtrado y paginación de búsqueda, respuesta de detalle y fusión de búsqueda de grupo.

```bash
mvn -o -B -pl public/common/components/formats/nexus-repository-cargo test
```
