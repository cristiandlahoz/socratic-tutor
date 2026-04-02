const fs = require("fs");
const path = require("path");
const PptxGenJS = require("pptxgenjs");

const pptx = new PptxGenJS();

const ROOT = path.resolve(__dirname, "..");
const outputDir = path.join(ROOT, "reports", "presentations");
const outputFile = path.join(outputDir, "tutor-crow-thesis-defense.pptx");
const logoPath = path.join(ROOT, "assets", "ppt", "pucmm-logo.png");

const COLORS = {
  yellow: "FFC000",
  black: "0E0E0E",
  white: "FFFFFF",
  ivory: "F7F4EE",
  paper: "FCFBF8",
  stone: "E7E2D9",
  text: "1E1E1E",
  muted: "6C6961",
  warmGray: "D7D1C5",
  blue: "1F497D",
  navy: "243142",
  steel: "53667F",
  slate: "37465C",
  sky: "DCE7F7",
  success: "2E7D32",
  red: "B33A3A",
  darkPanel: "1B2029",
  darkPanelElevated: "232A36",
  darkPanelSoft: "2B3445",
  darkText: "E8EDF7",
  darkMuted: "98A7C0",
  darkBorder: "3D4B61",
};

const FONTS = {
  title: "Aptos Display",
  body: "Aptos",
  mono: "Consolas",
};

const SLIDE = {
  w: 10,
  h: 7.5,
  marginX: 0.68,
  contentW: 8.64,
  topBarH: 0.38,
  footerH: 0.88,
};

pptx.defineLayout({ name: "PUCMM_4_3", width: SLIDE.w, height: SLIDE.h });
pptx.layout = "PUCMM_4_3";
pptx.author = "OpenAI Codex";
pptx.company = "Pontificia Universidad Catolica Madre y Maestra";
pptx.subject = "Defensa de tesis de ciencias de la computacion";
pptx.title = "Tutor Crow: Tutor Socratico Inteligente";
pptx.lang = "es-DO";
pptx.theme = {
  headFontFace: FONTS.title,
  bodyFontFace: FONTS.body,
  lang: "es-DO",
};

pptx.defineSlideMaster({
  title: "PUCMM_MASTER",
  background: { color: COLORS.paper },
  margin: 0,
  slideNumber: {
    x: 9.18,
    y: 6.92,
    w: 0.35,
    h: 0.18,
    fontFace: FONTS.body,
    fontSize: 8.5,
    color: COLORS.white,
    bold: true,
    align: "right",
  },
  objects: [
    {
      rect: {
        x: 0,
        y: 0,
        w: SLIDE.w,
        h: SLIDE.topBarH,
        line: { color: COLORS.yellow, transparency: 100 },
        fill: { color: COLORS.yellow },
      },
    },
    {
      rect: {
        x: 0,
        y: SLIDE.h - SLIDE.footerH,
        w: SLIDE.w,
        h: SLIDE.footerH,
        line: { color: COLORS.black, transparency: 100 },
        fill: { color: COLORS.black },
      },
    },
    {
      image: {
        path: logoPath,
        x: 0.18,
        y: 6.72,
        w: 1.64,
        h: 0.55,
      },
    },
    {
      text: {
        text: "Escuela de Ingenieria en Computacion y Telecomunicaciones",
        options: {
          x: 2.08,
          y: 6.93,
          w: 5.8,
          h: 0.14,
          fontFace: FONTS.body,
          fontSize: 7.5,
          color: "F7F7F7",
          opacity: 0.9,
        },
      },
    },
  ],
});

function addSectionLabel(slide, label) {
  slide.addText(label.toUpperCase(), {
    x: SLIDE.marginX,
    y: 0.58,
    w: 2.4,
    h: 0.18,
    fontFace: FONTS.body,
    fontSize: 10,
    bold: true,
    color: COLORS.blue,
    charSpace: 1.1,
  });
}

function addTitle(slide, title, subtitle) {
  slide.addText(title, {
    x: SLIDE.marginX,
    y: 0.82,
    w: 8.2,
    h: 0.52,
    fontFace: FONTS.title,
    fontSize: 24,
    bold: true,
    color: COLORS.text,
    breakLine: false,
    margin: 0,
  });

  if (subtitle) {
    slide.addText(subtitle, {
      x: SLIDE.marginX,
      y: 1.4,
      w: 8.1,
      h: 0.34,
      fontFace: FONTS.body,
      fontSize: 10.5,
      color: COLORS.muted,
      margin: 0,
      valign: "mid",
    });
  }
}

function addDivider(slide, y = 1.88) {
  slide.addShape(pptx.ShapeType.line, {
    x: SLIDE.marginX,
    y,
    w: 1.0,
    h: 0,
    line: { color: COLORS.yellow, pt: 2.2 },
  });
}

function addCard(slide, opts) {
  const {
    x,
    y,
    w,
    h,
    title,
    body,
    fill = COLORS.white,
    line = COLORS.stone,
    titleColor = COLORS.text,
    bodyColor = COLORS.muted,
    radius = 0.12,
    shadow = true,
  } = opts;

  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    rectRadius: radius,
    line: { color: line, pt: 1 },
    fill: { color: fill },
    shadow: shadow
      ? { type: "outer", color: "80776B", blur: 1, angle: 90, distance: 1, opacity: 0.12 }
      : undefined,
  });

  if (title) {
    slide.addText(title, {
      x: x + 0.18,
      y: y + 0.18,
      w: w - 0.36,
      h: 0.26,
      fontFace: FONTS.title,
      fontSize: 13,
      bold: true,
      color: titleColor,
      margin: 0,
    });
  }

  if (body) {
    slide.addText(body, {
      x: x + 0.18,
      y: y + 0.52,
      w: w - 0.36,
      h: h - 0.66,
      fontFace: FONTS.body,
      fontSize: 10,
      color: bodyColor,
      valign: "top",
      margin: 0,
      breakLine: false,
      fit: "shrink",
    });
  }
}

function addMetricCard(slide, opts) {
  const { x, y, w, h, value, label, footnote } = opts;
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    line: { color: COLORS.warmGray, pt: 1 },
    fill: { color: COLORS.white },
    shadow: { type: "outer", color: "A39685", blur: 1, angle: 90, distance: 1, opacity: 0.12 },
  });
  slide.addText(value, {
    x: x + 0.18,
    y: y + 0.15,
    w: w - 0.36,
    h: 0.38,
    fontFace: FONTS.title,
    fontSize: 24,
    bold: true,
    color: COLORS.text,
    margin: 0,
    align: "left",
  });
  slide.addText(label, {
    x: x + 0.18,
    y: y + 0.58,
    w: w - 0.36,
    h: 0.2,
    fontFace: FONTS.body,
    fontSize: 9,
    bold: true,
    color: COLORS.blue,
    margin: 0,
  });
  if (footnote) {
    slide.addText(footnote, {
      x: x + 0.18,
      y: y + 0.84,
      w: w - 0.36,
      h: 0.22,
      fontFace: FONTS.body,
      fontSize: 8.5,
      color: COLORS.muted,
      margin: 0,
      fit: "shrink",
    });
  }
}

function addChip(slide, text, x, y, w, fill, color = COLORS.text) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h: 0.26,
    line: { color: fill, transparency: 100 },
    fill: { color: fill },
  });
  slide.addText(text, {
    x: x + 0.08,
    y: y + 0.05,
    w: w - 0.16,
    h: 0.12,
    fontFace: FONTS.body,
    fontSize: 8.5,
    color,
    bold: true,
    margin: 0,
    align: "center",
  });
}

function addBulletList(slide, items, x, y, w, fontSize = 11, color = COLORS.text) {
  slide.addText(
    items.map((item) => ({ text: item, options: { bullet: { indent: 14 } } })),
    {
      x,
      y,
      w,
      h: Math.min(3.5, 0.42 * items.length + 0.12),
      fontFace: FONTS.body,
      fontSize,
      color,
      margin: 0,
      paraSpaceAfterPt: 7,
      breakLine: false,
      valign: "top",
      bullet: { indent: 14 },
    }
  );
}

function addArrow(slide, x, y, w) {
  slide.addShape(pptx.ShapeType.chevron, {
    x,
    y,
    w,
    h: 0.36,
    line: { color: COLORS.yellow, pt: 1 },
    fill: { color: COLORS.yellow, transparency: 18 },
  });
}

function addDarkPanel(slide, x, y, w, h) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    line: { color: COLORS.darkBorder, pt: 1 },
    fill: { color: COLORS.darkPanel },
    shadow: { type: "outer", color: "0A0E14", blur: 2, angle: 90, distance: 2, opacity: 0.28 },
  });
}

function addFooterNote(slide, text) {
  slide.addText(text, {
    x: SLIDE.marginX,
    y: 6.28,
    w: 7.9,
    h: 0.18,
    fontFace: FONTS.body,
    fontSize: 8,
    color: COLORS.muted,
    margin: 0,
  });
}

function makeSlide(section, title, subtitle) {
  const slide = pptx.addSlide("PUCMM_MASTER");
  slide.background = { color: COLORS.paper };
  addSectionLabel(slide, section);
  addTitle(slide, title, subtitle);
  addDivider(slide);
  return slide;
}

function addTimelineNode(slide, x, title, body, accent) {
  slide.addShape(pptx.ShapeType.ellipse, {
    x,
    y: 4.72,
    w: 0.18,
    h: 0.18,
    line: { color: accent, pt: 1 },
    fill: { color: accent },
  });
  addCard(slide, {
    x: x - 0.1,
    y: 4.98,
    w: 1.75,
    h: 0.98,
    title,
    body,
    fill: COLORS.white,
    line: COLORS.stone,
    shadow: false,
  });
}

slideCover();
slideAgenda();
slideProblem();
slideObjectives();
slideSolution();
slideArchitecture();
slideSafety();
slideCorpus();
slideShowcase();
slideTraceability();
slideImplementation();
slideResults();
slideConclusions();
slideQuestions();

ensureOutputDir();
writePresentation().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

function slideCover() {
  const slide = pptx.addSlide("PUCMM_MASTER");
  slide.background = { color: COLORS.paper };

  addChip(slide, "DEFENSA DE TESIS", 0.7, 0.72, 1.5, COLORS.blue, COLORS.white);

  slide.addText("Tutor Crow", {
    x: 0.72,
    y: 1.18,
    w: 3.6,
    h: 0.42,
    fontFace: FONTS.title,
    fontSize: 30,
    bold: true,
    color: COLORS.text,
    margin: 0,
  });
  slide.addText("Tutor socratico inteligente para Introduccion a la Algoritmia", {
    x: 0.72,
    y: 1.66,
    w: 4.95,
    h: 0.52,
    fontFace: FONTS.title,
    fontSize: 19,
    bold: true,
    color: COLORS.blue,
    margin: 0,
  });
  slide.addText(
    "Plataforma academica basada en LLM, RAG y guardrails pedagogicos para guiar al estudiante, preservar la voz docente y registrar evidencia reutilizable para seguimiento y evaluacion.",
    {
      x: 0.72,
      y: 2.36,
      w: 4.55,
      h: 0.72,
      fontFace: FONTS.body,
      fontSize: 11.2,
      color: COLORS.muted,
      margin: 0,
      fit: "shrink",
    }
  );

  addCard(slide, {
    x: 0.72,
    y: 4.12,
    w: 2.48,
    h: 0.96,
    title: "Autores",
    body: "Cristian Ignacio de la Hoz Reyes\nManuel Jose Rodriguez Cruz",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 3.36,
    y: 4.12,
    w: 1.78,
    h: 0.96,
    title: "Contexto",
    body: "ICC-101-T\nPUCMM\nMarzo 2026",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 5.56,
    y: 1.26,
    w: 3.12,
    h: 4.56,
    fill: COLORS.white,
    line: COLORS.stone,
    shadow: false,
  });

  slide.addText("Vista conceptual del sistema", {
    x: 5.84,
    y: 1.58,
    w: 2.56,
    h: 0.16,
    fontFace: FONTS.body,
    fontSize: 8.2,
    color: COLORS.muted,
    bold: true,
    margin: 0,
    align: "center",
  });
  slide.addShape(pptx.ShapeType.ellipse, {
    x: 6.62,
    y: 2.34,
    w: 1.12,
    h: 1.12,
    line: { color: COLORS.blue, pt: 1.5, transparency: 25 },
    fill: { color: COLORS.sky },
  });
  slide.addText("Tutor\nCrow", {
    x: 6.86,
    y: 2.7,
    w: 0.64,
    h: 0.28,
    fontFace: FONTS.title,
    fontSize: 15,
    bold: true,
    color: COLORS.blue,
    align: "center",
    margin: 0,
  });

  const orbitNodes = [
    ["LLM", 5.98, 2.02, 0.82],
    ["RAG", 7.86, 2.78, 0.84],
    ["Guardrails", 5.82, 4.0, 1.12],
    ["Trazabilidad", 7.5, 4.5, 1.14],
  ];
  orbitNodes.forEach(([label, x, y, w]) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w,
      h: 0.34,
      line: { color: COLORS.warmGray, pt: 1 },
      fill: { color: COLORS.ivory },
    });
    slide.addText(label, {
      x: x + 0.04,
      y: y + 0.1,
      w: w - 0.08,
      h: 0.12,
      fontFace: FONTS.body,
      fontSize: 8.4,
      color: COLORS.text,
      bold: true,
      align: "center",
      margin: 0,
    });
  });
  [
    [6.44, 2.34, 0.28, 0.18],
    [7.66, 3.06, 0.3, 0.04],
    [6.4, 3.46, 0.28, 0.56],
    [7.52, 3.36, 0.34, 0.96],
  ].forEach(([x, y, w, h]) => {
    slide.addShape(pptx.ShapeType.line, {
      x,
      y,
      w,
      h,
      line: { color: COLORS.steel, pt: 1.1, transparency: 20 },
    });
  });
  slide.addText("Tutoria socratica, recuperacion contextual y evidencia de sesion en una sola plataforma.", {
    x: 5.94,
    y: 5.14,
    w: 2.38,
    h: 0.42,
    fontFace: FONTS.body,
    fontSize: 8.8,
    color: COLORS.muted,
    align: "center",
    margin: 0,
  });
}

function slideAgenda() {
  const slide = makeSlide(
    "Guia",
    "Una narrativa breve, tecnica y facil de defender",
    "La presentacion se organiza para abrir con el problema, probar la propuesta y cerrar con evidencia concreta."
  );

  const items = [
    ["01", "Problema y oportunidad"],
    ["02", "Objetivos de la tesis"],
    ["03", "Propuesta Tutor Crow"],
    ["04", "Arquitectura y guardrails"],
    ["05", "Corpus, RAG y trazabilidad"],
    ["06", "Showcase e implementacion"],
    ["07", "Resultados y cierre"],
  ];

  items.forEach(([num, text], index) => {
    const y = 2.12 + index * 0.52;
    slide.addText(num, {
      x: 0.86,
      y,
      w: 0.42,
      h: 0.22,
      fontFace: FONTS.title,
      fontSize: 17,
      bold: true,
      color: index === 0 ? COLORS.white : COLORS.blue,
      align: "center",
      margin: 0,
    });
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 0.72,
      y: y - 0.08,
      w: 0.68,
      h: 0.38,
      line: { color: index === 0 ? COLORS.yellow : COLORS.warmGray, pt: 1 },
      fill: { color: index === 0 ? COLORS.yellow : COLORS.white },
    });
    slide.addText(text, {
      x: 1.62,
      y: y - 0.01,
      w: 3.5,
      h: 0.18,
      fontFace: FONTS.body,
      fontSize: 11.4,
      color: COLORS.text,
      bold: index === 0,
      margin: 0,
    });
  });

  addCard(slide, {
    x: 5.52,
    y: 2.12,
    w: 3.28,
    h: 1.56,
    title: "Hilo conductor",
    body:
      "La narrativa va de problema a propuesta, luego a arquitectura, evidencia y cierre. Cada bloque responde una pregunta natural del jurado.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 5.52,
    y: 3.92,
    w: 3.28,
    h: 1.4,
    title: "Criterio visual",
    body:
      "Titulos cortos, una sola figura principal por slide y apoyo textual compacto. La organizacion privilegia lectura rapida y defensa oral.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addChip(slide, "Problema", 5.72, 5.56, 0.86, COLORS.sky, COLORS.blue);
  addChip(slide, "Arquitectura", 6.68, 5.56, 1.06, COLORS.ivory, COLORS.text);
  addChip(slide, "Resultados", 7.84, 5.56, 0.92, COLORS.sky, COLORS.blue);
}

function slideProblem() {
  const slide = makeSlide(
    "Problema",
    "El reto no es solo tutorizar mejor, sino volver visible cada sesion",
    "Las tutorias informales ayudan en el momento, pero dejan poca evidencia reutilizable para seguimiento, evaluacion y mejora continua."
  );

  slide.addText("Sin trazabilidad, la ayuda academica termina cuando termina la conversacion.", {
    x: 0.72,
    y: 2.14,
    w: 4.48,
    h: 0.72,
    fontFace: FONTS.title,
    fontSize: 20,
    bold: true,
    color: COLORS.text,
    margin: 0,
  });

  addBulletList(
    slide,
    [
      "El estudiante recibe orientacion, pero el docente no siempre conserva el proceso que produjo la respuesta.",
      "Sin registro estructurado es dificil detectar patrones, dudas recurrentes o errores persistentes.",
      "La ausencia de evidencia limita la evaluacion del impacto pedagogico del sistema.",
    ],
    0.78,
    3.0,
    4.38,
    10.4
  );

  addCard(slide, {
    x: 5.62,
    y: 2.18,
    w: 3.18,
    h: 1.04,
    title: "Fragmentacion",
    body: "Dialogos utiles, pero dispersos y no comparables.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 5.62,
    y: 3.44,
    w: 3.18,
    h: 1.04,
    title: "Seguimiento debil",
    body: "El progreso entre sesiones no queda anclado a eventos verificables.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addCard(slide, {
    x: 5.62,
    y: 4.7,
    w: 3.18,
    h: 1.04,
    title: "Poca evidencia",
    body: "Sin datos ordenados, la mejora del tutor se vuelve reactiva y manual.",
    fill: COLORS.white,
    shadow: false,
  });

  addFooterNote(slide, "La oportunidad de tesis aparece al combinar ayuda pedagogica con evidencia estructurada de interaccion.");
}

function slideObjectives() {
  const slide = makeSlide(
    "Objetivos",
    "La tesis propone una plataforma que ensena, protege y deja huella analizable",
    "La formulacion combina una meta general clara con objetivos especificos que pueden defenderse como decisiones de arquitectura."
  );

  addCard(slide, {
    x: 0.72,
    y: 2.08,
    w: 8.48,
    h: 1.1,
    title: "Objetivo general",
    body:
      "Disenar e implementar Tutor Crow, una plataforma de tutoria socratica basada en LLM que apoye la ensenanza de Introduccion a la Algoritmia y capture evidencia estructurada de las interacciones para monitoreo, analitica y mejora continua.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });

  const specifics = [
    "Construir una experiencia conversacional capaz de guiar al estudiante paso a paso sin entregar respuestas finales.",
    "Integrar un subsistema RAG que reutilice corpus pedagogico real y ejemplos sinteticos de alta calidad.",
    "Aplicar guardrails de seguridad y alcance para conservar el rol socratico del tutor.",
    "Registrar eventos e indicadores que permitan trazabilidad, auditoria y evaluacion posterior.",
  ];

  specifics.forEach((text, index) => {
    const x = 0.72 + (index % 2) * 4.34;
    const y = 3.5 + Math.floor(index / 2) * 1.22;
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w: 4.0,
      h: 0.96,
      line: { color: COLORS.stone, pt: 1 },
      fill: { color: index % 2 === 0 ? COLORS.white : COLORS.paper },
    });
    slide.addText(String(index + 1).padStart(2, "0"), {
      x: x + 0.18,
      y: y + 0.15,
      w: 0.36,
      h: 0.2,
      fontFace: FONTS.title,
      fontSize: 16,
      bold: true,
      color: COLORS.yellow,
      margin: 0,
      align: "center",
    });
    slide.addText(text, {
      x: x + 0.7,
      y: y + 0.16,
      w: 3.08,
      h: 0.56,
      fontFace: FONTS.body,
      fontSize: 9.8,
      color: COLORS.text,
      margin: 0,
      fit: "shrink",
    });
  });
}

function slideSolution() {
  const slide = makeSlide(
    "Propuesta",
    "Tutor Crow unifica interaccion academica, recuperacion contextual y observabilidad",
    "La plataforma no se limita a responder: organiza una experiencia completa de tutor, conocimiento y evidencia."
  );

  slide.addText(
    "La propuesta integra tres capacidades que normalmente aparecen separadas en herramientas distintas: acompanamiento socratico, acceso contextual al conocimiento y trazabilidad de la sesion.",
    {
      x: 0.72,
      y: 2.12,
      w: 3.8,
      h: 0.64,
      fontFace: FONTS.body,
      fontSize: 11,
      color: COLORS.text,
      margin: 0,
      fit: "shrink",
    }
  );
  addBulletList(
    slide,
    [
      "Guia al estudiante con preguntas y retroalimentacion graduada.",
      "Recupera contexto desde corpus pedagogico real y sintetico.",
      "Convierte cada intercambio en una fuente de evidencia reutilizable.",
    ],
    0.78,
    2.98,
    3.86,
    10.2
  );

  slide.addShape(pptx.ShapeType.ellipse, {
    x: 6.56,
    y: 3.04,
    w: 1.18,
    h: 1.18,
    line: { color: COLORS.blue, pt: 1.6, transparency: 30 },
    fill: { color: COLORS.sky },
  });
  slide.addText("Tutor\nCrow", {
    x: 6.82,
    y: 3.42,
    w: 0.68,
    h: 0.22,
    fontFace: FONTS.title,
    fontSize: 14,
    bold: true,
    color: COLORS.blue,
    align: "center",
    margin: 0,
  });

  const nodes = [
    { label: "Interfaz conversacional", x: 5.2, y: 2.1, w: 1.42 },
    { label: "RAG academico", x: 7.54, y: 2.1, w: 1.18 },
    { label: "Guardrails", x: 5.22, y: 4.76, w: 1.2 },
    { label: "Trazabilidad", x: 7.42, y: 4.76, w: 1.32 },
  ];

  nodes.forEach((node) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x: node.x,
      y: node.y,
      w: node.w,
      h: 0.38,
      line: { color: COLORS.warmGray, pt: 1 },
      fill: { color: COLORS.white },
    });
    slide.addText(node.label, {
      x: node.x + 0.06,
      y: node.y + 0.11,
      w: node.w - 0.12,
      h: 0.12,
      fontFace: FONTS.body,
      fontSize: 8.2,
      bold: true,
      color: COLORS.text,
      align: "center",
      margin: 0,
    });
  });

  const connectors = [
    [6.12, 2.44, 0.46, 0.72],
    [7.76, 2.46, 0.38, 0.72],
    [6.16, 4.14, 0.44, 0.74],
    [7.74, 4.14, 0.36, 0.74],
  ];
  connectors.forEach(([x, y, w, h]) => {
    slide.addShape(pptx.ShapeType.line, {
      x,
      y,
      w,
      h,
      line: { color: COLORS.steel, pt: 1.1, transparency: 20 },
    });
  });

  addFooterNote(slide, "La propuesta se resume mejor como un nucleo unico con cuatro capacidades satelite claramente diferenciadas.");
}

function slideArchitecture() {
  const slide = makeSlide(
    "Arquitectura",
    "La arquitectura separa experiencia, inteligencia, control y conocimiento",
    "Esta separacion facilita evolucionar el sistema sin mezclar interfaz, tutor, recuperacion y politicas de seguridad."
  );

  const layers = [
    ["Capa de experiencia", "UI conversacional en Vaadin 25, estados de sesion y navegacion.", COLORS.white, COLORS.text],
    ["Capa de orquestacion", "Spring Boot 4 y Spring AI coordinan prompts, streaming y memoria conversacional.", COLORS.ivory, COLORS.text],
    ["Capa de tutor y guardrails", "TutorGuardAdvisor refuerza el modo socratico, maneja atajos, inyeccion y alcance.", COLORS.sky, COLORS.blue],
    ["Capa de conocimiento", "Corpus curado, chunking, vectorizacion y recuperacion con ChromaDB.", COLORS.ivory, COLORS.text],
    ["Capa de evidencia", "Eventos, logs y resultados de sesion alimentan monitoreo y analitica posterior.", COLORS.white, COLORS.text],
  ];

  layers.forEach(([title, body, fill, titleColor], index) => {
    const y = 2.16 + index * 0.72;
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 0.86,
      y,
      w: 8.26,
      h: 0.54,
      line: { color: COLORS.stone, pt: 1 },
      fill: { color: fill },
    });
    slide.addText(title, {
      x: 1.08,
      y: y + 0.09,
      w: 2.34,
      h: 0.18,
      fontFace: FONTS.title,
      fontSize: 11.8,
      bold: true,
      color: titleColor,
      margin: 0,
    });
    slide.addText(body, {
      x: 3.02,
      y: y + 0.09,
      w: 5.8,
      h: 0.22,
      fontFace: FONTS.body,
      fontSize: 9.3,
      color: COLORS.text,
      margin: 0,
      fit: "shrink",
    });
  });

  addChip(slide, "Vaadin", 1.02, 6.03, 0.72, COLORS.sky, COLORS.blue);
  addChip(slide, "Spring Boot 4", 1.84, 6.03, 1.12, COLORS.ivory, COLORS.text);
  addChip(slide, "Spring AI", 3.08, 6.03, 0.88, COLORS.sky, COLORS.blue);
  addChip(slide, "Ollama", 4.08, 6.03, 0.78, COLORS.ivory, COLORS.text);
  addChip(slide, "ChromaDB", 4.98, 6.03, 0.92, COLORS.sky, COLORS.blue);
}

function slideSafety() {
  const slide = makeSlide(
    "Guardrails",
    "El valor pedagogico depende de conservar el rol del tutor",
    "La tesis introduce una capa explicita de seguridad y alcance para evitar respuestas directas y mantener el estilo socratico."
  );

  addCard(slide, {
    x: 0.72,
    y: 2.12,
    w: 3.9,
    h: 1.3,
    title: "Modo socratico reforzado",
    body:
      "El sistema prioriza preguntas guiadas, explicaciones conceptuales, comprobaciones parciales y ejemplos del mundo real antes de mostrar codigo.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 0.72,
    y: 3.66,
    w: 3.9,
    h: 1.44,
    title: "Politicas activas",
    body:
      "Se detectan solicitudes de atajos, prompt injection, suplantacion de autoridad y temas fuera de alcance para redirigir la respuesta sin romper la ayuda.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });

  const states = [
    ["SAFE", "Consulta normal del estudiante.", COLORS.success],
    ["NOT_SAFE", "Pedido de respuesta final o codigo sin explicacion.", COLORS.red],
    ["IMPERSONATION", "La persona intenta cambiar reglas alegando autoridad.", COLORS.blue],
    ["OUT_OF_SCOPE", "La consulta sale del dominio de algoritmia en C.", COLORS.steel],
  ];
  states.forEach(([label, text, accent], index) => {
    const x = 5.02 + (index % 2) * 1.9;
    const y = 2.2 + Math.floor(index / 2) * 1.46;
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w: 1.66,
      h: 1.14,
      line: { color: accent, pt: 1.2 },
      fill: { color: index % 2 === 0 ? COLORS.white : COLORS.paper },
    });
    slide.addText(label, {
      x: x + 0.12,
      y: y + 0.16,
      w: 1.5,
      h: 0.2,
      fontFace: FONTS.title,
      fontSize: 11.2,
      bold: true,
      color: accent,
      align: "center",
      margin: 0,
      fit: "shrink",
    });
    slide.addText(text, {
      x: x + 0.12,
      y: y + 0.52,
      w: 1.5,
      h: 0.38,
      fontFace: FONTS.body,
      fontSize: 8.4,
      color: COLORS.text,
      align: "center",
      margin: 0,
      fit: "shrink",
    });
  });

  addFooterNote(slide, "El guard classifier trabaja con decisiones SAFE, NOT_SAFE, IMPERSONATION y OUT_OF_SCOPE definidas en el backend.");
}

function slideCorpus() {
  const slide = makeSlide(
    "Corpus y RAG",
    "El conocimiento del tutor nace de material real y se expande con sinteticos curados",
    "La base de conocimiento no es generica: se construye sobre dialogos socraticos de la asignatura y un pipeline de recuperacion semantica."
  );

  addMetricCard(slide, {
    x: 0.72,
    y: 2.12,
    w: 2.0,
    h: 1.16,
    value: "88",
    label: "Conversaciones",
    footnote: "3 reales + 85 sinteticas",
  });
  addMetricCard(slide, {
    x: 2.9,
    y: 2.12,
    w: 2.0,
    h: 1.16,
    value: "1,824",
    label: "Turnos",
    footnote: "Muestra de entrenamiento preservando voz docente",
  });
  addMetricCard(slide, {
    x: 5.08,
    y: 2.12,
    w: 2.0,
    h: 1.16,
    value: "2,725",
    label: "Fragmentos",
    footnote: "Corpus segmentado desde 4 fuentes heterogeneas",
  });
  addMetricCard(slide, {
    x: 7.26,
    y: 2.12,
    w: 1.94,
    h: 1.16,
    value: "2,603",
    label: "Vectores",
    footnote: "Recuperacion validada en ChromaDB",
  });

  const steps = [
    ["Fuentes reales", "Dialogos socraticos del profesor y material curricular."],
    ["Sintesis guiada", "Conversaciones sinteticas manteniendo metodologia y tono."],
    ["Chunking", "Segmentacion del corpus para recuperacion granular."],
    ["Vectorizacion", "Embeddings e ingesta en ChromaDB."],
  ];
  steps.forEach(([title, body], index) => {
    const x = 0.72 + index * 2.22;
    addCard(slide, {
      x,
      y: 3.76,
      w: 1.86,
      h: 1.3,
      title,
      body,
      fill: index % 2 === 0 ? COLORS.ivory : COLORS.white,
      line: index % 2 === 0 ? COLORS.yellow : COLORS.stone,
      shadow: false,
    });
    if (index < steps.length - 1) {
      addArrow(slide, x + 1.92, 4.24, 0.18);
    }
  });

  slide.addText("Similitud coseno validada entre 0.74 y 0.85 en recuperacion semantica.", {
    x: 0.72,
    y: 5.5,
    w: 8.0,
    h: 0.2,
    fontFace: FONTS.body,
    fontSize: 9.5,
    color: COLORS.blue,
    bold: true,
    margin: 0,
    align: "center",
  });
}

function slideShowcase() {
  const slide = makeSlide(
    "Showcase",
    "La interfaz conversacional combina tono academico, foco visual y baja friccion",
    "En lugar de una pantalla saturada, la experiencia prioriza el intercambio, el contexto y el impulso a hacer la primera pregunta."
  );

  addCard(slide, {
    x: 0.82,
    y: 2.08,
    w: 5.86,
    h: 3.92,
    fill: COLORS.white,
    line: COLORS.stone,
    shadow: false,
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x: 1.08,
    y: 2.36,
    w: 1.26,
    h: 3.28,
    line: { color: COLORS.stone, pt: 1 },
    fill: { color: COLORS.ivory },
  });
  slide.addText("Asistente academico", {
    x: 1.2,
    y: 2.62,
    w: 1.0,
    h: 0.18,
    fontFace: FONTS.body,
    fontSize: 8.5,
    color: COLORS.blue,
    bold: true,
    margin: 0,
  });
  slide.addText("Tutor\nCrow", {
    x: 1.2,
    y: 3.0,
    w: 0.9,
    h: 0.48,
    fontFace: FONTS.title,
    fontSize: 18,
    color: COLORS.text,
    bold: true,
    margin: 0,
  });
  slide.addText("Guia paso a paso, aclara conceptos y practica con ejemplos.", {
    x: 1.2,
    y: 3.72,
    w: 0.96,
    h: 0.64,
    fontFace: FONTS.body,
    fontSize: 8.6,
    color: COLORS.muted,
    margin: 0,
    fit: "shrink",
  });

  slide.addText("Haz tu primera pregunta", {
    x: 2.76,
    y: 2.58,
    w: 2.22,
    h: 0.28,
    fontFace: FONTS.title,
    fontSize: 18,
    bold: true,
    color: COLORS.darkText,
    margin: 0,
  });
  slide.addText("El estudiante entra en una pantalla sobria donde el tutor invita a razonar antes de escribir codigo.", {
    x: 2.76,
    y: 2.92,
    w: 2.9,
    h: 0.46,
    fontFace: FONTS.body,
    fontSize: 9.2,
    color: COLORS.muted,
    margin: 0,
  });

  const bubbles = [
    [2.78, 3.72, 2.66, 0.52, COLORS.sky, COLORS.text, "Profe, como planteo un while para sumar N ventas?"],
    [3.52, 4.38, 2.0, 0.62, COLORS.ivory, COLORS.text, "Antes de escribir codigo, pensemos el problema como un cajero."],
    [2.78, 5.2, 2.88, 0.32, COLORS.paper, COLORS.muted, "Escribe tu mensaje aqui..."],
  ];
  bubbles.forEach(([x, y, w, h, fill, color, text]) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w,
      h,
      line: { color: COLORS.warmGray, pt: 1 },
      fill: { color: fill },
    });
    slide.addText(text, {
      x: x + 0.12,
      y: y + 0.1,
      w: w - 0.24,
      h: h - 0.18,
      fontFace: FONTS.body,
      fontSize: 8.6,
      color,
      margin: 0,
      fit: "shrink",
    });
  });
  slide.addShape(pptx.ShapeType.ellipse, {
    x: 5.28,
    y: 5.24,
    w: 0.22,
    h: 0.22,
    line: { color: COLORS.blue, pt: 1 },
    fill: { color: COLORS.blue },
  });

  addCard(slide, {
    x: 7.0,
    y: 2.18,
    w: 2.1,
    h: 0.9,
    title: "01 Inicio guiado",
    body: "El empty state baja la ansiedad y orienta la primera accion.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 7.0,
    y: 3.28,
    w: 2.1,
    h: 0.9,
    title: "02 Streaming",
    body: "La respuesta aparece de forma progresiva y mantiene continuidad conversacional.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addCard(slide, {
    x: 7.0,
    y: 4.38,
    w: 2.1,
    h: 1.02,
    title: "03 Tono academico",
    body: "La interfaz comunica apoyo, no automatizacion fria ni solucionario.",
    fill: COLORS.white,
    shadow: false,
  });
}

function slideTraceability() {
  const slide = makeSlide(
    "Trazabilidad",
    "Cada intercambio puede convertirse en una unidad de evidencia revisable",
    "La tesis gana profundidad cuando la interaccion se traduce en eventos que luego pueden analizarse pedagogica y tecnicamente."
  );

  addCard(slide, {
    x: 0.86,
    y: 2.16,
    w: 4.24,
    h: 3.7,
    fill: COLORS.white,
    line: COLORS.stone,
    shadow: false,
  });
  slide.addText("Session timeline", {
    x: 1.12,
    y: 2.44,
    w: 1.6,
    h: 0.16,
    fontFace: FONTS.body,
    fontSize: 8.2,
    color: COLORS.muted,
    bold: true,
    margin: 0,
  });

  const events = [
    ["09:12", "student.prompt", "Pregunta sobre acumuladores con while", COLORS.sky],
    ["09:12", "guard.safe", "Consulta dentro de alcance", "D8F0DB"],
    ["09:13", "rag.retrieve", "Se recuperan fragmentos relevantes", "F2E1A7"],
    ["09:13", "assistant.stream", "Respuesta socratica en curso", "D6E4FA"],
    ["09:14", "session.outcome", "Se registra contexto y continuidad", "F5D7D7"],
  ];
  events.forEach(([time, name, text, fill], index) => {
    const y = 2.8 + index * 0.54;
    slide.addShape(pptx.ShapeType.line, {
      x: 1.34,
      y,
      w: 0,
      h: 0.42,
      line: { color: COLORS.warmGray, pt: 1.2 },
    });
    slide.addShape(pptx.ShapeType.ellipse, {
      x: 1.26,
      y: y + 0.12,
      w: 0.16,
      h: 0.16,
      line: { color: fill, pt: 1 },
      fill: { color: fill },
    });
    slide.addText(time, {
      x: 1.56,
      y: y + 0.08,
      w: 0.54,
      h: 0.12,
      fontFace: FONTS.mono,
      fontSize: 7.8,
      color: COLORS.muted,
      margin: 0,
    });
    slide.addText(name, {
      x: 2.16,
      y: y + 0.04,
      w: 1.1,
      h: 0.14,
      fontFace: FONTS.mono,
      fontSize: 8,
      color: COLORS.text,
      bold: true,
      margin: 0,
    });
    slide.addText(text, {
      x: 2.16,
      y: y + 0.22,
      w: 2.32,
      h: 0.12,
      fontFace: FONTS.body,
      fontSize: 7.8,
      color: COLORS.muted,
      margin: 0,
      fit: "shrink",
    });
  });

  addCard(slide, {
    x: 5.46,
    y: 2.2,
    w: 3.42,
    h: 0.96,
    title: "Seguimiento entre sesiones",
    body: "El docente puede observar continuidad, bloqueos recurrentes y progreso conceptual.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 5.46,
    y: 3.4,
    w: 3.42,
    h: 0.96,
    title: "Auditoria pedagogica",
    body: "Los guardrails dejan evidencia de como se sostuvo el rol socratico frente a solicitudes riesgosas.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addCard(slide, {
    x: 5.46,
    y: 4.6,
    w: 3.42,
    h: 0.96,
    title: "Analitica posterior",
    body: "Los logs permiten correlacionar tipo de duda, contexto recuperado y calidad de la respuesta.",
    fill: COLORS.white,
    shadow: false,
  });

  addFooterNote(slide, "La idea central para la defensa: cada sesion se convierte en evidencia de aprendizaje y de comportamiento del sistema.");
}

function slideImplementation() {
  const slide = makeSlide(
    "Implementacion",
    "El stack prioriza velocidad de iteracion, control local y extensibilidad",
    "La seleccion tecnologica refuerza la tesis: una interfaz robusta, un backend orquestador y un pipeline local de IA recuperable."
  );

  const stack = [
    ["Frontend", "Vaadin 25", "Interfaz conversacional, composicion del chat y experiencia de usuario."],
    ["Backend", "Spring Boot 4", "Orquestacion, servicios y configuracion de la aplicacion."],
    ["IA", "Spring AI + Ollama", "Streaming, prompting y control del modelo local."],
    ["Conocimiento", "ChromaDB", "Vector store para recuperar contexto pedagogico."],
    ["Gobernanza", "TutorGuardAdvisor", "Refuerzo del modo socratico y manejo de politicas."],
  ];

  stack.forEach(([label, tech, body], index) => {
    const x = 0.72 + index * 1.74;
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y: 2.28,
      w: 1.56,
      h: 3.08,
      line: { color: COLORS.stone, pt: 1 },
      fill: { color: index % 2 === 0 ? COLORS.white : COLORS.ivory },
    });
    slide.addText(label.toUpperCase(), {
      x: x + 0.12,
      y: 2.54,
      w: 1.32,
      h: 0.16,
      fontFace: FONTS.body,
      fontSize: 8.2,
      bold: true,
      color: COLORS.blue,
      align: "center",
      margin: 0,
    });
    slide.addText(tech, {
      x: x + 0.1,
      y: 2.96,
      w: 1.36,
      h: 0.42,
      fontFace: FONTS.title,
      fontSize: 14.5,
      bold: true,
      color: COLORS.text,
      align: "center",
      margin: 0,
      fit: "shrink",
    });
    slide.addText(body, {
      x: x + 0.12,
      y: 3.7,
      w: 1.32,
      h: 1.06,
      fontFace: FONTS.body,
      fontSize: 8.8,
      color: COLORS.muted,
      align: "center",
      margin: 0,
      fit: "shrink",
    });
  });

  addFooterNote(slide, "La implementacion muestra coherencia entre interfaz, politicas de tutor y recuperacion del conocimiento.");
}

function slideResults() {
  const slide = makeSlide(
    "Resultados",
    "El proyecto ya demuestra una base funcional y medible para seguir evaluando",
    "Los resultados reportados combinan volumen de corpus, validacion de recuperacion y progreso de implementacion."
  );

  addCard(slide, {
    x: 0.82,
    y: 2.1,
    w: 4.32,
    h: 3.08,
    title: "Activos del proyecto",
    body: "",
    fill: COLORS.white,
    line: COLORS.stone,
    shadow: false,
  });

  const bars = [
    ["Conversaciones", 88, 2725],
    ["Turnos", 1824, 2725],
    ["Fragmentos", 2725, 2725],
    ["Vectores", 2603, 2725],
  ];
  bars.forEach(([label, value, max], index) => {
    const y = 2.62 + index * 0.54;
    slide.addText(label, {
      x: 1.06,
      y,
      w: 1.1,
      h: 0.14,
      fontFace: FONTS.body,
      fontSize: 8.8,
      color: COLORS.text,
      bold: true,
      margin: 0,
    });
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 2.18,
      y: y + 0.02,
      w: 1.78,
      h: 0.12,
      line: { color: COLORS.stone, pt: 1 },
      fill: { color: COLORS.paper },
    });
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 2.18,
      y: y + 0.02,
      w: 1.78 * (value / max),
      h: 0.12,
      line: { color: COLORS.blue, pt: 1 },
      fill: { color: index % 2 === 0 ? COLORS.blue : COLORS.yellow },
    });
    slide.addText(String(value), {
      x: 4.08,
      y: y - 0.02,
      w: 0.52,
      h: 0.16,
      fontFace: FONTS.body,
      fontSize: 8.8,
      color: COLORS.muted,
      margin: 0,
      align: "right",
    });
  });

  addMetricCard(slide, {
    x: 5.44,
    y: 2.16,
    w: 1.64,
    h: 1.1,
    value: "65%",
    label: "Calendario",
    footnote: "Rendimiento reportado en el primer corte",
  });
  addMetricCard(slide, {
    x: 7.24,
    y: 2.16,
    w: 1.64,
    h: 1.1,
    value: "0.74-0.85",
    label: "Recuperacion",
    footnote: "Similitud coseno validada",
  });

  addCard(slide, {
    x: 5.44,
    y: 3.56,
    w: 3.44,
    h: 1.04,
    title: "Hallazgo principal",
    body: "La plataforma ya conecta corpus curado, experiencia conversacional y control pedagogico en una misma linea de trabajo.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addCard(slide, {
    x: 5.44,
    y: 4.82,
    w: 3.44,
    h: 0.94,
    title: "Lectura para el jurado",
    body: "No se presenta solo un chatbot: se presenta una arquitectura de apoyo academico con evidencia reutilizable.",
    fill: COLORS.white,
    shadow: false,
  });
}

function slideConclusions() {
  const slide = makeSlide(
    "Cierre",
    "Tutor Crow demuestra que tutoria, conocimiento y evidencia pueden diseniarse como un solo sistema",
    "La defensa debe cerrar respondiendo de forma directa al problema inicial y abriendo una ruta clara de evolucion."
  );

  slide.addText(
    "La contribucion central de la tesis es una plataforma que conserva la disciplina pedagogica del tutor socratico mientras estructura el conocimiento y la trazabilidad necesarios para estudiar, mejorar y escalar la experiencia.",
    {
      x: 0.72,
      y: 2.16,
      w: 4.72,
      h: 1.16,
      fontFace: FONTS.title,
      fontSize: 18,
      color: COLORS.text,
      bold: true,
      margin: 0,
      fit: "shrink",
    }
  );

  addCard(slide, {
    x: 5.72,
    y: 2.18,
    w: 3.1,
    h: 0.92,
    title: "Aporte 01",
    body: "Tutoria socratica asistida por LLM con control de comportamiento.",
    fill: COLORS.white,
    shadow: false,
  });
  addCard(slide, {
    x: 5.72,
    y: 3.28,
    w: 3.1,
    h: 0.92,
    title: "Aporte 02",
    body: "Corpus pedagogico recuperable desde fuentes reales y sinteticas.",
    fill: COLORS.ivory,
    line: COLORS.yellow,
    shadow: false,
  });
  addCard(slide, {
    x: 5.72,
    y: 4.38,
    w: 3.1,
    h: 0.92,
    title: "Aporte 03",
    body: "Base para analitica y seguimiento de sesiones con evidencia estructurada.",
    fill: COLORS.white,
    shadow: false,
  });

  addChip(slide, "Evaluacion con mas usuarios", 0.82, 5.18, 1.5, COLORS.sky, COLORS.blue);
  addChip(slide, "Dashboards docentes", 2.44, 5.18, 1.3, COLORS.ivory, COLORS.text);
  addChip(slide, "Integracion institucional", 3.88, 5.18, 1.58, COLORS.sky, COLORS.blue);
  addChip(slide, "Analitica predictiva", 5.62, 5.18, 1.36, COLORS.ivory, COLORS.text);
}

function slideQuestions() {
  const slide = pptx.addSlide("PUCMM_MASTER");
  slide.background = { color: COLORS.paper };
  addChip(slide, "CIERRE", 0.72, 0.78, 0.9, COLORS.yellow, COLORS.text);
  slide.addText("Gracias.", {
    x: 0.72,
    y: 1.48,
    w: 3.2,
    h: 0.42,
    fontFace: FONTS.title,
    fontSize: 30,
    bold: true,
    color: COLORS.text,
    margin: 0,
  });
  slide.addText("Preguntas", {
    x: 0.72,
    y: 1.98,
    w: 3.6,
    h: 0.42,
    fontFace: FONTS.title,
    fontSize: 27,
    color: COLORS.blue,
    bold: true,
    margin: 0,
  });
  slide.addText("La defensa puede volver a cualquier bloque: problema, corpus, guardrails, interfaz o resultados.", {
    x: 0.72,
    y: 2.66,
    w: 4.0,
    h: 0.48,
    fontFace: FONTS.body,
    fontSize: 11,
    color: COLORS.muted,
    margin: 0,
  });

  slide.addShape(pptx.ShapeType.ellipse, {
    x: 6.28,
    y: 2.0,
    w: 1.96,
    h: 1.96,
    line: { color: COLORS.stone, pt: 1.2 },
    fill: { color: COLORS.paper, transparency: 100 },
  });
  slide.addShape(pptx.ShapeType.ellipse, {
    x: 6.72,
    y: 2.44,
    w: 1.08,
    h: 1.08,
    line: { color: COLORS.blue, pt: 1.2, transparency: 35 },
    fill: { color: COLORS.sky, transparency: 16 },
  });
  slide.addText("Tutor\nCrow", {
    x: 6.98,
    y: 2.82,
    w: 0.56,
    h: 0.22,
    fontFace: FONTS.title,
    fontSize: 13,
    bold: true,
    color: COLORS.blue,
    align: "center",
    margin: 0,
  });

  const closingLabels = [
    ["Problema", 6.0, 1.8],
    ["RAG", 8.0, 2.78],
    ["Guardrails", 5.94, 4.18],
    ["Evidencia", 7.88, 4.46],
  ];
  closingLabels.forEach(([label, x, y]) => {
    addChip(slide, label, x, y, 0.84, COLORS.ivory, COLORS.text);
  });
}

function ensureOutputDir() {
  fs.mkdirSync(outputDir, { recursive: true });
}

async function writePresentation() {
  await pptx.writeFile({ fileName: outputFile, compression: true });
  console.log(`Created ${outputFile}`);
}
