# nexus-repository-pub

Formato `pub` para el fork de Nexus Repository: repositorios **hosted**, **proxy** y **group**
para paquetes Dart/Flutter definidos en la *Hosted Pub Repository Specification v2*.

## Recetas

| Receta | Descripción |
|---|---|
| `pub-hosted` | Publica paquetes propios y sirve `/api/packages/<name>` y los `.tar.gz`. |
| `pub-proxy` | Cachea `pub.dev` reescribiendo los `archive_url` para que las descargas pasen por Nexus. |
| `pub-group` | Fusiona por nombre y versión los documentos de los miembros del grupo. |

## Cliente

```bash
export PUB_HOSTED_URL=https://<nexus>/repository/pub-group/
dart pub get
```

Para publicar en un hosted (requiere HTTPS):

```bash
export PUB_HOSTED_URL=https://<nexus>/repository/pub-hosted/
dart pub token add https://<nexus>/repository/pub-hosted/
dart pub publish
```

El flujo de publicación implementa `GET /api/packages/versions/new`, el `POST` multipart y el
`GET` de finalización devuelto en la cabecera `Location`.

## Notas de implementación

- Los documentos JSON se generan al vuelo desde los atributos de los assets, nunca se almacenan como
  un asset que pueda quedar obsoleto tras una publicación o un borrado.
- `pubspec.yaml` se parsea con SnakeYAML en modo `SafeConstructor`; se registra el SHA-256 completo
  del archivo para el campo `archive_sha256`.
- El proxy usa el *metadata cache controller* para JSON y el de contenido para los tarballs, con
  `Accept-Encoding: identity` para poder reescribir las URLs antes de cachear.
- Sin soporte para attestations (campo opcional `attestationUrl`).
