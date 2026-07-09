# Memoria conversacional y compactación del historial

## Resumen

La memoria conversacional de Socratic Tutor permite que una sesión de tutoría mantenga continuidad a lo largo del tiempo. En lugar de tratar cada mensaje como una consulta independiente, el sistema conserva un registro persistente de eventos y construye, para cada nueva interacción, una representación activa del historial. Esta representación combina turnos recientes en forma literal con un resumen sintético de la conversación anterior.

El mecanismo responde a una restricción técnica y pedagógica. Técnicamente, la ventana de contexto del modelo de lenguaje es limitada. Pedagógicamente, una tutoría prolongada requiere recordar objetivos, errores conceptuales, pistas ya ofrecidas y explicaciones previas. La compactación busca equilibrar ambas necesidades: reducir el volumen de texto enviado al modelo sin borrar la trayectoria de aprendizaje del estudiante.

## Problema de investigación aplicada

Una conversación educativa extensa puede superar rápidamente la capacidad contextual del modelo. Si se envía todo el historial, el sistema puede exceder el límite de tokens, aumentar costos y degradar el rendimiento. Si se conserva únicamente una ventana reciente, se pierden elementos relevantes para la continuidad pedagógica.

El problema puede formularse así:

> ¿Cómo mantener una memoria útil de una tutoría prolongada cuando el modelo de lenguaje no puede recibir indefinidamente todo el historial conversacional?

La solución adoptada consiste en almacenar todo el historial en la base de datos, pero presentar al modelo solo una proyección activa: un resumen sintético de los eventos antiguos y un conjunto de turnos recientes preservados literalmente.

## Objetivos de diseño

El diseño de la memoria conversacional persigue los siguientes objetivos:

1. **Conservar la transcripción real** de la conversación para el estudiante y para usos administrativos o analíticos.
2. **Reducir el contexto activo** enviado al modelo cuando la conversación crece demasiado.
3. **Preservar continuidad pedagógica**, especialmente errores, avances, objetivos y estrategias ya utilizadas.
4. **Evitar cortes arbitrarios** que separen una respuesta de la pregunta que la originó.
5. **Permitir compactaciones sucesivas** sin perder el resumen acumulado de sesiones anteriores.

## Modelo de almacenamiento

Cada conversación se almacena como un registro de eventos. Estos eventos pueden corresponder a mensajes reales del usuario, respuestas reales del asistente, llamadas a herramientas o mensajes sintéticos producidos por la compactación.

La distinción fundamental no está en el almacenamiento, sino en la proyección. El mismo registro persistente puede alimentar consumidores diferentes: el modelo de lenguaje, la interfaz del estudiante o componentes de diagnóstico.

**Figura sugerida 1. Registro persistente y proyecciones derivadas.**

```text
Registro persistente de sesión
        │
        ├── Proyección para el modelo
        │       └── resumen sintético + turnos recientes activos
        │
        └── Proyección para la interfaz
                └── transcripción real visible para el usuario
```

Esta separación permite que el modelo reciba una memoria condensada, mientras que el estudiante conserva una experiencia coherente y transparente de la conversación real.

## Compactación por resumen recursivo

Cuando el contexto activo supera un umbral configurado, el sistema inicia un proceso de compactación. Los eventos antiguos se resumen mediante una llamada al modelo, mientras que los eventos recientes se conservan de forma literal. El resultado se almacena como un turno sintético, compuesto por un mensaje de usuario y una respuesta de asistente generados por el sistema.

La compactación no elimina los eventos antiguos. Estos quedan archivados: permanecen disponibles en el almacenamiento, pero dejan de formar parte del contexto activo enviado al modelo.

**Figura sugerida 2. Estado antes y después de la compactación.**

```text
Antes de compactar:

evento 0   evento 1   evento 2   evento 3   evento 4   evento 5
  real       real       real       real       real       real

Después de compactar:

eventos antiguos archivados   resumen sintético   turnos recientes reales
```

La estrategia preserva dos tipos de información: la literalidad de los intercambios más recientes y la memoria conceptual de los intercambios anteriores.

## Proyección del modelo y proyección de la interfaz

La memoria conversacional distingue entre lo que el modelo necesita para responder y lo que el usuario debe ver como transcripción. Esta distinción evita dos errores comunes: mostrar al estudiante mensajes artificiales de resumen o, inversamente, obligar al modelo a procesar todo el historial visible.

| Consumidor | Proyección utilizada | Propósito |
|---|---|---|
| Modelo de lenguaje | Eventos activos: resumen sintético y turnos recientes | Mantener continuidad dentro del límite de contexto |
| Interfaz de chat | Mensajes reales del usuario y del asistente | Mostrar la conversación auténtica al estudiante |
| Diagnóstico técnico | Todos los eventos, incluidos archivados y sintéticos | Inspeccionar el estado completo de la sesión |

Esta organización convierte el historial en una fuente única de verdad con varias lecturas posibles, cada una adecuada a un propósito distinto.

## Comparación de estrategias alternativas

| Estrategia | Ventajas | Desventajas |
|---|---|---|
| Enviar siempre el historial completo | Conserva máxima fidelidad textual | No escala ante conversaciones largas; puede superar el límite de contexto |
| Usar solo una ventana deslizante reciente | Es simple y de bajo costo | Pierde información pedagógica anterior relevante |
| Resumir toda la conversación | Reduce drásticamente el contexto | Puede perder detalles recientes necesarios para responder con precisión |
| Combinar resumen y turnos recientes | Equilibra continuidad y literalidad | Requiere lógica adicional de compactación y filtrado |
| Recuperar eventos por búsqueda semántica | Permite traer información antigua específica | Añade complejidad de índices, embeddings y relevancia |

La solución adoptada corresponde a la cuarta estrategia. Su ventaja principal es que reconoce la importancia diferencial de los eventos: los turnos recientes suelen requerir conservación literal, mientras que los antiguos pueden representarse mediante una síntesis pedagógica.

## Ventajas del enfoque adoptado

El enfoque ofrece varias ventajas para un sistema de tutoría:

- mantiene una continuidad razonable en conversaciones prolongadas;
- evita que el modelo reciba un historial excesivamente largo;
- conserva la transcripción real para el estudiante;
- permite compactaciones sucesivas;
- reduce la probabilidad de que el tutor repita explicaciones ya utilizadas;
- facilita que el resumen incluya el estado conceptual del estudiante, no solo una cronología de mensajes.

## Riesgos y limitaciones

La compactación introduce una pérdida controlada de información. Un resumen puede omitir matices, ejemplos específicos o señales afectivas presentes en la conversación original. Además, la calidad de la memoria compactada depende de la calidad del modelo resumidor y de las instrucciones utilizadas para generar el resumen.

Otro riesgo consiste en que un resumen defectuoso se propague a compactaciones posteriores. Por ello, el sistema conserva eventos recientes en forma literal y utiliza el resumen anterior como entrada cuando se produce una nueva compactación. Aun así, la memoria sintética debe interpretarse como una representación útil, no como una reproducción perfecta del historial.

## Criterios de evaluación

Para evaluar este componente pueden considerarse los siguientes criterios:

| Criterio | Pregunta evaluativa |
|---|---|
| Continuidad pedagógica | ¿El tutor recuerda objetivos, errores y explicaciones anteriores después de compactar? |
| Fidelidad de resumen | ¿El resumen conserva la información necesaria para continuar la tutoría? |
| Control de contexto | ¿La compactación mantiene el tamaño del prompt bajo el umbral esperado? |
| Coherencia de interfaz | ¿El estudiante ve una transcripción real, sin mensajes sintéticos? |
| Robustez ante repetición | ¿Las compactaciones sucesivas preservan el estado acumulado de la conversación? |

## Síntesis

La memoria conversacional de Socratic Tutor se basa en una separación entre almacenamiento persistente y contexto activo. Esta separación permite preservar la historia completa de la tutoría sin obligar al modelo a procesarla íntegramente en cada turno. La compactación mediante resumen sintético y conservación de turnos recientes constituye una solución intermedia entre fidelidad, costo y continuidad pedagógica.
