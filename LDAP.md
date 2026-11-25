# LDAP en el fork

El fork incorpora la configuración LDAP de Nexus Repository Pro en Core: el realm
`LdapRealm`, el directorio JNDI y la pantalla de administración (`LdapPage`) son código propio.
Esta nota describe las opciones añadidas en la última ampliación y su estado.

## Opciones nuevas

| Campo | Por defecto | Efecto |
|---|---|---|
| `connectionRetries` | `1` | Reintentos adicionales al abrir la conexión (usa `connectionRetryDelaySeconds` entre intentos). |
| `sizeLimit` | `0` | Límite de entradas por búsqueda de usuarios/grupos (`0` = sin límite; si es > 0 sustituye a `maxIncidentsCount` en la verificación de usuarios). |
| `timeLimitSeconds` | `0` | Límite de tiempo JNDI por búsqueda (`0` = sin límite explícito). |
| `searchBehavior` | `EXACT` | Coincidencia del usuario: `EXACT`, `SUBANY`, `SUBINIT` o `SUBFINAL` (los tres últimos construyen un filtro con comodines). |
| `userLastNameAttribute` | — | Atributo de apellido; se aplica al aprovisionar usuarios (JIT). |
| `userCompanyAttribute` | — | Atributo de empresa/departamento; se expone en el resultado de "Verify User Mapping". |
| `groupDynamicMemberAttribute` | — | Atributo del grupo con reglas dinámicas (p. ej. `memberURL`); sustituye a `userMemberOfAttribute` cuando se indica. |
| `groupLdapFilter` | — | Término adicional ANDado al filtro de búsqueda de grupos estáticos. |
| `defaultMembersGroup` | — | Rol concedido siempre a todo usuario autenticado por LDAP. |
| `groupLabelAttribute` | — | Etiqueta legible del grupo; se persiste y se envía a la UI (reservada para el mapeo de roles). |

Todos los campos se persisten en la capability LDAP (`LdapCapabilityProperties`) y viajan en
`LdapServerXO`. La pantalla los muestra en las secciones *Connection Settings*, *User Mapping* y
*Group Mapping*; el formulario conserva los valores al editar un servidor existente.

## Endpoints

| Método | Ruta | Cambio |
|---|---|---|
| `POST` | `/service/rest/v1/security/ldap/verify-login` | Ahora recibe `LdapVerifyLoginXO`: servidor + usuario/contraseña en Base64. |
| `GET` | `/service/rest/v1/security/ldap/templates` | Plantillas de esquema preconstruidas. |
| `DELETE` | `/service/rest/v1/security/ldap/cache` | Sin operación (el realm no cachea resultados). |
| `POST` | `/service/rest/v1/security/ldap/verify-connection`, `verify-user-mapping` | Restauran la contraseña enlazada almacenada cuando la UI reenvía el marcador de posición. |

`LdapUserXO` expone tanto los nombres antiguos (`userId`, `firstName`, `lastName`, `roles`) como
los de la UI actual (`username`, `realName`, `membership`, `company`).

## Seguridad

- LDAPS con `useTrustStore=true` usa ahora `trustStore.getSSLContext().getSocketFactory()`; antes se
  invocaba por reflexión un método inexistente y la opción no tenía efecto.
- Si el certificado de `lds.etecsa.cu` es autofirmado y no se activa el trust store de Nexus, hay que
  importarlo al `cacerts` de la JVM o arrancar con `-Djavax.net.ssl.trustStore=...`.

- El filtro de usuario aplica escape RFC 4515 al nombre de usuario; los comodines se añaden
  después del escape, de modo que `searchBehavior` no permite inyectar filtros.
- Las reglas dinámicas (`memberURL`) se leen del grupo y se combinan con una condición exacta de
  usuario antes de ejecutarse contra el directorio: una regla mal formada o demasiado amplia no
  puede conceder pertenencia a otros usuarios.
- Los valores `userMemberOfAttribute` y `groupDynamicMemberAttribute` son alternativas: basta uno
  para resolver grupos dinámicos.

## Verificación

| Conjunto | Resultado |
|---|---|
| `nexus-security` + `nexus-core` (`mvn test -Denforcer.skip=true`) | ✅ todos en verde |
| Jest: `ldapFormMachine`, `LdapForm`, `useLdapApi` | ✅ 220 tests en verde |
| Biome lint sobre los archivos cambiados | ✅ solo 4 avisos preexistentes en cadenas `${...}` |
| E2E: assembly 3.98.1-01 en Docker + LDAP en memoria (UnboundID) | ✅ ver abajo |

### E2E con directorio LDAP real

Se arrancó la assembly del fork en Docker contra un LDAP en memoria con dos usuarios y grupos
estáticos, y se comprobaron los endpoints:

- `POST verify-connection` → `200`.
- `POST verify-login` (usuario/contraseña en Base64) → `200`.
- `POST verify-user-mapping` → `200`, con `userId` correcto (`juan`, `ana`), `lastName`, `company`
  y los roles derivados de los grupos (`juan` → `devs`).
- `GET templates`, `DELETE cache`, `POST create`, `GET read` y `DELETE` → correctos; los diez
  campos nuevos se persisten y se devuelven en el XO.

Durante esta prueba se detectaron y corrigieron dos defectos del código heredado:

1. `verify-user-mapping` escapaba el comodín `*` usado para listar usuarios, por lo que siempre
   devolvía una lista vacía. Ahora la búsqueda de listado no escapa el comodín; las búsquedas por
   nombre de usuario siguen escapando su entrada (RFC 4515).
2. El identificador de usuario se construía con `String.valueOf(Attribute)` y salía como
   `uid: juan` en lugar de `juan`. Se usa `firstAttribute(...)` en las cuatro rutas afectadas
   (autenticación, verificación de mapeo, grupos dinámicos y formato de miembro de grupo).

Pendiente: validar reglas `memberURL` reales y búsquedas por substring (`SUBANY`/`SUBINIT`/
`SUBFINAL`) contra un directorio de producción.
