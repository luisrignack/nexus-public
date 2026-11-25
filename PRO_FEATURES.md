# Funciones tipo Pro añadidas al fork

Este documento complementa `SAML.md`, `LDAP.md` y `UI_THEME.md`. Todo se ha implementado con
APIs públicas del Core y especificaciones abiertas; no se ha portado código de las ediciones
comerciales de Sonatype.

## 1. Auditoría activada y exportable

El backend de auditoría (`nexus-audit` + `nexus-audit-plugin`) ya estaba en el árbol: DAO H2 y
PostgreSQL, capability `audit`, productores de eventos, REST `/internal/ui/audit-log` y export
CSV. En el fork se ha habilitado y completado:

- `nexus.previewui.audit.enabled=true` en `$data-dir/etc/nexus.properties` (el Core lo deja
  desactivado por defecto; el fork lo activa de forma explícita).
- Búsqueda libre `q` en `GET /internal/ui/audit-log` y en `/export`: nuevos métodos
  `findAllWithSearch`, `countWithSearch`, `findByDomainsWithSearch` y `countByDomainsWithSearch`
  en `AuditEventDAO`/`AuditEventStore`, con condición `LIKE` sobre `domain`, `type`, `context`
  e `initiator` en `AuditEventDAO.xml`.
- El hook `useAuditLogApi` construye una única vez los parámetros de filtro y expone
  `downloadAuditLog()`; la página `AuditLogPage` añade el botón **Export CSV**, que descarga
  el mismo conjunto filtrado.

Tests: `nexus-audit-plugin` → 121 tests en verde.

## 2. Stubs de endpoints internos

El Core referencia tres servicios que solo existen en ediciones comerciales. El fork registra
respuestas vacías con la forma documentada para eliminar los 404 de consola:

| Endpoint | Respuesta |
|---|---|
| `GET /service/rest/internal/ui/healthcheck/summary` | `[]` |
| `GET /service/rest/internal/ui/malware/counts` | `{counts:{}, totalCount:0, hdsAvailable:false, hcEnabledRepos:[]}` |
| `GET /service/rest/v1/user-telemetry/javascript` | JS no-op que define `window.userTelemetry.initialize()` |
| `GET /service/rest/v1/user-telemetry/config` | `{}` |
| `POST /service/rest/v1/user-telemetry/events` | 204 |

Clases: `ProCompatibilityResource` y `UserTelemetryCompatibilityResource` en
`nexus-coreui-plugin`. **No aportan datos de salud ni de malware**: solo evitan errores; un
escaneo real requiere un motor abierto (por ejemplo ClamAV o Trivy) y cuarentena, que sigue
fuera del alcance actual.

## 3. Gestión de Data Store desde la UI

La pantalla `DataStorePage` ya existía pero no tenía backend ni estaba visible en Core.

- `DataStoreUIResource` (`nexus-datastore`) expone `GET` y `PUT /service/rest/internal/ui/datastore`,
  usando `DataStoreConfigurationManager.load()/save()`. La contraseña nunca se devuelve.
  `maximumConnectionPool` se mapea a `maximumPoolSize` dentro del atributo `advanced`.
- `DatastorePluginDescriptorImpl` activa el bundle `nexus-pro-datastore-plugin`.
- Se retiró el gate de ediciones de las rutas clásica y preview y de la tarjeta de Settings;
  se conserva el requisito de permiso de administrador.

Requiere reinicio para aplicar cambios de configuración de la base de datos, igual que el Core.

## 4. Webhooks

El backend de webhooks ya era completo (`nexus-webhooks`, `WebhookServiceImpl`, capability
`webhook.global`/`webhook.repository` y `WebhookInternalResource`). Se añadió una tarjeta
**Webhooks** en Settings que enlaza con la lista de capabilities filtrables, para que la
función sea descubrible sin crear una página duplicada.

## 5. Comprobación

Tras regenerar la assembly y reiniciar:

- `GET /service/rest/v1/status` → 200.
- `/internal/ui/audit-log`, `/export`, `/internal/ui/datastore`, `/internal/ui/healthcheck/summary`
  y `/internal/ui/malware/counts` → 401 sin sesión (recursos registrados; antes 404).
- `/v1/user-telemetry/javascript` y `/config` → 200; `/events` solo acepta POST.
- Cache-buster de recursos UI: `nexus.webresources.cachebuster=fork-pro-1`.

## 6. Escaneo de malware bajo demanda (Fase 1)

- `MalwareScanService` y `MalwareScanResource` en `nexus-coreui-plugin`:
  - `POST /service/rest/internal/ui/malware/scan` con `{"repository":"...","path":"..."}`.
  - `GET /service/rest/internal/ui/malware/counts` devuelve recuentos reales por repositorio.
  - El servicio localiza el asset con `AssetStore.readPath()`, lee el blob con
    `BlobStoreLocator`, lo envía al bridge por HTTP y persiste `nxMalwareVerdict` /
    `nxMalwareSignature` en los atributos del asset.
  - Configurable con `nexus.malware.bridgeUrl`, `nexus.malware.enabled` y
    `nexus.malware.maxBytes` (inyectados por `entrypoint.sh` en Docker).
- Se retiró el stub de `/malware/counts` de `ProCompatibilityResource` para evitar duplicidad.

Verificación E2E en el stack Docker: repositorio `raw` con el fichero EICAR de prueba;
`POST /scan` devolvió `INFECTED` / `Eicar-Signature` y `GET /counts` devolvió
`{counts:{malware-test:{INFECTED:1}}, totalCount:1, infectedCount:1}`.

### Fase 2 — escaneo automático

- `MalwareAutoScanSubscriber` se suscribe al bus de eventos y reacciona a `AssetCreatedEvent` y
  `AssetUploadedEvent` (no a `AssetAttributesEvent`, para evitar bucles). Se registra explícitamente
  con `EventManager` porque el bundle se carga en su propio contexto.
- `nexus.malware.onUpload` (por defecto `false` en el código, `true` en el stack Docker) activa el
  escaneo automático; `nexus.malware.deleteInfected=true` elimina el asset si el veredicto es
  `INFECTED` (cuarentena por borrado; por defecto desactivado).
- `POST /service/rest/internal/ui/malware/rescan` re-escanea todos los assets con blob, pensado
  para ejecutarse tras actualizar las firmas de ClamAV.

Verificación E2E: subir el fichero EICAR a un repositorio `raw` generó
`Automatic malware scan of auto-test9:/eicar.txt -> INFECTED` sin llamar a `/scan`, y `/counts`
mostró el repositorio con un asset infectado. El re-escaneo manual devolvió
`{scanned: N, infected: N}`.

## 7. Fuera de alcance (por esfuerzo/riesgo)

No incluidos en esta entrega: restore automático del backup, tags con productor real, Crowd,
SCIM, IP allowlist, service-account tokens, papelera, cuarentena automática de malware,
blob stores Azure/GCS (excluido explícitamente), replicación, HA y FIPS.
