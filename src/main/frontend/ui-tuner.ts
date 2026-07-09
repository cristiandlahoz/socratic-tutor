export {};

type TuneControl = {
  key: keyof TuneValues;
  label: string;
  min: number;
  max: number;
  step: number;
  unit: string;
};

type TuneValues = {
  radius: number;
  padding: number;
  gap: number;
  borderWidth: number;
  surfaceAlpha: number;
  borderAlpha: number;
  shadowAlpha: number;
  shadowBlur: number;
  fontScale: number;
  accentHue: number;
  transitionMs: number;
};

type TuneState = {
  selector: string;
  target: string;
  values: TuneValues;
  customCss: Record<string, string>;
};

declare global {
  interface Window {
    __socraticUiTuner?: {
      values: TuneState;
      open: () => void;
      close: () => void;
      reset: () => void;
    };
  }
}

const STORAGE_KEY = "socratic-ui-tuner";
const STYLE_ID = "socratic-ui-tuner-style";
const PANEL_TAG = "socratic-ui-tuner";
const TARGET_ATTR = "data-ui-tune-target";
const TARGET_SELECTOR = `[${TARGET_ATTR}="selected"]`;
const SHADOW_HOST_ATTR = "data-ui-tune-shadow-host";
const SHADOW_PART_ATTR = "data-ui-tune-shadow-part";

const ORIGINAL_STYLE = new WeakMap<HTMLElement, string>();

const DEFAULT_STATE: TuneState = {
  selector: TARGET_SELECTOR,
  target: "No element selected",
  customCss: {},
  values: {
    radius: 18,
    padding: 18,
    gap: 14,
    borderWidth: 1,
    surfaceAlpha: 0.76,
    borderAlpha: 0.18,
    shadowAlpha: 0.18,
    shadowBlur: 28,
    fontScale: 1,
    accentHue: 155,
    transitionMs: 180,
  },
};

const CONTROLS: TuneControl[] = [
  { key: "radius", label: "Radius", min: 0, max: 48, step: 1, unit: "px" },
  { key: "padding", label: "Padding", min: 0, max: 56, step: 1, unit: "px" },
  { key: "gap", label: "Gap", min: 0, max: 40, step: 1, unit: "px" },
  { key: "borderWidth", label: "Border", min: 0, max: 4, step: 0.5, unit: "px" },
  { key: "surfaceAlpha", label: "Surface", min: 0, max: 1, step: 0.01, unit: "" },
  { key: "borderAlpha", label: "Border alpha", min: 0, max: 1, step: 0.01, unit: "" },
  { key: "shadowAlpha", label: "Shadow", min: 0, max: 1, step: 0.01, unit: "" },
  { key: "shadowBlur", label: "Shadow blur", min: 0, max: 80, step: 1, unit: "px" },
  { key: "fontScale", label: "Font scale", min: 0.82, max: 1.3, step: 0.01, unit: "×" },
  { key: "accentHue", label: "Accent hue", min: 0, max: 360, step: 1, unit: "°" },
  { key: "transitionMs", label: "Motion", min: 0, max: 600, step: 10, unit: "ms" },
];

const CSS_PROPERTIES = [
  "align-content", "align-items", "align-self", "appearance", "aspect-ratio", "backdrop-filter", "background",
  "background-color", "background-image", "background-position", "background-size", "border", "border-bottom",
  "border-bottom-color", "border-bottom-left-radius", "border-bottom-right-radius", "border-bottom-width", "border-color",
  "border-left", "border-left-color", "border-left-width", "border-radius", "border-right", "border-right-color",
  "border-right-width", "border-top", "border-top-color", "border-top-left-radius", "border-top-right-radius",
  "border-top-width", "border-width", "bottom", "box-shadow", "box-sizing", "color", "column-gap", "cursor", "display",
  "filter", "flex", "flex-basis", "flex-direction", "flex-grow", "flex-shrink", "flex-wrap", "font", "font-family",
  "font-size", "font-style", "font-weight", "gap", "grid", "grid-area", "grid-template-columns", "height", "inset",
  "justify-content", "justify-items", "justify-self", "left", "letter-spacing", "line-height", "margin", "margin-bottom",
  "margin-left", "margin-right", "margin-top", "max-height", "max-width", "min-height", "min-width", "object-fit",
  "opacity", "outline", "outline-color", "outline-offset", "outline-width", "overflow", "overflow-x", "overflow-y",
  "padding", "padding-bottom", "padding-left", "padding-right", "padding-top", "place-content", "place-items",
  "pointer-events", "position", "right", "row-gap", "text-align", "text-decoration", "text-shadow", "text-transform",
  "top", "transform", "transform-origin", "transition", "translate", "user-select", "vertical-align", "visibility",
  "white-space", "width", "z-index", "--aura-accent-color", "--aura-surface-level", "--aura-surface-opacity",
  "--vaadin-padding", "--vaadin-radius", "--vaadin-text-color",
];

const COMMON_CSS_PRESETS = [
  { label: "Text color", property: "color", value: "var(--vaadin-text-color)" },
  { label: "Muted text", property: "color", value: "var(--vaadin-text-color-secondary)" },
  { label: "Background", property: "background-color", value: "var(--aura-surface-color)" },
  { label: "Transparent bg", property: "background", value: "transparent" },
  { label: "Radius", property: "border-radius", value: "12px" },
  { label: "Padding", property: "padding", value: "12px" },
  { label: "Gap", property: "gap", value: "8px" },
  { label: "Font size", property: "font-size", value: "14px" },
  { label: "Weight", property: "font-weight", value: "600" },
  { label: "Shadow", property: "box-shadow", value: "var(--aura-shadow-s)" },
  { label: "No shadow", property: "box-shadow", value: "none" },
];

const CSS_UNITS = ["px", "rem", "em", "%", "vh", "vw", "ch", "fr", "ms", "s", "deg", ""];

function cloneDefaultState(): TuneState {
  return JSON.parse(JSON.stringify(DEFAULT_STATE)) as TuneState;
}

function loadState(): TuneState {
  const fallback = cloneDefaultState();
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) {
      return fallback;
    }
    const parsed = JSON.parse(stored) as Partial<TuneState>;
    const storedSelector = typeof parsed.selector === "string" && parsed.selector.trim()
      ? parsed.selector.trim()
      : fallback.selector;
    const values = { ...fallback.values, ...(parsed.values ?? {}) };
    CONTROLS.forEach((control) => {
      const value = values[control.key];
      if (!Number.isFinite(value)) {
        values[control.key] = fallback.values[control.key];
        return;
      }
      values[control.key] = Math.min(control.max, Math.max(control.min, value));
    });
    return {
      selector: storedSelector === ".ui-tune-target" ? TARGET_SELECTOR : storedSelector,
      target: typeof parsed.target === "string" ? parsed.target : fallback.target,
      customCss: parsed.customCss && typeof parsed.customCss === "object" ? parsed.customCss as Record<string, string> : {},
      values,
    };
  } catch (_error) {
    return fallback;
  }
}

function saveState(state: TuneState): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function splitPartSelector(selector: string): { hostSelector: string; partName: string } | null {
  const match = selector.match(/^(.*)::part\(([^)]+)\)$/);
  if (!match) {
    return null;
  }
  return { hostSelector: match[1].trim(), partName: match[2].trim() };
}

function validSelector(selector: string): string {
  const trimmed = selector.trim();
  if (!trimmed) {
    return DEFAULT_STATE.selector;
  }
  const partSelector = splitPartSelector(trimmed);
  try {
    document.querySelector(partSelector ? partSelector.hostSelector : trimmed);
    return trimmed;
  } catch (_error) {
    return DEFAULT_STATE.selector;
  }
}

function matchedHosts(selector: string): HTMLElement[] {
  const safeSelector = validSelector(selector);
  const partSelector = splitPartSelector(safeSelector);
  const hostSelector = partSelector ? partSelector.hostSelector : safeSelector;
  return [...document.querySelectorAll(hostSelector)].filter((element): element is HTMLElement => element instanceof HTMLElement);
}

function previewSelectorFor(selector: string): string {
  const safeSelector = validSelector(selector);
  const partSelector = splitPartSelector(safeSelector);
  if (partSelector) {
    return `${partSelector.hostSelector}[data-ui-tune-preview="on"]::part(${partSelector.partName})`;
  }
  return safeSelector === TARGET_SELECTOR ? `${TARGET_SELECTOR}[data-ui-tune-preview="on"]` : safeSelector;
}

function targetLabel(selector: string): string {
  const safeSelector = validSelector(selector);
  const count = matchedHosts(safeSelector).length;
  const partSelector = splitPartSelector(safeSelector);
  if (safeSelector === TARGET_SELECTOR && count === 0) {
    return "No element selected. Pick an element to read its current visual values.";
  }
  if (partSelector) {
    return `${count} host${count === 1 ? "" : "s"} matched · shadow part: ${partSelector.partName}`;
  }
  return `${count} element${count === 1 ? "" : "s"} matched`;
}

function parsePx(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseAlpha(color: string): number {
  if (color === "transparent") {
    return 0;
  }
  const match = color.match(/rgba?\(([^)]+)\)/);
  if (!match) {
    return 1;
  }
  const parts = match[1].split(/[\s,\/]+/).filter(Boolean);
  const alpha = Number.parseFloat(parts[3] ?? "1");
  return Number.isFinite(alpha) ? alpha : 1;
}

function parseBoxShadow(shadow: string): { blur: number; alpha: number; color: string } {
  if (!shadow || shadow === "none") {
    return { blur: 0, alpha: 0, color: "rgb(0 0 0)" };
  }
  const colorMatch = shadow.match(/rgba?\([^)]+\)|oklch\([^)]+\)|#[0-9a-fA-F]+/);
  const color = colorMatch?.[0] ?? "rgb(0 0 0)";
  const lengths = shadow.replace(color, "").match(/-?\d*\.?\d+px/g) ?? [];
  return {
    blur: parsePx(lengths[2] ?? lengths[1] ?? "0px"),
    alpha: parseAlpha(color),
    color,
  };
}

function readElementValues(element: HTMLElement, previousValues: TuneValues): TuneValues {
  const style = getComputedStyle(element);
  const shadow = parseBoxShadow(style.boxShadow);
  const rowGap = style.rowGap === "normal" ? "0px" : style.rowGap;
  const columnGap = style.columnGap === "normal" ? "0px" : style.columnGap;
  return {
    radius: parsePx(style.borderTopLeftRadius),
    padding: parsePx(style.paddingTop),
    gap: Math.max(parsePx(rowGap), parsePx(columnGap)),
    borderWidth: parsePx(style.borderTopWidth),
    surfaceAlpha: parseAlpha(style.backgroundColor),
    borderAlpha: parsePx(style.borderTopWidth) > 0 ? parseAlpha(style.borderTopColor) : 0,
    shadowAlpha: shadow.alpha,
    shadowBlur: shadow.blur,
    fontScale: previousValues.fontScale,
    accentHue: previousValues.accentHue,
    transitionMs: previousValues.transitionMs,
  };
}

function setElementBaseValues(element: HTMLElement, variableHost = element): void {
  const style = getComputedStyle(element);
  const shadow = parseBoxShadow(style.boxShadow);
  variableHost.style.setProperty("--ui-tune-base-background", style.backgroundColor || "transparent");
  variableHost.style.setProperty("--ui-tune-base-border-color", style.borderTopColor || "currentColor");
  variableHost.style.setProperty("--ui-tune-base-shadow-color", shadow.color);
}

function clearPickedTargets(): void {
  document.querySelectorAll(`[${TARGET_ATTR}], [${SHADOW_HOST_ATTR}]`).forEach((element) => {
    element.removeAttribute(TARGET_ATTR);
    element.removeAttribute(SHADOW_HOST_ATTR);
    element.removeAttribute(SHADOW_PART_ATTR);
    element.removeAttribute("data-ui-tune-preview");
  });
}

function shadowHostFor(element: HTMLElement): HTMLElement | null {
  const root = element.getRootNode();
  return root instanceof ShadowRoot && root.host instanceof HTMLElement ? root.host : null;
}

function firstPartName(element: HTMLElement): string | null {
  const part = element.getAttribute("part")?.trim().split(/\s+/)[0];
  return part || null;
}

function shortElementLabel(element: HTMLElement): string {
  const id = element.id ? `#${element.id}` : "";
  const classes = [...element.classList].filter((name) => !name.startsWith("ui-")).slice(0, 2).map((name) => `.${name}`).join("");
  const part = element.getAttribute("part") ? `::part(${firstPartName(element)})` : "";
  const text = (element.textContent ?? "").trim().replace(/\s+/g, " ").slice(0, 42);
  return `${element.localName}${id}${classes}${part}${text ? ` · “${text}”` : ""}`;
}

function describeTarget(element: HTMLElement): string {
  const host = shadowHostFor(element);
  if (!host) {
    return shortElementLabel(element);
  }
  return `${shortElementLabel(host)} → shadow → ${shortElementLabel(element)}`;
}

function deepElementFromPoint(x: number, y: number): HTMLElement | null {
  let current = document.elementFromPoint(x, y);
  while (current instanceof HTMLElement && current.shadowRoot) {
    const inner = current.shadowRoot.elementFromPoint(x, y);
    if (!inner || inner === current) {
      break;
    }
    current = inner;
  }
  return current instanceof HTMLElement ? current : null;
}

function allElementsDeep(root: Document | ShadowRoot | Element = document): HTMLElement[] {
  const elements = [...root.querySelectorAll<HTMLElement>("*")];
  const nested = elements.flatMap((element) => element.shadowRoot ? allElementsDeep(element.shadowRoot) : []);
  return [...elements, ...nested];
}

function granularElementFromPoint(x: number, y: number, tuner: HTMLElement): HTMLElement | null {
  const candidates = allElementsDeep()
    .filter((element) => element !== tuner && !tuner.contains(element))
    .map((element) => ({ element, rect: element.getBoundingClientRect() }))
    .filter(({ rect }) => rect.width > 0 && rect.height > 0 && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom)
    .sort((a, b) => (a.rect.width * a.rect.height) - (b.rect.width * b.rect.height));
  return candidates[0]?.element ?? deepElementFromPoint(x, y);
}

function rememberOriginalStyle(element: HTMLElement): void {
  if (!ORIGINAL_STYLE.has(element)) {
    ORIGINAL_STYLE.set(element, element.getAttribute("style") ?? "");
  }
}

function restoreOriginalStyle(element: HTMLElement): void {
  const original = ORIGINAL_STYLE.get(element);
  if (original === undefined) {
    return;
  }
  if (original) {
    element.setAttribute("style", original);
  } else {
    element.removeAttribute("style");
  }
}

function applyPreview(element: HTMLElement, values: TuneValues): void {
  rememberOriginalStyle(element);
  setElementBaseValues(element);
  element.setAttribute("data-ui-tune-preview", "on");
  element.style.setProperty("background", `color-mix(in srgb, var(--ui-tune-base-background, transparent) ${Math.round(values.surfaceAlpha * 100)}%, transparent)`, "important");
  element.style.setProperty("border", `${values.borderWidth}px solid color-mix(in srgb, var(--ui-tune-base-border-color, currentColor) ${Math.round(values.borderAlpha * 100)}%, transparent)`, "important");
  element.style.setProperty("border-radius", `${values.radius}px`, "important");
  element.style.setProperty("box-shadow", `0 ${values.shadowBlur / 2}px ${values.shadowBlur}px color-mix(in srgb, var(--ui-tune-base-shadow-color, black) ${Math.round(values.shadowAlpha * 100)}%, transparent)`, "important");
  element.style.setProperty("font-size", `calc(1em * ${values.fontScale})`, "important");
  element.style.setProperty("gap", `${values.gap}px`, "important");
  element.style.setProperty("padding", `${values.padding}px`, "important");
  element.style.setProperty("transition", `all ${values.transitionMs}ms ease`, "important");
}

function applyCustomCss(element: HTMLElement, customCss: Record<string, string>): void {
  rememberOriginalStyle(element);
  Object.entries(customCss).forEach(([property, value]) => {
    if (property.trim() && value.trim()) {
      element.style.setProperty(property.trim(), value.trim(), "important");
    }
  });
}

function applyState(state: TuneState): void {
  const root = document.documentElement;
  root.dataset.uiTunerActive = "";
  const { values } = state;
  root.style.setProperty("--ui-tune-radius", `${values.radius}px`);
  root.style.setProperty("--ui-tune-padding", `${values.padding}px`);
  root.style.setProperty("--ui-tune-gap", `${values.gap}px`);
  root.style.setProperty("--ui-tune-border-width", `${values.borderWidth}px`);
  root.style.setProperty("--ui-tune-surface-alpha", `${values.surfaceAlpha}`);
  root.style.setProperty("--ui-tune-surface-alpha-pct", `${Math.round(values.surfaceAlpha * 100)}%`);
  root.style.setProperty("--ui-tune-border-alpha", `${values.borderAlpha}`);
  root.style.setProperty("--ui-tune-border-alpha-pct", `${Math.round(values.borderAlpha * 100)}%`);
  root.style.setProperty("--ui-tune-shadow-alpha", `${values.shadowAlpha}`);
  root.style.setProperty("--ui-tune-shadow-alpha-pct", `${Math.round(values.shadowAlpha * 100)}%`);
  root.style.setProperty("--ui-tune-shadow-blur", `${values.shadowBlur}px`);
  root.style.setProperty("--ui-tune-font-scale", `${values.fontScale}`);
  root.style.setProperty("--ui-tune-accent-hue", `${values.accentHue}`);
  root.style.setProperty("--ui-tune-transition", `${values.transitionMs}ms`);

  const selector = validSelector(state.selector);
  const previewSelector = previewSelectorFor(selector);
  let style = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = STYLE_ID;
    document.head.append(style);
  }
  style.textContent = `
    html[data-ui-tuner-active] ${previewSelector} {
      background: color-mix(in srgb, var(--ui-tune-base-background, transparent) var(--ui-tune-surface-alpha-pct), transparent) !important;
      border: var(--ui-tune-border-width) solid color-mix(in srgb, var(--ui-tune-base-border-color, currentColor) var(--ui-tune-border-alpha-pct), transparent) !important;
      border-radius: var(--ui-tune-radius) !important;
      box-shadow: 0 calc(var(--ui-tune-shadow-blur) / 2) var(--ui-tune-shadow-blur)
        color-mix(in srgb, var(--ui-tune-base-shadow-color, black) var(--ui-tune-shadow-alpha-pct), transparent) !important;
      color: inherit;
      font-size: calc(1em * var(--ui-tune-font-scale)) !important;
      gap: var(--ui-tune-gap) !important;
      padding: var(--ui-tune-padding) !important;
      transition: all var(--ui-tune-transition) ease !important;
    }
    html[data-ui-tuner-active] ${previewSelector} :is(a, button, vaadin-button) {
      --aura-accent-color: oklch(70% 0.15 var(--ui-tune-accent-hue));
      --aura-accent-text-color: oklch(76% 0.17 var(--ui-tune-accent-hue));
    }
    html[data-ui-picking-tune-target] * { cursor: crosshair !important; }
    html[data-ui-picking-tune-target] body *:hover:not(${PANEL_TAG}):not(${PANEL_TAG} *) {
      outline: 2px dashed oklch(76% 0.17 var(--ui-tune-accent-hue)) !important;
      outline-offset: 3px !important;
    }
    html[data-ui-picking-tune-target] ${PANEL_TAG}, html[data-ui-picking-tune-target] ${PANEL_TAG} * { cursor: default !important; }
  `;
  window.__socraticUiTuner = tunerApi(state);
}

function tunerApi(state: TuneState) {
  return {
    values: state,
    open: () => openPanel(),
    close: () => document.querySelector(PANEL_TAG)?.remove(),
    reset: () => {
      const resetState = cloneDefaultState();
      saveState(resetState);
      applyState(resetState);
      openPanel(true);
    },
  };
}

function exportText(state: TuneState): string {
  return `UI tuner target:\n${state.target}\n\nUI tuner params:\n${JSON.stringify(state, null, 2)}\n\nMeaning: target describes the exact element picked, including shadow DOM context when present; values are temporary preview values for the final CSS pass.`;
}

class SocraticUiTuner extends HTMLElement {
  private state = loadState();
  private pickingTarget = false;
  private selectedElement?: HTMLElement;

  connectedCallback(): void {
    this.applyHostStyles();
    if (!this.shadowRoot) {
      this.attachShadow({ mode: "open" });
    }
    this.render();
    applyState(this.state);
  }

  private applyHostStyles(): void {
    this.style.setProperty("position", "fixed", "important");
    this.style.setProperty("top", "18px", "important");
    this.style.setProperty("right", "18px", "important");
    this.style.setProperty("bottom", "auto", "important");
    this.style.setProperty("left", "auto", "important");
    this.style.setProperty("display", "block", "important");
    this.style.setProperty("width", "auto", "important");
    this.style.setProperty("height", "auto", "important");
    this.style.setProperty("z-index", "2147483647", "important");
  }

  private setValue(key: keyof TuneValues, value: number): void {
    this.state = { ...this.state, values: { ...this.state.values, [key]: value } };
    if (this.selectedElement) {
      applyPreview(this.selectedElement, this.state.values);
    } else {
      const targetsShadowPart = splitPartSelector(validSelector(this.state.selector)) !== null;
      matchedHosts(this.state.selector).forEach((element) => {
        if (!targetsShadowPart) {
          setElementBaseValues(element);
        }
        element.setAttribute("data-ui-tune-preview", "on");
      });
      applyState(this.state);
    }
    saveState(this.state);
    this.updateLiveValues(key);
  }

  private setSelector(selector: string): void {
    this.state = { ...this.state, selector };
    saveState(this.state);
    applyState(this.state);
    this.updateStatus();
    this.updateExport();
  }

  private startPicking(): void {
    if (this.pickingTarget) {
      return;
    }
    this.pickingTarget = true;
    document.documentElement.dataset.uiPickingTuneTarget = "";
    this.updateStatus("Picking: click a component. Hold ⌘ while clicking to drill into shadow DOM internals.");

    const pick = (event: MouseEvent) => {
      const path = event.composedPath();
      const pathTarget = path.find((item) => item instanceof HTMLElement) as HTMLElement | undefined;
      if (!pathTarget || path.includes(this)) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      if (this.selectedElement) {
        restoreOriginalStyle(this.selectedElement);
      }
      clearPickedTargets();
      this.selectedElement = event.metaKey
        ? (granularElementFromPoint(event.clientX, event.clientY, this) ?? pathTarget)
        : (shadowHostFor(pathTarget) ?? pathTarget);
      const target = this.selectedElement;
      const shadowHost = shadowHostFor(target);
      const partName = firstPartName(target);
      if (shadowHost && partName) {
        shadowHost.setAttribute(SHADOW_HOST_ATTR, "selected");
        shadowHost.setAttribute(SHADOW_PART_ATTR, partName);
        this.state = {
          ...this.state,
          selector: `[${SHADOW_HOST_ATTR}="selected"]::part(${partName})`,
          target: describeTarget(target),
          customCss: {},
          values: readElementValues(target, this.state.values),
        };
      } else {
        target.setAttribute(TARGET_ATTR, "selected");
        this.state = {
          ...this.state,
          selector: TARGET_SELECTOR,
          target: describeTarget(target),
          customCss: {},
          values: readElementValues(target, this.state.values),
        };
      }
      saveState(this.state);
      applyState(this.state);
      this.render();
      cleanup();
    };

    const cancel = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        cleanup();
      }
    };

    const cleanup = () => {
      this.pickingTarget = false;
      delete document.documentElement.dataset.uiPickingTuneTarget;
      document.removeEventListener("click", pick, true);
      document.removeEventListener("keydown", cancel, true);
      this.updateStatus();
    };

    document.addEventListener("click", pick, true);
    document.addEventListener("keydown", cancel, true);
  }

  private async copyExport(): Promise<void> {
    const text = exportText(this.state);
    await navigator.clipboard?.writeText(text);
    this.updateStatus("Copied params to clipboard.");
  }

  private applyCssPropertyFromPanel(): void {
    const propertyInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-property]");
    const valueInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-value]");
    this.applyCssProperty(propertyInput?.value.trim() ?? "", valueInput?.value.trim() ?? "");
  }

  private applyCssProperty(property: string, value: string): void {
    if (!property || !value) {
      this.updateStatus("Enter a CSS property and value first. Custom properties like --token-name work too.");
      return;
    }
    if (!property.startsWith("--") && !CSS.supports(property, value)) {
      this.updateStatus(`Browser did not recognize ${property}: ${value}; applying anyway.`);
    }
    this.state = { ...this.state, customCss: { ...this.state.customCss, [property]: value } };
    if (this.selectedElement) {
      applyCustomCss(this.selectedElement, this.state.customCss);
    }
    applyState(this.state);
    saveState(this.state);
    this.updateCssList();
    this.updateExport();
    this.updateStatus(`Applied ${property}: ${value}`);
  }

  private applyNumericCssPropertyFromPanel(): void {
    const property = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-property]")?.value.trim() ?? "";
    const number = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-number]")?.value.trim() ?? "";
    const unit = this.shadowRoot?.querySelector<HTMLSelectElement>("[data-css-unit]")?.value ?? "px";
    this.applyCssProperty(property, `${number}${unit}`);
  }

  private setCssPreset(property: string, value: string): void {
    const propertyInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-property]");
    const valueInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-value]");
    if (propertyInput) {
      propertyInput.value = property;
    }
    if (valueInput) {
      valueInput.value = value;
    }
    this.applyCssProperty(property, value);
  }

  private prefillCssValue(property: string): void {
    if (!this.selectedElement || !property) {
      return;
    }
    const valueInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-value]");
    const numberInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-number]");
    const unitSelect = this.shadowRoot?.querySelector<HTMLSelectElement>("[data-css-unit]");
    const value = getComputedStyle(this.selectedElement).getPropertyValue(property).trim();
    if (valueInput && value) {
      valueInput.value = value;
    }
    const match = value.match(/^(-?\d*\.?\d+)([a-z%]*)$/i);
    if (match && numberInput && unitSelect) {
      numberInput.value = match[1];
      unitSelect.value = CSS_UNITS.includes(match[2]) ? match[2] : "px";
    }
  }

  private removeCssProperty(property: string): void {
    const { [property]: _removed, ...customCss } = this.state.customCss;
    this.state = { ...this.state, customCss };
    if (this.selectedElement) {
      restoreOriginalStyle(this.selectedElement);
      applyPreview(this.selectedElement, this.state.values);
      applyCustomCss(this.selectedElement, this.state.customCss);
    }
    applyState(this.state);
    saveState(this.state);
    this.updateCssList();
    this.updateExport();
  }

  private setTab(tab: "tune" | "css"): void {
    this.shadowRoot?.querySelectorAll<HTMLElement>("[data-panel]").forEach((panel) => {
      panel.hidden = panel.dataset.panel !== tab;
    });
    this.shadowRoot?.querySelectorAll<HTMLButtonElement>("[data-tab]").forEach((button) => {
      button.setAttribute("aria-selected", String(button.dataset.tab === tab));
    });
  }

  private reset(): void {
    if (this.selectedElement) {
      restoreOriginalStyle(this.selectedElement);
      this.selectedElement = undefined;
    }
    clearPickedTargets();
    this.state = cloneDefaultState();
    saveState(this.state);
    applyState(this.state);
    this.render();
  }

  private setViewport(label: string, width: number, height: number): void {
    const url = new URL(window.location.href);
    url.searchParams.set("uiTuner", "1");
    const features = [
      `width=${width}`,
      `height=${height}`,
      "left=80",
      "top=80",
      "resizable=yes",
      "scrollbars=yes",
      "popup=yes",
    ].join(",");
    const preview = window.open(url.toString(), `socratic-ui-${label}`, features);
    preview?.focus();
    this.updateStatus(preview
      ? `Opened ${label} preview window · ${width}×${height}`
      : `Popup blocked. Allow popups, then retry ${label} · ${width}×${height}`);
  }

  private render(): void {
    if (!this.shadowRoot) {
      return;
    }
    const controls = CONTROLS.map((control) => this.renderControl(control)).join("");
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          color-scheme: dark;
          font-family: Inter, ui-sans-serif, system-ui, sans-serif;
        }
        .panel {
          width: min(340px, calc(100vw - 32px));
          max-height: min(640px, calc(100vh - 96px));
          overflow: auto;
          overscroll-behavior: contain;
          border: 1px solid rgba(255,255,255,.16);
          border-radius: 14px;
          background: rgba(18, 18, 17, .96);
          padding: 12px;
        }
        header { display: flex; align-items: start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
        h2 { font-size: 14px; margin: 0; letter-spacing: .08em; text-transform: uppercase; }
        p { color: rgba(255,255,255,.62); font-size: 12px; line-height: 1.45; margin: 4px 0 0; }
        button {
          border: 1px solid rgba(255,255,255,.16);
          border-radius: 9px;
          background: rgba(255,255,255,.08);
          color: white;
          font: inherit;
          font-size: 12px;
          padding: 7px 9px;
        }
        button:hover { background: rgba(255,255,255,.14); }
        .actions, .viewports, .tabs, .css-row, .presets { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
        .viewports { margin-top: 8px; }
        .viewports button { color: rgba(255,255,255,.76); }
        .tabs { margin-bottom: 8px; }
        .tabs button[aria-selected="true"] { background: rgba(255,255,255,.18); color: white; }
        label { display: grid; gap: 7px; margin: 10px 0; color: rgba(255,255,255,.78); font-size: 12px; }
        .row { display: flex; justify-content: space-between; gap: 10px; }
        .status { color: rgba(255,255,255,.72); font-size: 12px; margin: 8px 0 2px; }
        .target-box, input[type="text"] {
          box-sizing: border-box; border: 1px solid rgba(255,255,255,.14); border-radius: 10px;
          background: rgba(255,255,255,.06); color: rgba(255,255,255,.86); padding: 9px 10px;
          font: 12px/1.35 ui-monospace, SFMono-Regular, Menlo, monospace;
          overflow-wrap: anywhere;
        }
        input[type="text"] { width: 100%; }
        input[type="color"] { width: 42px; min-height: 34px; padding: 0; border: 0; background: transparent; }
        .css-row label { flex: 1 1 9rem; margin: 0; }
        .css-row--compact label { flex-basis: 5rem; }
        select {
          border: 1px solid rgba(255,255,255,.14); border-radius: 10px; background: rgba(255,255,255,.06);
          color: rgba(255,255,255,.86); padding: 9px 10px; font: 12px ui-monospace, monospace;
        }
        .presets button { color: rgba(255,255,255,.76); }
        .css-list { display: grid; gap: 6px; margin-top: 10px; }
        .css-item { display: flex; justify-content: space-between; gap: 8px; align-items: center; border: 1px solid rgba(255,255,255,.1); border-radius: 9px; padding: 7px 8px; font: 11px/1.35 ui-monospace, monospace; color: rgba(255,255,255,.78); }
        .css-item button { padding: 4px 7px; }
        input[type="range"] { width: 100%; accent-color: oklch(76% .17 var(--ui-tune-accent-hue, ${this.state.values.accentHue})); }
        textarea {
          width: 100%; min-height: 128px; resize: vertical; box-sizing: border-box; border: 1px solid rgba(255,255,255,.16);
          border-radius: 14px; background: rgba(0,0,0,.22); color: rgba(255,255,255,.82); padding: 10px;
          font: 11px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace;
        }
        .hint { border-top: 1px solid rgba(255,255,255,.1); margin-top: 12px; padding-top: 12px; }
      </style>
      <section class="panel" aria-label="UI tuner">
        <header>
          <div>
            <h2>UI tuner</h2>
            <p>Pick one real element, tweak preview values, then send me the export.</p>
          </div>
          <button data-action="close" title="Close">×</button>
        </header>
        <label>
          <span class="row"><span>Selected target</span></span>
          <div class="target-box" data-target>${this.escapeHtml(this.state.target)}</div>
        </label>
        <p class="status" data-status>${this.escapeHtml(this.state.target === DEFAULT_STATE.target ? targetLabel(this.state.selector) : "Exact element selected")}</p>
        <div class="actions">
          <button data-action="pick">Pick exact element</button>
          <button data-action="copy">Copy params</button>
          <button data-action="reset">Reset</button>
        </div>
        <div class="viewports" aria-label="Viewport presets">
          <button data-viewport="mobile">Mobile 390</button>
          <button data-viewport="tablet">Tablet 768</button>
          <button data-viewport="desktop">Desktop 1440</button>
        </div>
        <div class="tabs" role="tablist" aria-label="Tuner modes">
          <button data-tab="tune" aria-selected="true">Tune</button>
          <button data-tab="css" aria-selected="false">Any CSS</button>
        </div>
        <div data-panel="tune">
          ${controls}
        </div>
        <div data-panel="css" hidden>
          ${this.renderCssDatalist()}
          <div class="css-row">
            <label><span>Property</span><input data-css-property type="text" list="ui-tuner-css-properties" placeholder="Pick MDN property or type --custom-token" /></label>
            <label><span>Value</span><input data-css-value type="text" placeholder="oklch(...), 12px, var(--token)" /></label>
            <label><span>Color</span><input data-css-color type="color" value="#38d996" /></label>
          </div>
          <div class="css-row css-row--compact">
            <label><span>Number</span><input data-css-number type="text" placeholder="12" /></label>
            <label><span>Unit</span><select data-css-unit>${this.renderUnitOptions()}</select></label>
            <button data-action="apply-css-number">Apply number</button>
          </div>
          <button data-action="apply-css">Apply CSS value</button>
          <div class="presets" aria-label="Common CSS presets">${this.renderPresetButtons()}</div>
          <div class="css-list" data-css-list>${this.renderCssList()}</div>
        </div>
        <label>
          <span class="row"><span>Export</span></span>
          <textarea data-export readonly>${this.escapeHtml(exportText(this.state))}</textarea>
        </label>
        <p class="hint">Toggle with <strong>⌘ + ⌥ + U</strong>. Viewport buttons open real-sized preview windows because CSS media queries need an actual viewport.</p>
      </section>
    `;
    this.bindEvents();
  }

  private renderControl(control: TuneControl): string {
    const value = this.state.values[control.key];
    return `
      <label>
        <span class="row"><span>${control.label}</span><strong data-value="${control.key}">${value}${control.unit}</strong></span>
        <input type="range" min="${control.min}" max="${control.max}" step="${control.step}" value="${value}" data-key="${control.key}" />
      </label>
    `;
  }

  private renderCssList(): string {
    const entries = Object.entries(this.state.customCss);
    if (entries.length === 0) {
      return `<p>No custom CSS yet.</p>`;
    }
    return entries.map(([property, value]) => `
      <div class="css-item">
        <span>${this.escapeHtml(property)}: ${this.escapeHtml(value)}</span>
        <button data-remove-css="${this.escapeHtml(property)}">Remove</button>
      </div>
    `).join("");
  }

  private renderCssDatalist(): string {
    return `<datalist id="ui-tuner-css-properties">${CSS_PROPERTIES.map((property) => `<option value="${property}"></option>`).join("")}</datalist>`;
  }

  private renderUnitOptions(): string {
    return CSS_UNITS.map((unit) => `<option value="${unit}" ${unit === "px" ? "selected" : ""}>${unit || "unitless"}</option>`).join("");
  }

  private renderPresetButtons(): string {
    return COMMON_CSS_PRESETS.map((preset) => `
      <button data-css-preset-property="${this.escapeHtml(preset.property)}" data-css-preset-value="${this.escapeHtml(preset.value)}">${this.escapeHtml(preset.label)}</button>
    `).join("");
  }

  private bindEvents(): void {
    this.shadowRoot?.querySelectorAll<HTMLInputElement>("input[type='range']").forEach((input) => {
      input.addEventListener("input", () => this.setValue(input.dataset.key as keyof TuneValues, Number(input.value)));
    });
    this.shadowRoot?.querySelector("[data-action='close']")?.addEventListener("click", () => this.remove());
    this.shadowRoot?.querySelector("[data-action='pick']")?.addEventListener("click", () => this.startPicking());
    this.shadowRoot?.querySelector("[data-action='copy']")?.addEventListener("click", () => void this.copyExport());
    this.shadowRoot?.querySelector("[data-action='reset']")?.addEventListener("click", () => this.reset());
    this.shadowRoot?.querySelector("[data-action='apply-css']")?.addEventListener("click", () => this.applyCssPropertyFromPanel());
    this.shadowRoot?.querySelector("[data-action='apply-css-number']")?.addEventListener("click", () => this.applyNumericCssPropertyFromPanel());
    this.shadowRoot?.querySelectorAll<HTMLButtonElement>("[data-tab]").forEach((button) => {
      button.addEventListener("click", () => this.setTab(button.dataset.tab === "css" ? "css" : "tune"));
    });
    this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-color]")?.addEventListener("input", (event) => {
      const valueInput = this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-value]");
      if (valueInput) {
        valueInput.value = (event.target as HTMLInputElement).value;
      }
    });
    this.shadowRoot?.querySelector<HTMLInputElement>("[data-css-property]")?.addEventListener("change", (event) => {
      this.prefillCssValue((event.target as HTMLInputElement).value.trim());
    });
    this.shadowRoot?.querySelectorAll<HTMLButtonElement>("[data-css-preset-property]").forEach((button) => {
      button.addEventListener("click", () => this.setCssPreset(button.dataset.cssPresetProperty ?? "", button.dataset.cssPresetValue ?? ""));
    });
    this.shadowRoot?.querySelectorAll<HTMLButtonElement>("[data-remove-css]").forEach((button) => {
      button.addEventListener("click", () => this.removeCssProperty(button.dataset.removeCss ?? ""));
    });
    this.shadowRoot?.querySelector("[data-viewport='mobile']")?.addEventListener("click", () => this.setViewport("mobile", 390, 844));
    this.shadowRoot?.querySelector("[data-viewport='tablet']")?.addEventListener("click", () => this.setViewport("tablet", 768, 1024));
    this.shadowRoot?.querySelector("[data-viewport='desktop']")?.addEventListener("click", () => this.setViewport("desktop", 1440, 1000));
  }

  private updateLiveValues(changedKey: keyof TuneValues): void {
    const control = CONTROLS.find((item) => item.key === changedKey);
    const value = this.state.values[changedKey];
    const readout = this.shadowRoot?.querySelector(`[data-value="${changedKey}"]`);
    if (readout && control) {
      readout.textContent = `${value}${control.unit}`;
    }
    this.updateExport();
  }

  private updateStatus(message = this.state.target === DEFAULT_STATE.target ? targetLabel(this.state.selector) : "Exact element selected"): void {
    const status = this.shadowRoot?.querySelector("[data-status]");
    if (status) {
      status.textContent = message;
    }
  }

  private updateExport(): void {
    const output = this.shadowRoot?.querySelector<HTMLTextAreaElement>("[data-export]");
    if (output) {
      output.value = exportText(this.state);
    }
  }

  private updateCssList(): void {
    const list = this.shadowRoot?.querySelector<HTMLElement>("[data-css-list]");
    if (!list) {
      return;
    }
    list.innerHTML = this.renderCssList();
    list.querySelectorAll<HTMLButtonElement>("[data-remove-css]").forEach((button) => {
      button.addEventListener("click", () => this.removeCssProperty(button.dataset.removeCss ?? ""));
    });
  }

  private escapeHtml(value: string): string {
    return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
  }
}

function openPanel(forceRender = false): void {
  const appendPanel = () => {
    let panel = document.querySelector(PANEL_TAG) as SocraticUiTuner | null;
    if (!panel) {
      panel = document.createElement(PANEL_TAG) as SocraticUiTuner;
      document.body.append(panel);
    } else if (forceRender) {
      panel.remove();
      openPanel();
    }
  };

  if (document.body) {
    appendPanel();
  } else {
    window.addEventListener("DOMContentLoaded", appendPanel, { once: true });
  }
}

function closePanel(): void {
  document.querySelector(PANEL_TAG)?.remove();
}

function togglePanel(): void {
  const panel = document.querySelector(PANEL_TAG);
  if (panel) {
    closePanel();
    return;
  }
  openPanel();
}

if (!customElements.get(PANEL_TAG)) {
  customElements.define(PANEL_TAG, SocraticUiTuner);
}

localStorage.removeItem("socratic-ui-tuner-enabled");

const initialState = loadState();
applyState(initialState);

function syncPanelWithLocation(): void {
  const params = new URLSearchParams(window.location.search);
  if (params.get("uiTuner") === "0") {
    closePanel();
    return;
  }
  if (params.get("uiTuner") === "1") {
    requestAnimationFrame(() => openPanel());
  }
}

function notifyLocationChanged(): void {
  window.dispatchEvent(new Event("ui-tuner-location-changed"));
}

const originalPushState = history.pushState.bind(history);
history.pushState = (...args) => {
  const result = originalPushState(...args);
  notifyLocationChanged();
  return result;
};

const originalReplaceState = history.replaceState.bind(history);
history.replaceState = (...args) => {
  const result = originalReplaceState(...args);
  notifyLocationChanged();
  return result;
};

window.addEventListener("popstate", notifyLocationChanged);
window.addEventListener("ui-tuner-location-changed", syncPanelWithLocation);

window.addEventListener(
  "keydown",
  (event) => {
    if ((event.metaKey || event.ctrlKey) && event.altKey && event.code === "KeyU") {
      event.preventDefault();
      event.stopPropagation();
      togglePanel();
    }
  },
  true,
);

syncPanelWithLocation();
window.setTimeout(syncPanelWithLocation, 0);
