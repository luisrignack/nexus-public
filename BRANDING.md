# Identidad visual del fork (ETECSA)

Se sustituyeron los logos, isotipos y la marca textual de Sonatype por los assets de ETECSA
proporcionados en `/home/ladmin/Pictures/etecsa/1`.

## Assets

| Asset | Origen | Uso |
|---|---|---|
| `logo-etecsa-wordmark.png` (947×230) | `23_Diseno_Estructura-Identidad_e_Imagen-3_...png` | Cabecera, login, logos de edición y pantalla de carga |
| `logo-etecsa-isotipo.png` (230×233) | `Imagotipo_azul_...png` | Menú colapsado, favicons e iconos |

Los SVG históricos (`logo-header-wordmark*.svg`, `logo-header-mark*.svg`, `logo-*-edition-header*.svg`,
`favicon*.svg`, `sonatype-repository-logo*.svg`, `nxrm-logo-white.svg`, iconos x16/x32/x100…) se
sustituyeron por envoltorios SVG que incrustan estos PNG, para no romper las rutas existentes.
También se regeneraron los PNG de favicon y el `.ico`.

## Texto

El texto visible de marca pasó a **"Gestor de repositorios empresarial"** en:

- `<title>` y meta descripción de `index.vm`.
- Título del navegador (fijado en `createRouter.js`).
- `alt` y `aria-label` del logo en login, cabecera y menú lateral.
- Modal *Acerca de*, tooltips, textos de API/tokens/Licencias y páginas de error.

Se conservan las referencias legales a Sonatype en `COPYRIGHT.vm` y en los ficheros `LICENSE`,
porque forman parte de la licencia EPL del código base.

## Cache

Las URLs de logo llevan `?_c=etecsa-1` y el `nexus.webresources.cachebuster` de la instancia local
se subió a `etecsa-3`. Al desplegar en otra máquina, incrementar este valor para forzar la descarga
de la nueva identidad visual.
