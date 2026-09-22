# Sistema visual — Stitch "Panel Academia de Tenis"

> Extraído del proyecto de Stitch `projects/11670252332052436843` el 2026-09-22.
> Stitch genera HTML/React: sirve como **referencia visual**, no se copia código.
> El frontend real es Angular; estos valores se trasladan a
> `frontend/src/styles/tokens.css` como variables CSS.

Capturas de cada pantalla en [`frontend/design/`](design/):

| Archivo | Pantalla |
|---|---|
| `01-inicio-sesion.png` | Inicio de sesión (split style) |
| `02-admin-estudiantes.png` | Panel de administración — gestión de estudiantes |
| `03-ficha-estudiante.png` | Ficha de estudiante |
| `04-documentacion-expediente.png` | Documentación y expediente |
| `05-portal-familias.png` | Portal de familias |

Tema base: `LIGHT`, fuente `Inter` en todo el sistema (cuerpo, headlines, labels).

## Filosofía

"Precision Modernism" + densidad de datos, pensado para uso administrativo
intensivo (fichas, calendarios, listados). Sin gradientes decorativos ni
flourish de marketing: jerarquía estructural, bordes nítidos, feedback
operativo ajustado. Aplica directamente a este proyecto: paneles de
administración densos en datos (listados de estudiantes, documentación con
caducidades) y un portal de familias más ligero.

## Colores

Paleta con acento primario verde institucional oscuro y acento terciario verde
césped reservado para estados activos/confirmados.

| Token | Valor | Uso |
|---|---|---|
| `primary` | `#00130A` (aplicado como `#0F291E`) | Navegación global, botones primarios, headers estructurales |
| `on-primary` | `#FFFFFF` | Texto sobre primary |
| `primary-container` | `#0F291E` | Contenedores de énfasis primario |
| `secondary` | `#545F73` (Deep Slate) | Tablas de datos, cabeceras de columna, chrome secundario |
| `tertiary` / acento cancha | `#65A30D` (Technical Grass Court) | Highlights accionables, slots activos, sesiones confirmadas — uso quirúrgico |
| `background` | `#FAF8FF` | Canvas base |
| `surface-container-lowest` | `#FFFFFF` | Tarjetas y grids elevados |
| `surface-container` | `#EAEDFF` | Wells secundarios, cabeceras de tabla |
| `on-surface` | `#131B2E` | Texto principal |
| `on-surface-variant` | `#424844` | Texto secundario / metadatos |
| `outline` | `#727974` | Bordes estructurales |
| `outline-variant` | `#C2C8C2` | Bordes de inputs en reposo |
| `error` | `#BA1A1A` / contenedor `#FFDAD6` | Errores |

### Indicadores de estado operativo

| Estado | Texto | Fondo |
|---|---|---|
| Éxito / Disponible | `#15803D` | `#F0FDF4` |
| Aviso / Pendiente de confirmación | `#B45309` | `#FFFBEB` |
| Crítico / Bloqueado | `#B91C1C` | `#FEF2F2` |
| Info / Programado | `#0369A1` | `#F0F9FF` |

Traducible directamente a los estados de documento del dominio: `PENDING`
(aviso), `RECEIVED`/`REVIEWED` (éxito/info), `expired` calculado (crítico).

## Tipografía

Fuente única: **Inter**. Jerarquía por peso (400/500/600/700), no por tamaño
excesivo — los tamaños raramente superan 28px en vistas administrativas.

| Estilo | Tamaño / interlineado | Peso | Uso |
|---|---|---|---|
| `headline-xl` | 28px / 36px, tracking -0.02em | 700 | Títulos de página (desktop) |
| `headline-xl-mobile` | 22px / 28px | 700 | Títulos de página (mobile) |
| `headline-lg` | 20px / 28px | 600 | Secciones principales |
| `headline-md` | 16px / 24px | 600 | Subsecciones, cabeceras de tarjeta |
| `body-lg` | 15px / 22px | 400 | Texto de cuerpo destacado |
| `body-md` | 13px / 18px | 400 | Texto de cuerpo estándar |
| `body-md-medium` | 13px / 18px | 500 | Texto de cuerpo con énfasis |
| `label-lg` | 12px / 16px, tracking +0.02em | 600 | Etiquetas de control |
| `label-md` | 11px / 14px, tracking +0.04em, mayúsculas | 600 | Cabeceras de columna, descriptores de orden, badges |
| `caption` | 11px / 14px | 400 | Texto auxiliar |

Regla: los números en tablas/metadatos usan cifras tabulares
(`font-variant-numeric: tabular-nums`).

## Espaciado

Ritmo base en `rem`, escala corta y consistente:

| Token | Valor |
|---|---|
| `space-xs` | 0.25rem |
| `space-sm` | 0.5rem |
| `space-md` | 0.75rem |
| `space-lg` | 1.25rem |
| `space-xl` | 2rem |
| `gutter` | 1rem (1.25rem desktop) |
| `margin` | 1rem (2rem desktop) |

Contenedores de listas/datos usan padding ajustado (`space-sm`–`space-md`)
para maximizar densidad sin scroll continuo.

## Radios

Métrica "soft corner" — radio base 4px, deliberadamente contenido:

| Token | Valor | Uso |
|---|---|---|
| `sm` | 0.125rem | — |
| `DEFAULT` | 0.25rem (4px) | Inputs, botones, celdas, filas de tabla |
| `md` | 0.375rem | — |
| `lg` | 0.5rem | Tarjetas y módulos de datos |
| `xl` | 0.75rem | — |
| `full` | 9999px | Solo avatares (circulares) |

Importante: **los badges/chips de estado usan `sm` (4px), no pill (`full`)** —
alinean mejor contra las líneas de grid vertical en tablas de datos. Difiere
del otro proyecto Stitch descartado, que sí usaba pills; mantener el radio
recto en `students`/`documents` es intencional según este sistema.

## Sombras / elevación

Jerarquía por bordes de 1px + sombra ambiental mínima, sin blur pesado:

| Nivel | Uso | Estilo |
|---|---|---|
| Tier 0 | Canvas base | Plano, `#FAF8FF`, sin elevación |
| Tier 1 | Tarjetas, grids, unidades de calendario | Borde `1px solid #E2E8F0` (equiv.) + `0 1px 2px 0 rgba(15,23,42,.04)` |
| Tier 2 | Dropdowns, popovers, date pickers | Borde `1px solid #CBD5E1` + `0 4px 6px -1px rgba(15,23,42,.07), 0 2px 4px -2px rgba(15,23,42,.05)` |
| Tier 3 | Modales y overlays | Backdrop `#0F172A` al 45% + `0 10px 15px -3px rgba(15,23,42,.12), 0 4px 6px -4px rgba(15,23,42,.08)` |

## Componentes recurrentes

**Botones** — altura 32px (compacto) / 36px (default), radio 4px.
- Primario: fondo `#0F291E`, texto blanco, borde a juego. Hover `#1B4332`.
  Foco: anillo 2px offset en `#65A30D`.
- Secundario/outline: fondo blanco, texto `#1E293B`, borde `#CBD5E1`.
- Terciario/ghost: transparente, texto `#475569`.
- Destructivo: fondo `#FEF2F2`, texto `#B91C1C`, borde `#F87171`.

**Inputs** — altura 34px, borde `#CBD5E1`, texto 13px. Foco: borde `#0F291E`
+ anillo `2px solid rgba(15,41,30,.15)`. Checkboxes/radios: 16×16px, radio 3px,
relleno `#0F291E` cuando están activos.

**Tablas de datos / listados** (relevante para `students`, `documents`) —
fila de 36px (32px en modo denso), cabecera `#F8FAFC` con texto 11px
mayúsculas en `#475569`, borde inferior `1px solid #CBD5E1`. Hover de fila
`#F1F5F9`. Fila seleccionada `#ECFDF5` con borde izquierdo 2px en `#15803D`.

**Badges de estado** — altura 20px, 11px, peso 600, mayúsculas, radio 4px
(no pill). Confirmado/en sesión: fondo `#DCFCE7`, texto `#166534`. Bloqueado:
fondo `#FEE2E2`, texto `#991B1B`. Sin asignar: fondo `#F1F5F9`, texto `#475569`.

## Fuera de alcance de esta extracción

El proyecto de Stitch incluye un "Schedule & Court Allocation Matrix"
(calendario de pistas) que no aplica: el calendario está fuera de alcance en
fase 1 según `CLAUDE.md`. Se documenta el token de color aquí por completitud
del sistema visual, pero no debe implementarse ningún componente de esa
sección.
