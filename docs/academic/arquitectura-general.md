# Arquitectura general del sistema Socratic Tutor

## Resumen

Socratic Tutor es una plataforma de tutoría académica asistida por modelos de lenguaje. Su objetivo no se limita a ofrecer respuestas conversacionales, sino a sostener un proceso de aprendizaje guiado, contextualizado y seguro. Para ello, el sistema combina una interfaz educativa, un modelo de memoria conversacional persistente, mecanismos de compactación del historial, una capa de protección pedagógica y un esquema de autorización sensible al contexto institucional.

La arquitectura se organiza alrededor de una idea central: un tutor artificial debe preservar la continuidad de la conversación sin renunciar a la integridad académica ni a las restricciones propias de una institución educativa. En consecuencia, el sistema no trata cada mensaje como una interacción aislada, sino como parte de una trayectoria de aprendizaje que debe ser recordada, filtrada, resumida y protegida.

## Planteamiento del problema

Los sistemas conversacionales aplicados a la educación enfrentan una tensión particular. Por un lado, deben responder de forma suficientemente útil para acompañar al estudiante en la resolución de problemas. Por otro lado, no deben convertirse en un mecanismo de sustitución del esfuerzo cognitivo del alumno. Esta tensión se intensifica cuando la conversación se prolonga durante muchas interacciones, pues el sistema necesita conservar información sobre objetivos, errores previos, explicaciones ya utilizadas y estado conceptual del estudiante.

A esta dificultad pedagógica se suman restricciones técnicas. Los modelos de lenguaje poseen una ventana de contexto finita; por tanto, no es posible enviar indefinidamente todo el historial de una conversación. Además, en una aplicación académica real, distintos usuarios actúan bajo roles y contextos diversos: un mismo usuario puede ser administrador de una institución, profesor de una clase y estudiante en otra. La autorización no puede depender únicamente de roles globales.

Socratic Tutor aborda estas restricciones mediante cuatro subsistemas principales:

1. **Memoria conversacional persistente**, encargada de registrar los eventos de una sesión de tutoría.
2. **Compactación del historial**, orientada a mantener continuidad pedagógica dentro de límites de contexto.
3. **Guardia tutorial**, responsable de impedir que solicitudes inseguras contaminen el modelo o la memoria.
4. **Autorización contextual**, destinada a resolver permisos en función del espacio académico activo.

## Vista conceptual de la arquitectura

La aplicación puede entenderse como una cadena de mediaciones entre el usuario y el modelo de lenguaje. Antes de que un mensaje llegue al modelo, el sistema determina el contexto académico del usuario, verifica sus permisos, evalúa la seguridad pedagógica de la solicitud y construye una memoria conversacional adecuada.

**Figura sugerida 1. Arquitectura conceptual de Socratic Tutor.**

```text
Usuario académico
        │
        ▼
Interfaz de aplicación
        │
        ├── Autorización contextual
        │       ├── contexto activo
        │       └── permisos efectivos
        │
        ├── Guardrails
        │       ├── clasificación de intención
        │       ├── saneamiento pedagógico
        │       └── respuesta de bloqueo
        │
        ├── Memoria conversacional
        │       ├── registro persistente de eventos
        │       ├── filtro de contexto activo
        │       └── compactación por resumen
        │
        ▼
Modelo de lenguaje
```

Esta vista muestra que el modelo de lenguaje no opera como único responsable del comportamiento educativo. Por el contrario, está integrado en una arquitectura que delimita qué mensajes puede recibir, qué memoria se le presenta y bajo qué contexto académico se interpreta la interacción.

## Principios de diseño

La arquitectura se apoya en cinco principios.

### Continuidad pedagógica

La conversación de tutoría debe conservar suficiente información para que el sistema no trate al estudiante como un usuario nuevo en cada turno. Esto incluye el tema trabajado, las dificultades observadas, las pistas ya ofrecidas y el siguiente paso razonable dentro de una estrategia socrática.

### Integridad académica

El sistema debe evitar entregar soluciones completas cuando ello sustituye la actividad cognitiva del estudiante. La ayuda debe orientarse a formular preguntas, ofrecer pistas, aclarar conceptos y promover razonamiento propio.

### Integridad contextual

No todo mensaje del usuario debe entrar en la memoria persistente. Solicitudes adversariales, intentos de suplantación de autoridad o demandas explícitas de respuestas finales pueden afectar turnos futuros si son almacenadas y reutilizadas como contexto.

### Separación de proyecciones

El historial físico de eventos no coincide necesariamente con lo que ve cada consumidor del sistema. El modelo necesita una proyección compactada y activa; la interfaz del estudiante requiere una transcripción real de la conversación. La arquitectura separa almacenamiento y consumo mediante filtros.

### Autorización situada

El significado de una acción depende del contexto académico. Ver una actividad, administrar roles o acceder a una conversación no puede resolverse únicamente a partir de la identidad global del usuario; debe considerarse la institución, la clase y el rol efectivo en ese espacio.

## Componentes principales

| Componente | Responsabilidad principal | Razón arquitectónica |
|---|---|---|
| Interfaz de usuario | Presentar vistas de chat, administración y gestión académica | Permite adaptar la experiencia según el contexto activo del usuario. |
| Autorización contextual | Determinar permisos efectivos por contexto | Evita confundir identidad global con autoridad local. |
| Guardia tutorial | Clasificar, sanear o bloquear entradas inseguras | Protege la integridad pedagógica y la memoria futura. |
| Memoria de sesión | Persistir eventos conversacionales | Mantiene continuidad entre turnos y sesiones. |
| Compactación | Resumir historia antigua y conservar turnos recientes | Controla el uso de la ventana de contexto del modelo. |
| Modelo de lenguaje | Generar respuestas tutoriales | Produce la interacción natural, pero bajo restricciones del sistema. |

## Decisiones de diseño y compromisos

La siguiente tabla resume algunas decisiones relevantes de la arquitectura.

| Decisión | Ventajas | Desventajas |
|---|---|---|
| Usar una memoria persistente de sesión | Permite continuidad conversacional y análisis posterior | Exige mecanismos de filtrado, compactación y protección contra entradas inseguras |
| Ejecutar la guardia antes de la memoria | Evita que mensajes peligrosos se almacenen como contexto futuro | Requiere un orden cuidadoso en la cadena de procesamiento |
| Separar historial visible y contexto del modelo | Preserva una transcripción real para el estudiante y una memoria útil para el modelo | Introduce mayor complejidad semántica en los filtros |
| Resolver permisos por contexto activo | Representa con mayor fidelidad la realidad institucional | Es más complejo que un esquema de roles globales |
| Compactar mediante resumen sintético | Reduce el tamaño del contexto sin perder por completo el estado pedagógico | Puede omitir matices de la conversación original |

## Aporte arquitectónico

El aporte principal de esta arquitectura consiste en tratar la tutoría conversacional como un sistema de estado, no como un simple intercambio de mensajes. Esta distinción es fundamental: una respuesta aislada puede ser correcta lingüísticamente, pero inadecuada pedagógicamente si ignora el historial del estudiante, repite una explicación ya fallida o responde a una solicitud que vulnera la integridad académica.

Socratic Tutor propone, por tanto, una organización en la que la generación lingüística queda subordinada a un conjunto de restricciones educativas, contextuales y de seguridad. Esta organización permite que el sistema sea útil sin perder control sobre el proceso de aprendizaje que pretende acompañar.

## Limitaciones generales

La arquitectura no elimina por completo los riesgos asociados a los modelos de lenguaje. La compactación puede perder información; la clasificación de la guardia puede incurrir en falsos positivos o falsos negativos; y el modelo de autorización contextual requiere disciplina en la implementación de cada nuevo servicio o vista. Sin embargo, estas limitaciones se vuelven explícitas y tratables al estar representadas como componentes separados del sistema.

## Síntesis

Socratic Tutor se diseña como una plataforma de tutoría académica en la que memoria, seguridad pedagógica y autorización institucional son responsabilidades de primer orden. Esta separación permite construir un sistema más robusto que un chatbot genérico, ya que cada interacción se interpreta dentro de una conversación persistente, un contexto académico y un conjunto de límites pedagógicos.
