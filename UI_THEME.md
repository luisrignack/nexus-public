# Tema azul de la interfaz en el fork

El fork sustituye el verde original por azul tanto en la **UI clásica** (ExtJS) como en la
**vista moderna** (Nexus One / Radix Themes). El cambio se hizo modificando variables,
fuentes Sass y assets propios; no se ha copiado código propietario de Sonatype.

## Paleta usada

| Uso | Valor anterior | Valor nuevo |
|---|---|---|
| Tema clásico principal | `#29b473` / `rgb(41, 180, 115)` | `#006BBF` |
| Verde oscuro clásico | `#0B9743`, `#0B893D` y derivados | `#0047B2`, `#003B7A` |
| Verde lima clásico | `#84C900` | `#2476C3` |
| Acento Radix (cabecera, menú, botones) | `accentColor="green"` / `--accent-9: #138653` | `accentColor="blue"` / `--accent-9: #2d36ec` |
| Iconos de acceso rápido Welcome | `rgba(0,184,145) → rgba(0,189,111)` | `rgba(0,107,191) → rgba(0,118,195)` |
| Logos SVG | `#05b96c`, `#05bb6d`, `#00bb6c`, `#29b473` | `#006BBF`, `#2476C3` |

Los colores rojo/naranja de salud, umbrales y errores se conservan: son estados semánticos,
no parte del tema. Algunos indicadores de éxito en otras pantallas siguen usando el token
`--green-*` de Radix; el cambio cubre el tema global, cabecera, Welcome y assets.

## Ficheros modificados

### UI clásica

- `public/common/components/nexus-rapture/src/main/baseapp/packages/local/rapture-theme/sass/etc/colors.scss`
  — fuente de las variables del tema.
- `public/common/components/nexus-rapture/src/main/resources/static/rapture/resources/baseapp-prod_{1,2,3}.css`
  y `baseapp-debug_{1,2,3}.css` — CSS compilado servido.
- `public/common/components/nexus-rapture/src/main/resources/static/rapture/resources/loading-debug.css`.
- SVGs de `.../nexus-rapture/src/main/resources/static/rapture/resources/`: `favicon.svg`,
  `images/nxrm-logo-white.svg`, `images/sonatype-repository-logo*.svg`,
  `icons/x16|x32|x100/sonatype-repository-icon*.svg`, `404-error-image.svg`.

### Vista moderna (Nexus One)

- `public/common/components/nexus-coreui-plugin/src/frontend/src/App.jsx` — `<Theme>` raíz
  (cabecera y menú) e interior.
- `public/common/components/nexus-ui-plugin/src/frontend/src/contexts/ThemeContext.jsx` —
  `document.body.setAttribute('data-accent-color', 'blue')`.
- `public/common/components/nexus-ui-plugin/src/frontend/src/components/layout/LoginLayout.jsx`
  y `.../preview/shared/GlobalHeader/GlobalHeader.jsx` — badge de edición y botones de cambio
  de UI.
- `.../preview/shared/Navigation/Navigation.scss` y
  `public/common/components/nexus-coreui-plugin/src/frontend/src/components/LeftNavigationMenu/LeftNavigationMenuRadix.scss`
  — `$repository-primary` del elemento activo del menú.
- `.../preview/pages/Welcome/` y `.../pages/user/Welcome/OutreachActions.scss` — tarjetas,
  iconos de acceso rápido y acentos de la vista Welcome (las dos implementaciones, preview y
  clásica, tienen su propio SCSS).
- `.../art/logos/*.svg` — wordmark, mark y logos de edición.
- `.../interface/editionUtils.ts` y `.../pages/login/LoginPage.jsx` — cache-buster de las URLs
  de logos/favicon.

## Cache-buster

Los recursos estáticos se sirven con `Cache-Control: max-age=2592000`. Por eso, además de
cambiar los archivos, hay que cambiar la URL para que el navegador descargue la versión nueva:

```properties
# $data-dir/etc/nexus.properties
nexus.webresources.cachebuster=fork-blue-11
```

Los SVG de logos tienen su propio sufijo (`?_c=fork-blue-6`) en `editionUtils.ts` y el
favicon del login `?_c=fork-blue-8` en `LoginPage.jsx`. Si se vuelven a recolorar los assets,
hay que subir ambos valores y el `nexus.webresources.cachebuster`.

## Reconstrucción

Con JDK 21, Node 24 y Maven:

```shell
export JAVA_HOME=/ruta/a/jdk-21
export PATH=/ruta/a/node-v24/bin:$JAVA_HOME/bin:/ruta/a/maven/bin:$PATH
export MAVEN_OPTS="-Xmx3g -Djava.net.preferIPv4Stack=true"

mvn -B -Denforcer.skip=true -Dnode.version=v24.21.0 -DskipTests \
  -pl public/common/components/nexus-rapture \
      public/common/components/nexus-ui-plugin \
      public/common/components/nexus-coreui-plugin install

rm -f public/selfhosted/assemblies/nexus-repository-core/target/nexus-repository-core-3.98.1-01.jar{,.original}

mvn -B -Denforcer.skip=true -Dnode.version=v24.21.0 -DskipTests \
  -pl public/selfhosted/assemblies/nexus-repository-core package
```

Después se reinicia la assembly y se actualiza `nexus.webresources.cachebuster`.

## Verificación

- `GET /service/rest/v1/status` → `200`.
- `baseapp-prod_3.css` servido: `#006BBF` presente, tonos verdes anteriores ausentes.
- Bundle servido `nexus-coreui-bundle.js`: `accentColor:"blue"` y
  `setAttribute("data-accent-color","blue")`, sin variantes verdes.
- En el navegador: `document.body.dataset.accentColor === 'blue'`,
  `--accent-9 === #2d36ec` y cero elementos con `[data-accent-color="green"]`.
- Comprobado en las dos vistas Welcome (Nexus One y clásica): los tres iconos de
  *System Health Check*, *Cleanup Policies* y *Browse* son azules.
