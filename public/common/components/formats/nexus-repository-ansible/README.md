# nexus-repository-ansible

Formato **ansible** (Galaxy collections) para el fork Nexus Repository, licencia EPL-1.0.

Recetas: `ansible-hosted`, `ansible-proxy`, `ansible-group`.

## Endpoints servidos

Bajo la base del repositorio (`http://host:8081/repository/<repo>/`):

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v3/` | raíz de la API (`collection_index`, `available_versions`) |
| GET | `/api/v3/plugin/ansible/content/published/collections/index/` | listado paginado de todas las collections (`data` + `results`) |
| GET | `…/collections/index/{ns}/` | collections de un namespace |
| GET | `…/collections/index/{ns}/{name}/` | detalle de una collection (`highest_version`, `dependencies`, `version_count`) |
| GET | `…/collections/index/{ns}/{name}/versions/` | versiones, la más reciente primero |
| GET | `…/collections/index/{ns}/{name}/versions/{v}/` | detalle de versión (`artifact`, `manifest`, `download_url`) |
| GET | `/api/v3/collections/{ns}/{name}/{v}/download/` | tarball `application/octet-stream` |
| GET | `/api/v2/collections/{ns}/{name}/` | compatibilidad Galaxy v2 |
| GET | `/api/v2/collections/{ns}/{name}/versions/` y `…/{v}/` | compatibilidad Galaxy v2 |
| PUT | `…/collections/index/{ns}/{name}/versions/{v}/` (body = tar.gz) | alta de collection (hosted) |
| PUT | `/collections/{ns}/{name}/{v}/{ns}.{name}-{v}.tar.gz` | alta de collection (hosted, ruta canónica) |
| DELETE | `…/collections/index/{ns}/{name}/…` | borra todas las versiones de la collection |
| GET | cualquier otro path | se sirve como asset normal desde el blob store |

La raíz del repositorio redirige (`302`) a `api/v3/`.

## Layout de almacenamiento

```
collections/{namespace}/{name}/{version}/{namespace}.{name}-{version}.tar.gz
```

El `MANIFEST.json` interno del tarball es la fuente de verdad: si `namespace`/`name`/`version`
no coinciden con la URL, la petición se rechaza con **400** y un mensaje legible. Las coordenadas,
la descripción y los autores leídos del manifiesto se guardan como atributos del componente
(`ansible.description`, `ansible.authors`, `ansible.dependencies`), de modo que los documentos de
la API se generan sin volver a abrir los archives.

## Comportamiento por tipo de repositorio

- **hosted**: lee/escribe contra el almacén; genera la API Galaxy desde los componentes almacenados.
- **proxy**: reenvía a `proxy.remoteUrl` (p. ej. `https://galaxy.ansible.com`), cachea según
  `contentMaxAge`/`metadataMaxAge`/`negativeCache` y **reescribe las URLs absolutas** de las
  respuestas JSON para que apunten a este repositorio. Los endpoints de la API se cachean con el
  controlador de metadatos, los tarballs con el de contenido.
- **group**: fusiona los documentos de los miembros (la primera collection que conoce un miembro
  gana), reescribe las URLs de cada miembro a las del group y sirve los tarballs desde el miembro
  que los tenga.

## Pruebas por REST

```bash
BASE=http://localhost:8081/repository/ansible-hosted

# crear el repositorio
curl -u admin:$NEXUS_PASSWORD -X POST http://localhost:8081/service/rest/v1/repositories/ansible/hosted \
  -H 'Content-Type: application/json' -d '{
    "name":"ansible-hosted","online":true,
    "storage":{"blobStoreName":"default","strictContentTypeValidation":false},
    "ansible":{}
  }'

curl -s $BASE/api/v3/ | python3 -m json.tool          # raíz de la API
curl -s $BASE/api/v3/plugin/ansible/content/published/collections/index/   # listado (vacío al inicio)

# publicar una collection (tarball ya construido con `ansible-galaxy collection build`)
curl -u admin:$NEXUS_PASSWORD -X PUT \
  --data-binary @community-general-1.0.0.tar.gz \
  "$BASE/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/1.0.0/"

curl -s "$BASE/api/v3/plugin/ansible/content/published/collections/index/community/general/" | python3 -m json.tool
curl -OJ "$BASE/api/v3/collections/community/general/1.0.0/download/"
```

Cliente real:

```bash
ansible-galaxy collection install -p ./collections \
  --server http://localhost:8081/repository/ansible-group
```

## Tests

`mvn -o -B -pl public/common/components/formats/nexus-repository-ansible test` → 23 tests:

- `AnsibleCoordinatesHelperTest` (9): clasificación de rutas Galaxy v3/v2, coordenadas canónicas.
- `AnsibleCollectionMetadataTest` (5): lectura del `MANIFEST.json` dentro del tar.gz, validaciones.
- `AnsibleIndexBuilderTest` (9): comparación de versiones, fusión de listas de miembros,
  reescritura de URLs, documento raíz, fusión de detalles.

## Limitaciones conocidas

- Los documentos de listado llevan la colección dos veces (`data` para v3 y `results` para v2)
  para servir ambos clientes desde un único generador.
- El paginado de Galaxy (`links.next`) se responde siempre como `null`: los repositorios se
  sirven completos en una página (hasta 100 componentes por consulta interna, paginado por
  continuidad).
- `galaxy.ansible.com` devuelve 403 desde este entorno de red; el proxy debe validarse contra un
  Galaxy accesible (o un upstream falso local).
