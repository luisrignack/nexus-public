# SAML 2.0 en el fork

Implementación propia del service provider SAML 2.0, escrita desde la especificación pública con
OpenSAML 4.0.1 (Apache 2.0). No se ha portado código del plugin Pro de Sonatype.

## Componentes

- `org.sonatype.nexus.security.authc.saml.SamlRealm` — realm Shiro (`SamlRealm`), aprovisionamiento JIT y mapeo de grupos a roles.
- `org.sonatype.nexus.internal.security.saml.SamlLoginFlow` — construcción de `AuthnRequest`, consumo y validación de la `SAMLResponse`.
- `org.sonatype.nexus.internal.security.saml.SamlLoginServlet` — endpoints `/saml/login` y `/saml/acs`.
- `org.sonatype.nexus.saml.SamlEntityMetadata` — parseo del metadata del IdP y extracción del certificado de firma.
- `org.sonatype.nexus.internal.security.saml.rest.SamlConfigurationResource` — REST de configuración y metadata del SP.
- Capability oculta `saml`, activable desde la configuración; habilita el realm.

## Endpoints

| Método | Ruta | Uso |
|---|---|---|
| `GET/PUT/DELETE` | `/service/rest/internal/ui/saml` | Configuración (la usa la pantalla de administración). |
| `GET` | `/service/rest/internal/ui/saml/metadata` | Metadata XML del service provider. |
| `GET` | `/saml/login` | Inicia el login SP-initiated. |
| `POST` | `/saml/acs` | Recibe la respuesta del IdP. |

Privilegios: `nx-saml-settings:read` y `nx-saml-settings:update`.

## Configuración

Campos del JSON: `enabled`, `entityId`, `idpMetadata` (XML), `usernameAttribute`,
`firstNameAttribute`, `lastNameAttribute`, `emailAttribute`, `groupsAttribute`,
`validateResponseSignature` y `validateAssertionSignature` (tri-state).

Si `entityId` se deja vacío se usa la URL del ACS como audience. Para `usernameAttribute`
el valor especial `nameid` (por defecto) usa el `NameID` de la assertion.

## Seguridad

- Se valida la firma de la respuesta o de la assertion contra el certificado publicado en el metadata del IdP.
- Se comprueban `Destination`, `Audience`, `NotBefore`/`NotOnOrAfter` (con skew de 120 s),
  `SubjectConfirmationData` y `InResponseTo` del estado de un solo uso.
- Se mantiene una caché de assertions ya consumidas para evitar replay.
- El parseo XML deshabilita DTD y entidades externas.
- El metadata del IdP se trata como entrada no confiable; un XML inválido se rechaza con mensaje claro.

## Límites actuales

- Un único IdP por instancia.
- No se firman las peticiones `AuthnRequest` (sp-initiated básico).
- La caché de replay/estado es local al nodo; en HA hace falta una caché compartida.
- El build del frontend (`yarn install`) no se pudo verificar en el entorno de desarrollo;
  los cambios de UI son pequeños (activación del bundle `nexus-saml-plugin` y del tile de ajustes).
