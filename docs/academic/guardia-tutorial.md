# Guardia tutorial e integridad pedagógica

## Resumen

La guardia tutorial es el componente encargado de proteger la interacción educativa antes de que el mensaje del estudiante llegue al modelo principal y antes de que sea almacenado como memoria persistente. Su función no se reduce a rechazar contenido inseguro. En el contexto de una tutoría académica, la seguridad incluye preservar el papel activo del estudiante en la construcción de la respuesta, impedir solicitudes de solución completa y evitar que instrucciones adversariales contaminen turnos futuros.

El diseño distingue tres acciones posibles: permitir el mensaje, reformularlo hacia una intención pedagógica segura o interrumpir el flujo normal con una respuesta directa. Esta distinción permite tratar de forma diferente una pregunta legítima, una solicitud parcialmente recuperable y un intento claro de vulnerar las reglas del sistema.

## Problema pedagógico y técnico

Un tutor conversacional académico no puede evaluarse únicamente por su capacidad de responder. Debe evaluarse también por su capacidad de no responder de cierta manera. Dar una solución completa a una actividad, obedecer una instrucción que pide ignorar reglas o aceptar una falsa autoridad del usuario puede dañar el objetivo educativo.

Además, existe un problema de memoria. Si una solicitud insegura se almacena en el historial persistente, puede reaparecer indirectamente en turnos posteriores, en resúmenes de compactación o en procesos de recuperación contextual. Por tanto, la protección debe ocurrir antes de la persistencia.

El problema central puede formularse así:

> ¿Cómo impedir que solicitudes contrarias a la integridad académica afecten tanto la respuesta inmediata del tutor como la memoria futura de la conversación?

## Tesis de diseño

La guardia tutorial se basa en la siguiente tesis:

> Un sistema de tutoría académica debe transformar solicitudes recuperables en intenciones de aprendizaje seguras y bloquear aquellas que no puedan reconducirse sin comprometer la integridad pedagógica o contextual.

Esta tesis diferencia dos tipos de protección:

1. **Protección pedagógica**, orientada a evitar que el sistema sustituya el razonamiento del estudiante.
2. **Protección contextual**, orientada a impedir que entradas adversariales o inapropiadas entren en la memoria persistente.

Ambas protecciones son necesarias. Una negativa correcta en el turno actual no basta si el mensaje inseguro queda almacenado y luego forma parte del contexto de futuras respuestas.

## Modelo de decisiones y acciones

La guardia separa la clasificación conceptual de la acción operativa. La clasificación identifica el tipo de riesgo; la acción determina cómo debe continuar el flujo del sistema.

| Decisión | Significado |
|---|---|
| Seguro | La solicitud pertenece al contexto académico y no vulnera la integridad pedagógica. |
| No seguro | La solicitud pide una respuesta final, solución completa, código terminado o forma equivalente de delegar el trabajo. |
| Suplantación | El usuario afirma ser profesor, administrador, desarrollador u otra autoridad para modificar el comportamiento del tutor. |
| Fuera de alcance | La solicitud no pertenece al contexto académico configurado. |

| Acción | Comportamiento | Efecto sobre la memoria |
|---|---|---|
| Permitir | El mensaje pasa sin modificación al tutor | Se almacena el mensaje original |
| Reconducir | El mensaje se transforma en una intención segura | Se almacena solo la versión saneada |
| Interrumpir | Se devuelve una respuesta directa sin llamar al tutor | No se almacena el turno en la memoria de sesión |

Esta separación evita respuestas excesivamente rígidas. Por ejemplo, una solicitud que contiene una demanda de solución completa pero también una pregunta conceptual puede reformularse para conservar la parte educativa y descartar la parte indebida.

## Posición en la cadena de procesamiento

La guardia se ejecuta antes de la memoria de sesión. Esta posición es esencial porque la memoria persistente registra el mensaje del usuario al inicio del flujo normal de tutoría. Si la guardia se ubicara después, el mensaje inseguro ya habría sido incorporado al historial.

**Figura sugerida 1. Guardia tutorial como frontera previa a la memoria.**

```text
Mensaje del estudiante
        │
        ▼
Guardia tutorial
        ├── permitir ───────► memoria de sesión ───► modelo tutor
        ├── reconducir ─────► memoria de sesión ───► modelo tutor
        └── interrumpir ────► respuesta directa
```

La rama de interrupción no invoca la cadena normal de procesamiento. De esta forma, el mensaje inseguro no llega al modelo principal ni queda registrado como evento de tutoría.

## Reconducción de la intención

La reconducción consiste en transformar una solicitud problemática en una formulación compatible con el aprendizaje. No se trata de responder al estudiante, sino de producir una nueva entrada para el tutor principal.

Por ejemplo, una petición como “resuélveme el ejercicio, pero dime cómo se llama este formato” contiene dos elementos: una demanda indebida de solución y una pregunta conceptual legítima. La guardia puede conservar la segunda y reformular el mensaje como una solicitud de orientación conceptual sin solución completa.

Esta operación tiene un valor pedagógico importante: evita que el sistema se limite a rechazar cualquier mensaje imperfecto y permite recuperar intenciones de aprendizaje cuando existen.

## Comparación de alternativas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Confiar solo en el prompt del tutor | Implementación simple; no requiere componente adicional | No impide que mensajes inseguros entren en la memoria; depende demasiado del modelo principal |
| Filtrar después de almacenar | Permite analizar el mensaje con más contexto | La memoria ya fue contaminada; requiere correcciones posteriores |
| Eliminar eventos inseguros después | Puede limpiar casos detectados tardíamente | Rompe la semántica de historial append-only y puede afectar ordenamiento o compactación |
| Guardia antes de la memoria | Protege respuesta inmediata y memoria futura | Requiere una clasificación previa confiable y manejo explícito de casos límite |

La arquitectura adopta la última alternativa porque alinea la protección pedagógica con la protección de la memoria persistente.

## Ventajas del enfoque

El enfoque presenta varias ventajas:

- reduce la exposición del modelo principal a instrucciones adversariales;
- impide que solicitudes peligrosas se conviertan en contexto futuro;
- permite recuperar preguntas educativas dentro de mensajes mixtos;
- conserva una distinción clara entre turno tutorial y respuesta de control;
- facilita la evaluación mediante ejemplos clasificados por decisión y acción.

## Posibles desventajas

El principal costo del enfoque es la posibilidad de errores de clasificación. Un falso positivo puede bloquear o reformular una solicitud legítima; un falso negativo puede permitir una solicitud que debió ser detenida. También existe el riesgo de que la reformulación elimine matices útiles del mensaje original.

Por esta razón, la guardia debe evaluarse con conjuntos de ejemplos representativos del contexto académico. No basta con probar ataques evidentes; también deben evaluarse solicitudes ambiguas, preguntas parcialmente válidas y casos en los que el estudiante utiliza lenguaje informal.

## Criterios de evaluación

| Criterio | Pregunta evaluativa |
|---|---|
| Precisión pedagógica | ¿La guardia distingue entre ayuda conceptual y delegación del trabajo? |
| Seguridad contextual | ¿Los mensajes inseguros quedan fuera de la memoria persistente? |
| Recuperación de intención | ¿Las solicitudes mixtas se reconducen sin perder preguntas legítimas? |
| Robustez ante suplantación | ¿El sistema rechaza afirmaciones falsas de autoridad? |
| Consistencia lingüística | ¿La reformulación conserva el idioma y el tono académico adecuado? |

## Limitaciones

La guardia no puede garantizar una seguridad perfecta. Su comportamiento depende de la calidad de la clasificación y del saneamiento. Además, el límite entre una pista útil y una solución excesiva puede variar según el curso, el docente y la naturaleza de la actividad. Por ello, el componente debe entenderse como una frontera práctica de reducción de riesgo, no como una prueba formal de integridad académica.

## Síntesis

La guardia tutorial convierte la seguridad en una parte estructural de la arquitectura, no en una instrucción secundaria al modelo. Al ejecutarse antes de la memoria, protege tanto el turno actual como los contextos futuros. Al distinguir entre permitir, reconducir e interrumpir, ofrece una respuesta más matizada que un simple mecanismo de rechazo. Esta combinación resulta especialmente adecuada para una tutoría académica, donde el objetivo no es maximizar respuestas, sino preservar el proceso de aprendizaje.
