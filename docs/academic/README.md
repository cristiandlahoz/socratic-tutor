# Documentación académica para la tesis

Esta carpeta contiene una versión discursiva, académica y redactada en español de las notas técnicas ubicadas en `docs/`. Su propósito no es sustituir la documentación de ingeniería, sino servir como base textual para una posterior composición en LaTeX.

Los documentos de esta carpeta privilegian:

- exposición conceptual antes que detalle operativo;
- justificación de diseño antes que enumeración de clases;
- tablas comparativas de ventajas, desventajas y alternativas;
- referencias explícitas a figuras y tablas;
- lenguaje formal, claro y defendible en un trabajo de grado.

## Relación con las notas técnicas

| Documento académico | Fuente técnica principal | Función dentro de la tesis |
|---|---|---|
| `arquitectura-general.md` | `docs/*.md` | Presentar la visión global del sistema. |
| `memoria-conversacional.md` | `docs/compaction.md`, `docs/session-history-filters.md` | Explicar la persistencia, compactación y proyección del historial conversacional. |
| `guardia-tutorial.md` | `docs/tutor-guard.md` | Describir el mecanismo de protección pedagógica y de integridad contextual. |
| `autorizacion-contextual.md` | `docs/rbac.md` | Explicar el modelo de autorización basado en contexto académico. |

## Convenciones para LaTeX

Las figuras se indican como marcadores textuales. En una fase posterior deben reemplazarse por diagramas en PDF, SVG convertido a PDF, TikZ o el formato gráfico que exija la plantilla institucional.

Ejemplo:

```markdown
**Figura sugerida 1.** Arquitectura general del sistema.
```

Las tablas están redactadas en Markdown para facilitar su conversión mediante Pandoc o su traslado manual a entornos `table` y `tabular` de LaTeX.

## Tono recomendado

La redacción evita el estilo de anuncio, bitácora o documentación interna. El objetivo es que cada sección pueda integrarse en una tesis con ajustes mínimos de numeración, citas y referencias cruzadas.
