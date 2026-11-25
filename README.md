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

# Sonatype Nexus Repository Core

Sonatype Nexus Repository is the single source of truth for all your internal and third-party binaries, components, and packages. Integrate all your development tools into a centralized binary repository manager so that you can choose the best open source components, optimize your build performance, and ship code quickly while increasing visibility across your SDLC.

This is the open source codebase of Nexus Repository Core. This contains functionality for maven, raw, and APT repository formats, and uses an embedded H2 database that is appropriate for small workloads.

For a fully-featured version of Nexus Repository, download the Community Edition binary from https://www.sonatype.com/products/nexus-community-edition-download. Community Edition includes additional format support such as npm, Docker, NuGet, PyPI and many others. It also allows use of an external PostgreSQL database that allows you to deploy Nexus Repository under Kubernetes.

## Fork local (rama `feature/pub-format`)

Este fork está rebaseado sobre `release-3.98.1-01` y añade funciones tipo Pro implementadas
desde especificaciones públicas, sin copiar código propietario de Sonatype:

- Formato **Pub/Flutter** (hosted, proxy y group): `PubFormat`, recetas REST y tests.
- **SAML 2.0 SSO** con OpenSAML 4.0.1 (Apache 2.0), realm, login SP-initiated, ACS y REST de
  configuración. Detalles en [SAML.md](SAML.md).
- **LDAP** con opciones avanzadas, realm propio, REST ampliado y pantalla de administración
  activada también en la UI clásica. Detalles en [LDAP.md](LDAP.md).
- **Tema azul** en la UI clásica y en la vista moderna (Nexus One), incluidos logos e iconos
  de la Welcome. Detalles y procedimiento de reconstrucción en [UI_THEME.md](UI_THEME.md).
- **Auditoría, stubs de UI, Data Store y Webhooks**: ver [PRO_FEATURES.md](PRO_FEATURES.md).

### Notas de construcción del fork

El enforcer de 3.98 exige JDK 25, pero la matriz oficial soporta Java 21 en runtime: compilar
con JDK 21 y `-Denforcer.skip=true`. Para los módulos frontend usar Node 24 (con Node 26 falla
`corepack yarn install`). El tag no incluye `yarn.lock`, así que se generó con
`nodeLinker: node-modules` en `.yarnrc.yml` porque Maven invoca Yarn con `--immutable`.

La instancia local se sirve desde
`public/selfhosted/assemblies/nexus-repository-core/target/assembly` en `http://localhost:8081`.

#### Issues

If you are using Nexus Repository Core or Community Edition and need to report an issue or request an enhancement, [open an issue here](https://github.com/sonatype/nexus-public/issues).

For help with Nexus Repository Core or Community Edition, please join the [Sonatype Community](https://community.sonatype.com/) to get tips and tricks from other users.

To report a security vulnerability, please see https://www.sonatype.com/report-a-security-vulnerability

Sonatype Nexus Repository Pro customers can contact our world-class support team at https://support.sonatype.com/.

## Build Requirements

Builds use Apache Maven and require Java 21 or later. Apache Maven wrapper scripts are included in the source tree.

## Building From Source

Released versions are tagged and branched using a name of the form `release-{version}`. For example: `release-3.89.0-09`

To build a tagged release, first fetch all tags:

```shell
git fetch --tags
```

Then checkout the remote branch you want. For example:

```shell
git checkout -b release-3.89.0-09 origin/release-3.89.0-09 --
```

### Initialize Yarn Workspaces

Before building with Maven, initialize the Yarn workspaces (required for frontend modules):

```shell
# Enable corepack (ships with Node.js 16.10+)
corepack enable

# Install workspace dependencies
corepack yarn install
```

### Build

Build using the included Maven wrapper script:

```shell
./mvnw clean install -Ppublic
```

## Running

To run Nexus Repository Core after building:

1. Navigate to `public/selfhosted/assemblies/nexus-repository-core/target/assembly`
2. Run `java -jar bin/nexus-repository-core-*.jar`

The application will create a sonatype-work directory at `public/selfhosted/assemblies/nexus-repository-core/target/sonatype-work`, 
which will contain the default administrator credentials, database, and file blobstore.

## License

This project is licensed under the Eclipse Public License - v 1.0, you can read the full text [here](LICENSE.txt)
