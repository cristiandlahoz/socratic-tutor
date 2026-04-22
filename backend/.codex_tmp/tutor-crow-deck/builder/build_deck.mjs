const fs = await import("node:fs/promises");
const path = await import("node:path");
const { Presentation, PresentationFile } = await import("@oai/artifact-tool");

const W = 1280;
const H = 720;

const OUT_DIR = path.resolve("/Users/cigdelahoz/ghq/github.com/cristiandlahoz/this_studio/backend/.codex_tmp/tutor-crow-deck/out");

const PAPER = "#F6F0E6";
const PAPER_SOFT = "#FBF8F1";
const INK = "#182028";
const MUTED = "#5E6670";
const LINE = "#CFC3B0";
const ACCENT = "#2E5B7A";
const ACCENT_SOFT = "#DCE8F0";
const ACCENT_DARK = "#1E455E";
const SAND = "#E7D5BF";
const WHITE = "#FFFFFF";
const TRANSPARENT = "#00000000";

const TITLE_FACE = "Georgia";
const BODY_FACE = "Aptos";
const MONO_FACE = "Aptos Mono";

const FOOTER = "PUCMM · Proyecto de grado en Ciencias de la Computación";

const SLIDES = [
  {
    section: "Apertura",
    title: "Tutor Crow",
    subtitle:
      "un chatbot tutor para aprender programación mediante preguntas guiadas, retroalimentación consciente de errores y diálogo reflexivo.",
    tagline: "la tesis no es “usar IA”, sino diseñar una tutoría que obligue a pensar.",
  },
  {
    section: "Problema",
    title: "el problema no es responder, es aprender a razonar",
    subtitle:
      "icc-101 exige modelos mentales que todavía se están formando. una ia sin restricciones resuelve demasiado pronto.",
    cards: [
      [
        "Alta exigencia cognitiva",
        "La programación introductoria exige abstracción, razonamiento paso a paso y construcción de modelos mentales todavía frágiles.",
      ],
      [
        "IA irrestricta",
        "Las herramientas generales tienden a entregar respuestas completas, reduciendo el esfuerzo cognitivo que produce aprendizaje real.",
      ],
      [
        "Necesidad pedagógica",
        "El estudiante necesita acompañamiento para detectar errores conceptuales y avanzar de forma gradual, no solo una solución correcta.",
      ],
    ],
  },
  {
    section: "Principios",
    title: "tres principios sostienen el diseño del tutor",
    subtitle: "la plataforma traduce teoría pedagógica en reglas de interacción concretas.",
    cards: [
      [
        "Andamiaje cognitivo",
        "La ayuda debe operar dentro de la zona de comprensión del estudiante y preservar el trabajo mental que genera aprendizaje.",
      ],
      [
        "Método socrático",
        "La respuesta avanza con preguntas, pistas y reformulaciones para que el estudiante construya la solución en vez de recibirla terminada.",
      ],
      [
        "Misconceptions",
        "Los fallos no son azar: revelan modelos mentales incorrectos que deben hacerse visibles y corregirse con intención.",
      ],
    ],
  },
  {
    section: "Objetivos",
    title: "objetivos del proyecto",
    subtitle: "cada objetivo cubre una capa distinta: contenido, comportamiento tutor y evidencia.",
    metrics: [
      ["01", "Base de conocimiento", "Recopilar contenidos, ejercicios y errores frecuentes de ICC-101 para sostener la tutoría."],
      ["02", "Tutoría socrática", "Implementar un tutor basado en LLM que guíe sin entregar soluciones completas."],
      ["03", "Evaluación", "Valorar la calidad pedagógica y la utilidad del sistema con revisión experta y evidencia cualitativa."],
    ],
  },
  {
    section: "Arquitectura",
    title: "de filosofía pedagógica a pipeline de respuesta",
    subtitle: "la arquitectura no busca maximizar fluidez; busca controlar cómo y cuándo ayudar.",
    diagram: "architecture",
  },
  {
    section: "Caso de uso",
    title: "ciclo tutorial para ICC-101",
    subtitle: "ejemplo: el estudiante confunde un invariante de ciclo con el resultado final del algoritmo.",
    diagram: "cycle",
  },
  {
    section: "Demo",
    title: "qué debería pasar durante una buena interacción",
    subtitle: "la demo debe mostrar corrección guiada, no espectacularidad técnica.",
    cards: [
      [
        "Entrada del estudiante",
        "“El invariante del ciclo es el valor final que queda al terminar.” El sistema detecta una confusión conceptual, no solo una respuesta incompleta.",
      ],
      [
        "Respuesta del tutor",
        "Primero pide distinguir entre propiedad que se mantiene y estado final; luego ofrece una pista ligada al algoritmo que el estudiante está resolviendo.",
      ],
      [
        "Resultado esperado",
        "El estudiante reformula su idea, corrige el modelo mental y llega a la respuesta con apoyo gradual en vez de copia inmediata.",
      ],
    ],
  },
  {
    section: "Evaluación",
    title: "la evaluación debe medir calidad pedagógica",
    subtitle: "si solo medimos fluidez del texto, perdemos el punto del proyecto.",
    cards: [
      [
        "Revisión experta",
        "Docentes evalúan si las respuestas respetan principios instruccionales y corrigen errores sin reemplazar el razonamiento.",
      ],
      [
        "Evidencia cualitativa",
        "Se observa si el estudiante explica mejor su proceso, identifica sus fallos y mantiene participación activa durante la interacción.",
      ],
      [
        "Criterio central",
        "Éxito significa orientar pensamiento y comprensión, no producir la respuesta más rápida ni más larga.",
      ],
    ],
  },
  {
    section: "Hallazgos",
    title: "hallazgo principal: estructura mejor aprendizaje que una respuesta directa",
    subtitle: "el valor esperado del sistema está en la forma de la interacción.",
    metrics: [
      ["1", "Enfoque", "La contribución fuerte es guiar razonamiento, no competir como resolvedor universal de ejercicios."],
      ["2", "Valor pedagógico", "La tutoría hace visibles misconceptions y obliga al estudiante a verbalizar ideas intermedias."],
      ["3", "Viabilidad", "La combinación de reglas pedagógicas, contexto y LLM hace defendible el uso de IA en apoyo académico."],
    ],
  },
  {
    section: "Limitaciones",
    title: "límites claros del sistema",
    subtitle: "poner límites explícitos le da credibilidad al proyecto.",
    cards: [
      [
        "Alcance de dominio",
        "El caso de uso se limita a Introducción a la Algoritmia (ICC-101-T) y a los fundamentos que ese contexto exige.",
      ],
      [
        "Cobertura técnica",
        "El soporte se centra en lenguaje C y no pretende generalizar todavía a otros lenguajes o cursos avanzados.",
      ],
      [
        "Dependencias y rol",
        "El rendimiento depende de la infraestructura disponible y el sistema no sustituye al docente ni funciona como evaluador autónomo.",
      ],
    ],
  },
  {
    section: "Contribución",
    title: "qué aporta Tutor Crow",
    subtitle: "la tesis propone una forma más responsable de insertar IA en educación de programación.",
    cards: [
      [
        "Aporte conceptual",
        "Replantea el LLM como compañero de aprendizaje que guía pensamiento en lugar de atajar el esfuerzo cognitivo.",
      ],
      [
        "Aporte de diseño",
        "Conecta principios pedagógicos con decisiones de arquitectura y de comportamiento conversacional observables.",
      ],
      [
        "Aporte institucional",
        "Abre una ruta para apoyar cursos introductorios con IA sin romper la lógica formativa del aula universitaria.",
      ],
    ],
  },
  {
    section: "Siguiente paso",
    title: "después de la defensa",
    subtitle: "la ruta razonable es ampliar evidencia antes de ampliar ambición.",
    cards: [
      [
        "Validar más",
        "Ampliar evaluación con más docentes, más estudiantes y rúbricas comparables entre respuestas guiadas y respuestas directas.",
      ],
      [
        "Extender alcance",
        "Sumar más temas de ICC-101 y luego considerar otros cursos o lenguajes solo cuando el comportamiento pedagógico sea estable.",
      ],
      [
        "Refinar memoria",
        "Profundizar el seguimiento de errores frecuentes y progreso del estudiante para personalizar mejor la tutoría futura.",
      ],
    ],
    closing: "un tutor debe guiar pensamiento, no solo entregar respuestas.",
  },
];

await fs.mkdir(OUT_DIR, { recursive: true });

function line(fill = TRANSPARENT, width = 0) {
  return { style: "solid", fill, width };
}

function addShape(slide, geometry, left, top, width, height, fill = TRANSPARENT, stroke = TRANSPARENT, strokeWidth = 0) {
  return slide.shapes.add({
    geometry,
    position: { left, top, width, height },
    fill,
    line: line(stroke, strokeWidth),
  });
}

function addText(
  slide,
  text,
  left,
  top,
  width,
  height,
  {
    size = 20,
    face = BODY_FACE,
    color = INK,
    bold = false,
    align = "left",
    valign = "top",
    fill = TRANSPARENT,
    stroke = TRANSPARENT,
    strokeWidth = 0,
    italic = false,
  } = {},
) {
  const box = addShape(slide, "rect", left, top, width, height, fill, stroke, strokeWidth);
  box.text = text;
  box.text.fontSize = size;
  box.text.typeface = face;
  box.text.color = color;
  box.text.bold = bold;
  box.text.italic = italic;
  box.text.alignment = align;
  box.text.verticalAlignment = valign;
  box.text.insets = { left: 0, right: 0, top: 0, bottom: 0 };
  return box;
}

function setBackground(slide) {
  slide.background.fill = PAPER;
  addShape(slide, "rect", 0, 0, W, 116, PAPER_SOFT);
  addShape(slide, "rect", 0, 0, 18, H, ACCENT);
}

function addHeader(slide, idx, section) {
  addText(slide, section.toUpperCase(), 68, 30, 320, 24, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, `${String(idx).padStart(2, "0")} / ${String(SLIDES.length).padStart(2, "0")}`, 1090, 30, 120, 24, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
    align: "right",
  });
  addShape(slide, "rect", 68, 64, 1144, 1.5, LINE);
  addShape(slide, "ellipse", 60, 56, 16, 16, ACCENT, ACCENT, 1);
}

function addFooter(slide) {
  addText(slide, FOOTER, 68, 684, 460, 18, {
    size: 11,
    face: BODY_FACE,
    color: MUTED,
  });
}

function addTitleBlock(slide, title, subtitle) {
  addText(slide, title, 68, 92, 860, 94, {
    size: 34,
    face: TITLE_FACE,
    color: INK,
    bold: true,
  });
  addText(slide, subtitle, 70, 188, 760, 54, {
    size: 18,
    face: BODY_FACE,
    color: MUTED,
  });
}

function addCard(slide, x, y, w, h, title, body, accent = ACCENT) {
  addShape(slide, "roundRect", x, y, w, h, WHITE, LINE, 1.2);
  addShape(slide, "rect", x, y, 8, h, accent);
  addText(slide, title, x + 24, y + 26, w - 48, 26, {
    size: 15,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, body, x + 24, y + 68, w - 48, h - 92, {
    size: 17,
    face: BODY_FACE,
    color: INK,
  });
}

function addMetricCard(slide, x, y, w, h, number, label, body, accent) {
  addShape(slide, "roundRect", x, y, w, h, WHITE, LINE, 1.2);
  addShape(slide, "rect", x, y, w, 7, accent);
  addText(slide, number, x + 22, y + 24, w - 40, 46, {
    size: 36,
    face: TITLE_FACE,
    color: INK,
    bold: true,
  });
  addText(slide, label, x + 24, y + 76, w - 48, 24, {
    size: 15,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, body, x + 24, y + 112, w - 48, h - 132, {
    size: 17,
    face: BODY_FACE,
    color: INK,
  });
}

function addCardsSlide(slide, cards) {
  const startX = 86;
  const y = 328;
  const gap = 24;
  const w = (1108 - gap * 2) / 3;
  const h = 254;
  const accents = [ACCENT, "#A56A2A", "#6E7D45"];
  cards.forEach(([title, body], i) => addCard(slide, startX + i * (w + gap), y, w, h, title, body, accents[i % accents.length]));
}

function addMetricsSlide(slide, metrics) {
  const startX = 92;
  const y = 316;
  const gap = 24;
  const w = (1096 - gap * 2) / 3;
  const h = 286;
  const accents = [ACCENT, "#A56A2A", "#6E7D45"];
  metrics.forEach(([n, label, body], i) => addMetricCard(slide, startX + i * (w + gap), y, w, h, n, label, body, accents[i % accents.length]));
}

function addArrow(slide, fromX, fromY, toX, toY) {
  const left = Math.min(fromX, toX);
  const top = Math.min(fromY, toY) - 8;
  const width = Math.max(Math.abs(toX - fromX), 24);
  addShape(slide, "rightArrow", left, top, width, 16, ACCENT, ACCENT, 1);
}

function addArchitectureDiagram(slide) {
  const steps = [
    ["Estudiante", "Pregunta, duda o respuesta parcial."],
    ["Interfaz", "Captura contexto, intención y turno."],
    ["Capa tutorial", "Aplica reglas socráticas, memoria y control pedagógico."],
    ["LLM", "Genera la intervención guiada según restricciones."],
    ["Respuesta", "Pregunta, pista o retroalimentación gradual."],
  ];
  const y = 322;
  const boxW = 196;
  const boxH = 144;
  const gap = 26;
  steps.forEach(([title, body], i) => {
    const x = 72 + i * (boxW + gap);
    addShape(slide, "roundRect", x, y, boxW, boxH, WHITE, LINE, 1.2);
    addText(slide, title, x + 18, y + 18, boxW - 36, 28, {
      size: 18,
      face: TITLE_FACE,
      color: INK,
      bold: true,
      align: "center",
    });
    addText(slide, body, x + 18, y + 58, boxW - 36, 56, {
      size: 15,
      face: BODY_FACE,
      color: INK,
      align: "center",
    });
    if (i < steps.length - 1) {
      addArrow(slide, x + boxW, y + 72, x + boxW + gap - 8, y + 72);
    }
  });
  addShape(slide, "roundRect", 390, 520, 500, 96, ACCENT_SOFT, LINE, 1.2);
  addText(slide, "idea central", 416, 540, 150, 20, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, "la capa tutorial decide cómo ayudar antes de dejar hablar al modelo.", 416, 566, 438, 26, {
    size: 20,
    face: BODY_FACE,
    color: INK,
    bold: true,
  });
}

function addCycleDiagram(slide) {
  const labels = [
    ["01", "Detectar", "Identificar si hay confusión, respuesta parcial o misconception."],
    ["02", "Diagnosticar", "Ubicar el error conceptual y el nivel de ayuda necesario."],
    ["03", "Guiar", "Responder con pregunta, pista o reformulación."],
    ["04", "Evaluar", "Comprobar si el estudiante corrigió la idea."],
  ];
  const xPositions = [92, 384, 676, 968];
  labels.forEach(([n, title, body], i) => {
    const x = xPositions[i];
    addShape(slide, "ellipse", x + 74, 284, 78, 78, ACCENT_SOFT, ACCENT, 1.5);
    addText(slide, n, x + 96, 310, 34, 22, {
      size: 18,
      face: MONO_FACE,
      color: ACCENT_DARK,
      bold: true,
      align: "center",
    });
    addShape(slide, "roundRect", x, 382, 226, 170, WHITE, LINE, 1.2);
    addText(slide, title, x + 18, 404, 190, 28, {
      size: 22,
      face: TITLE_FACE,
      color: INK,
      bold: true,
      align: "center",
    });
    addText(slide, body, x + 18, 448, 190, 74, {
      size: 15,
      face: BODY_FACE,
      color: INK,
      align: "center",
    });
    if (i < labels.length - 1) {
      addArrow(slide, x + 226, 467, xPositions[i + 1] - 18, 467);
    }
  });
  addShape(slide, "roundRect", 396, 584, 488, 70, PAPER_SOFT, LINE, 1.2);
  addText(slide, "el ciclo se repite hasta que la respuesta del estudiante muestre comprensión, no solo coincidencia textual.", 424, 606, 432, 26, {
    size: 17,
    face: BODY_FACE,
    color: MUTED,
    align: "center",
    italic: true,
  });
}

function buildCover(slide, data, idx) {
  slide.background.fill = PAPER;
  addShape(slide, "rect", 0, 0, W, H, PAPER);
  addShape(slide, "rect", 0, 0, 22, H, ACCENT);
  addShape(slide, "rect", 74, 108, 8, 444, ACCENT);
  addText(slide, "tesis de grado · tutoría con IA", 100, 108, 320, 22, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, data.title, 100, 152, 760, 86, {
    size: 50,
    face: TITLE_FACE,
    color: INK,
    bold: true,
  });
  addText(slide, data.subtitle, 104, 264, 650, 94, {
    size: 22,
    face: BODY_FACE,
    color: INK,
  });
  addShape(slide, "roundRect", 100, 410, 474, 96, WHITE, LINE, 1.2);
  addText(slide, "tesis central", 126, 432, 120, 20, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  addText(slide, data.tagline, 126, 456, 420, 34, {
    size: 20,
    face: BODY_FACE,
    color: INK,
    bold: true,
  });
  addShape(slide, "roundRect", 824, 108, 364, 432, ACCENT_SOFT, LINE, 1.2);
  addText(slide, "resumen de la defensa", 854, 140, 210, 22, {
    size: 12,
    face: MONO_FACE,
    color: ACCENT_DARK,
    bold: true,
  });
  const agenda = [
    "problema educativo",
    "principios pedagógicos",
    "arquitectura y caso de uso",
    "evaluación, límites y aporte",
  ].join("\n");
  addText(slide, agenda, 854, 188, 270, 180, {
    size: 22,
    face: TITLE_FACE,
    color: INK,
    bold: true,
  });
  addShape(slide, "rect", 854, 402, 250, 1.5, LINE);
  addText(slide, FOOTER, 854, 430, 250, 34, {
    size: 14,
    face: BODY_FACE,
    color: MUTED,
  });
  addText(slide, `${String(idx).padStart(2, "0")}`, 1116, 620, 70, 42, {
    size: 28,
    face: TITLE_FACE,
    color: ACCENT_DARK,
    bold: true,
    align: "right",
  });
}

function buildStandardSlide(slide, data, idx) {
  setBackground(slide);
  addHeader(slide, idx, data.section);
  addTitleBlock(slide, data.title, data.subtitle);
  if (data.cards) {
    addCardsSlide(slide, data.cards);
  } else if (data.metrics) {
    addMetricsSlide(slide, data.metrics);
  } else if (data.diagram === "architecture") {
    addArchitectureDiagram(slide);
  } else if (data.diagram === "cycle") {
    addCycleDiagram(slide);
  }
  addFooter(slide);
}

function buildClosingAccent(slide, data) {
  addShape(slide, "roundRect", 744, 546, 424, 98, SAND, LINE, 1.2);
  addText(slide, data.closing, 772, 576, 370, 30, {
    size: 20,
    face: BODY_FACE,
    color: INK,
    bold: true,
    align: "center",
  });
}

try {
  const presentation = Presentation.create({ slideSize: { width: W, height: H } });

  SLIDES.forEach((slideData, index) => {
    const slide = presentation.slides.add();
    if (index === 0) {
      buildCover(slide, slideData, index + 1);
    } else {
      buildStandardSlide(slide, slideData, index + 1);
      if (slideData.closing) {
        buildClosingAccent(slide, slideData);
      }
    }
  });

  const pptx = await PresentationFile.exportPptx(presentation);
  const outputPath = path.join(OUT_DIR, "Tutor-Crow-kami-redesign.pptx");
  await pptx.save(outputPath);
  console.log(outputPath);
} catch (error) {
  console.error("deck build failed");
  console.error(error?.message || error);
  console.error(error?.stack || "no stack");
  process.exit(1);
}
