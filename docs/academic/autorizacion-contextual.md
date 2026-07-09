# Autorización contextual en un entorno académico

## Resumen

Socratic Tutor utiliza un modelo de autorización contextual para representar adecuadamente la complejidad institucional de una plataforma educativa. En este dominio, la identidad de un usuario no determina por sí sola lo que puede hacer. El mismo usuario puede actuar como administrador de una institución, profesor de una clase, asistente académico o estudiante, dependiendo del contexto seleccionado.

Por esta razón, el sistema separa autenticación, contexto activo, permisos efectivos y reglas de dominio. La autenticación responde quién es el usuario; el contexto activo determina dónde actúa; la instantánea de acceso indica qué permisos posee en ese contexto; y las reglas de dominio delimitan qué registros concretos puede consultar o modificar.

## Problema de autorización

En aplicaciones simples, suele ser suficiente asignar roles globales como “administrador” o “usuario”. Sin embargo, esta estrategia resulta insuficiente en una plataforma académica multiinstitucional. Una persona puede tener autoridad administrativa en una institución, ser profesor en una clase específica y estudiante en otra. Cada una de estas posiciones implica permisos distintos y alcances diferentes.

El problema puede formularse así:

> ¿Cómo representar permisos académicos cuando la autoridad de un usuario depende del contexto institucional y no únicamente de su identidad global?

La respuesta adoptada consiste en construir permisos efectivos a partir de un contexto activo. Dicho contexto puede corresponder al nivel de plataforma, institución o clase.

## Separación conceptual

El modelo distingue cuatro conceptos principales.

| Concepto | Pregunta que responde | Ejemplo |
|---|---|---|
| Autenticación | ¿Quién inició sesión? | Una cuenta de usuario válida |
| Contexto activo | ¿Dónde está actuando? | Plataforma, institución o clase |
| Permisos efectivos | ¿Qué puede hacer allí? | Crear actividades, ver conversaciones, asignar roles |
| Reglas de dominio | ¿Sobre qué registros concretos puede actuar? | Solo conversaciones de su clase o institución |

Esta separación evita confundir identidad con autoridad. Un usuario no es simplemente “profesor” o “administrador” en abstracto; lo es en relación con un espacio académico determinado.

## Arquitectura del contexto activo

Al iniciar sesión, el sistema descubre los contextos disponibles para la cuenta. Si existe un único contexto, puede seleccionarlo automáticamente. Si existen varios, el usuario debe escoger bajo cuál actuará. Esta selección se conserva como preferencia y se utiliza para construir la autorización efectiva.

**Figura sugerida 1. Ciclo de selección de contexto.**

```text
Usuario autenticado
        │
        ▼
Descubrimiento de contextos disponibles
        │
        ├── ningún contexto ─────► sin acceso
        ├── un contexto ─────────► selección automática
        └── varios contextos ────► selección por el usuario
                                 │
                                 ▼
                         contexto activo
```

El contexto activo se convierte en una condición previa para la navegación, la autorización de servicios y la visibilidad de opciones en la interfaz.

## Instantánea de acceso

Una vez determinado el contexto activo, el sistema construye una instantánea de acceso. Esta instantánea contiene los identificadores relevantes del usuario en ese contexto, los roles aplicables y los códigos de permiso resultantes.

El uso de una instantánea tiene dos funciones. Primero, evita recalcular permisos en cada operación. Segundo, ofrece una representación explícita y auditable del estado de autorización bajo el cual se ejecuta una acción.

**Figura sugerida 2. Construcción de permisos efectivos.**

```text
Cuenta autenticada + contexto activo
        │
        ▼
Asignaciones de rol aplicables
        │
        ▼
Permisos efectivos
        │
        ▼
Servicios y vistas protegidas
```

## Identidad académica y permisos

El sistema distingue entre identidad académica y permisos RBAC. La identidad de aula —por ejemplo, profesor, estudiante o asistente— se deriva de la membresía en una clase. Los roles, en cambio, conceden permisos. Esta distinción es relevante porque asignar un rol con ciertos permisos no debería transformar a un estudiante en profesor ni alterar la naturaleza de su participación académica.

En términos conceptuales, la membresía responde “qué lugar ocupa el usuario en la clase”, mientras que el rol responde “qué acciones adicionales puede ejecutar”.

## Comparación de alternativas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Roles globales | Simplicidad de implementación y comprensión inicial | No representa usuarios con funciones distintas en contextos distintos |
| Autoridades de Spring Security solamente | Integración directa con el marco de seguridad | Dificultad para expresar permisos dependientes de institución o clase |
| Reglas dispersas en servicios | Gran flexibilidad local | Baja auditabilidad y alto riesgo de inconsistencias |
| RBAC contextual con instantáneas | Representa fielmente el dominio académico y centraliza permisos efectivos | Mayor complejidad en descubrimiento, caché e invalidación |

Socratic Tutor adopta el RBAC contextual porque la complejidad del dominio no es accidental, sino inherente a la estructura institucional que debe modelar.

## Ventajas del enfoque

El modelo contextual ofrece las siguientes ventajas:

- permite que una misma cuenta tenga funciones distintas en espacios distintos;
- reduce el riesgo de conceder permisos fuera del contexto apropiado;
- facilita la construcción de menús y rutas según permisos efectivos;
- permite cachear decisiones de autorización sin perder sensibilidad contextual;
- separa permisos técnicos de pertenencia académica.

## Costos y limitaciones

El enfoque requiere mayor disciplina arquitectónica. Cada vista protegida debe declarar permisos; cada servicio sensible debe aplicar reglas de dominio; y cada cambio en roles o asignaciones debe invalidar las instantáneas correspondientes. Además, el sistema debe evitar que la visibilidad de un menú se confunda con seguridad real: ocultar un enlace no equivale a proteger una operación.

Otra limitación es la complejidad cognitiva para el usuario. Cuando una persona tiene varios contextos disponibles, la interfaz debe comunicar con claridad desde cuál está actuando para evitar errores de interpretación.

## Criterios de evaluación

| Criterio | Pregunta evaluativa |
|---|---|
| Corrección contextual | ¿Los permisos cambian adecuadamente al cambiar de contexto? |
| Principio de mínimo privilegio | ¿El usuario recibe solo los permisos necesarios en cada espacio? |
| Separación de identidad y permiso | ¿La membresía académica permanece independiente de los roles asignados? |
| Auditabilidad | ¿Puede reconstruirse por qué un usuario tenía cierto permiso? |
| Seguridad de navegación | ¿Las rutas protegidas exigen permisos explícitos y no dependen solo del menú? |

## Síntesis

La autorización contextual permite modelar con mayor precisión las relaciones institucionales de una plataforma educativa. Al separar autenticación, contexto activo, permisos efectivos y reglas de dominio, Socratic Tutor evita una simplificación excesiva del concepto de rol. Esta decisión introduce complejidad, pero resulta necesaria para representar de forma segura y coherente la realidad académica del sistema.
