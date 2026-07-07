import 'Frontend/shared/code/code-block-viewer.js';
import DOMPurify from 'dompurify';
import { LitElement, html, type PropertyValues } from 'lit';
import { Marked, type HooksObject, type RendererObject, type Tokens } from 'marked';
import { estimateMarkdownBlockSize } from './markdown-layout-estimate.js';

const MARKDOWN_RENDERER_STYLE_ID = 'markdown-renderer-styles';
const MARKDOWN_CONTENT_SELECTOR = '[data-markdown-content]';
const MIN_PENDING_RENDER_BLOCK_SIZE_PX = 96;

type CodeBlock = {
  code: string;
  lang: string;
};

type CodeBlockViewer = HTMLElement & {
  value: string;
  lang: string;
  debuggable: boolean;
};

function ensureMarkdownRendererStyles(): void {
  if (document.getElementById(MARKDOWN_RENDERER_STYLE_ID)) {
    return;
  }

  const style = document.createElement('style');
  style.id = MARKDOWN_RENDERER_STYLE_ID;
  style.textContent = `
    markdown-renderer {
      --mk-font-family: var(
        --aura-font-family,
        ui-sans-serif,
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        Helvetica,
        Arial,
        sans-serif
      );
      --mk-font-family-mono: var(
        --aura-font-family,
        ui-monospace,
        "SF Mono",
        SFMono-Regular,
        Menlo,
        Monaco,
        Consolas,
        "Liberation Mono",
        monospace
      );

      --mk-font-size: var(--message-item-font-size-base, var(--aura-font-size-m, 13px));
      --mk-line-height: var(--message-item-line-height, var(--aura-line-height-m, 1.56));

      --mk-bg: transparent;
      --mk-surface: var(--vaadin-background-container, color-mix(in srgb, var(--vaadin-text-color) 5%, transparent));
      --mk-surface-raised: var(--vaadin-background-container-strong, color-mix(in srgb, var(--vaadin-text-color) 9%, transparent));
      --mk-surface-hover: color-mix(in srgb, var(--vaadin-text-color) 6%, transparent);

      --mk-text: var(--markdown-renderer-text-color, var(--message-prose-color, var(--vaadin-text-color)));
      --mk-text-strong: var(--markdown-renderer-heading-color, var(--vaadin-text-color));
      --mk-text-muted: var(--vaadin-text-color-secondary);
      --mk-text-faint: var(--vaadin-text-color-disabled);

      --mk-primary: var(--aura-accent-text-color, var(--vaadin-focus-ring-color));
      --mk-primary-soft: color-mix(in srgb, var(--mk-primary) 16%, transparent);
      --mk-border: var(--vaadin-border-color-secondary, var(--vaadin-border-color));
      --mk-border-strong: var(--vaadin-border-color);

      --mk-inline-code-bg: light-dark(#c4c4bd, #2b2b2b);
      --mk-inline-code-text: var(--aura-code-text-color, #eb5757);
      --mk-block-code-bg: var(--vaadin-background-container);
      --mk-highlight-bg: color-mix(in srgb, var(--aura-yellow, var(--mk-primary)) 20%, transparent);
      --mk-highlight-text: var(--mk-text-strong);
      --mk-selection-bg: var(--selection-background, color-mix(in srgb, var(--mk-primary) 26%, transparent));

      --mk-table-bg: transparent;
      --mk-table-header-bg: transparent;
      --mk-table-row-alt-bg: transparent;
      --mk-table-row-hover-bg: color-mix(in srgb, var(--vaadin-text-color) 3%, transparent);
      --mk-table-border: var(--mk-border-strong);
      --mk-table-code-bg: var(--mk-inline-code-bg);
      --mk-table-code-text: var(--mk-inline-code-text);
      --mk-table-cell-min-width: calc(var(--mk-font-size) * 10.5);
      --mk-table-font-size: var(--mk-step-1);

      --mk-step--1: clamp(calc(var(--mk-font-size) * 0.88), 0.78rem, calc(var(--mk-font-size) * 0.95));
      --mk-step-0: var(--mk-font-size);
      --mk-step-1: clamp(calc(var(--mk-font-size) * 1.08), 1.1vw, calc(var(--mk-font-size) * 1.18));
      --mk-step-2: clamp(calc(var(--mk-font-size) * 1.22), 1.45vw, calc(var(--mk-font-size) * 1.42));
      --mk-step-3: clamp(calc(var(--mk-font-size) * 1.48), 2vw, calc(var(--mk-font-size) * 1.78));
      --mk-step-4: clamp(calc(var(--mk-font-size) * 1.86), 3vw, calc(var(--mk-font-size) * 2.42));

      --mk-space-025: calc(var(--mk-font-size) * 0.25);
      --mk-space-050: calc(var(--mk-font-size) * 0.5);
      --mk-space-075: calc(var(--mk-font-size) * 0.75);
      --mk-space-100: var(--mk-font-size);
      --mk-space-150: calc(var(--mk-font-size) * 1.5);
      --mk-space-200: calc(var(--mk-font-size) * 2);
      --mk-space-300: calc(var(--mk-font-size) * 3);

      --mk-radius-xs: calc(var(--mk-font-size) * 0.3);
      --mk-radius-sm: calc(var(--mk-font-size) * 0.46);
      --mk-radius-md: var(--vaadin-radius-m, calc(var(--mk-font-size) * 0.7));

      display: block;
      width: 100%;
      min-width: 0;
      color: var(--mk-text);
      background: var(--mk-bg);
      font-family: var(--mk-font-family);
      font-size: var(--mk-step-0);
      line-height: var(--mk-line-height);
      overflow-x: auto;
      overflow-wrap: break-word;
      text-rendering: optimizeLegibility;
      -webkit-font-smoothing: antialiased;
    }

    markdown-renderer,
    markdown-renderer * {
      box-sizing: border-box;
    }

    markdown-renderer .markdown-renderer__content {
      display: block;
      width: 100%;
      min-width: 0;
    }

    markdown-renderer ::selection {
      background: var(--mk-selection-bg);
    }

    markdown-renderer .markdown-renderer__content > :first-child {
      margin-top: 0 !important;
    }

    markdown-renderer .markdown-renderer__content > :last-child {
      margin-bottom: 0 !important;
    }

    markdown-renderer p,
    markdown-renderer ul,
    markdown-renderer ol,
    markdown-renderer blockquote,
    markdown-renderer pre,
    markdown-renderer table,
    markdown-renderer details,
    markdown-renderer figure,
    markdown-renderer dl,
    markdown-renderer code-block-viewer {
      margin-block: var(--mk-space-075);
    }

    markdown-renderer h1,
    markdown-renderer h2,
    markdown-renderer h3,
    markdown-renderer h4,
    markdown-renderer h5,
    markdown-renderer h6 {
      margin: var(--mk-space-200) 0 var(--mk-space-075);
      color: var(--mk-text-strong);
      font-weight: 650;
      line-height: 1.18;
      letter-spacing: -0.025em;
      text-wrap: balance;
    }

    markdown-renderer h1 {
      font-size: var(--mk-step-4);
      margin-top: 0;
      letter-spacing: -0.045em;
    }

    markdown-renderer h2 {
      font-size: var(--mk-step-3);
      padding-bottom: var(--mk-space-050);
      border-bottom: 1px solid var(--mk-border);
    }

    markdown-renderer h3 {
      font-size: var(--mk-step-2);
    }

    markdown-renderer h4 {
      font-size: var(--mk-step-1);
    }

    markdown-renderer h5,
    markdown-renderer h6 {
      font-size: var(--mk-step-0);
      color: var(--mk-text-muted);
    }

    markdown-renderer h6 {
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    markdown-renderer p {
      color: var(--mk-text);
    }

    markdown-renderer br {
      line-height: inherit;
    }

    markdown-renderer strong {
      color: var(--mk-text-strong);
      font-weight: 650;
    }

    markdown-renderer em {
      color: inherit;
    }

    markdown-renderer small {
      color: var(--mk-text-muted);
      font-size: var(--mk-step--1);
    }

    markdown-renderer a {
      color: inherit;
      text-decoration-line: underline;
      text-decoration-thickness: 0.06em;
      text-underline-offset: 0.18em;
      text-decoration-color: color-mix(in srgb, var(--mk-text) 55%, transparent);
      transition: color 120ms ease, text-decoration-color 120ms ease, background-color 120ms ease;
    }

    markdown-renderer a:hover {
      color: var(--mk-primary);
      text-decoration-color: currentColor;
    }

    markdown-renderer mark {
      color: var(--mk-highlight-text);
      background: var(--mk-highlight-bg);
      border-radius: var(--mk-radius-xs);
      padding: 0.05em 0.28em;
    }

    markdown-renderer del {
      color: var(--mk-text-muted);
      text-decoration-color: color-mix(in srgb, var(--mk-text-muted) 70%, transparent);
    }

    markdown-renderer hr {
      height: 1px;
      margin: var(--mk-space-200) 0;
      border: 0;
      background: var(--mk-border);
    }

    markdown-renderer ul,
    markdown-renderer ol {
      padding-left: calc(var(--mk-font-size) * 1.65);
    }

    markdown-renderer li {
      margin-block: calc(var(--mk-font-size) * 0.28);
      padding-left: calc(var(--mk-font-size) * 0.18);
    }

    markdown-renderer li::marker {
      color: var(--mk-text-faint);
    }

    markdown-renderer li > p {
      margin-block: calc(var(--mk-font-size) * 0.28);
    }

    markdown-renderer ul ul,
    markdown-renderer ul ol,
    markdown-renderer ol ul,
    markdown-renderer ol ol {
      margin-block: calc(var(--mk-font-size) * 0.28);
    }

    markdown-renderer .contains-task-list {
      padding-left: calc(var(--mk-font-size) * 1.65);
      list-style: none;
    }

    markdown-renderer .task-list-item {
      position: relative;
      padding-left: calc(var(--mk-font-size) * 0.45);
    }

    markdown-renderer .task-list-item input[type="checkbox"] {
      appearance: none;
      position: absolute;
      inline-size: calc(var(--mk-font-size) * 1.05);
      block-size: calc(var(--mk-font-size) * 1.05);
      margin: 0;
      inset-inline-start: calc(var(--mk-font-size) * -1.45);
      inset-block-start: calc(var(--mk-font-size) * 0.27);
      border: 1px solid var(--mk-border-strong);
      border-radius: calc(var(--mk-font-size) * 0.23);
      background: transparent;
      opacity: 1;
      pointer-events: none;
    }

    markdown-renderer .task-list-item input[type="checkbox"]:checked {
      border-color: var(--mk-primary);
      background: var(--mk-primary);
    }

    markdown-renderer .task-list-item input[type="checkbox"]:checked::after {
      content: "";
      position: absolute;
      inset-inline-start: 33%;
      inset-block-start: 15%;
      inline-size: 30%;
      block-size: 55%;
      border: solid #fff;
      border-width: 0 1.6px 1.6px 0;
      transform: rotate(45deg);
    }

    markdown-renderer blockquote {
      margin-inline: 0;
      padding: var(--mk-space-075) var(--mk-space-100);
      color: var(--mk-text);
      background: var(--mk-surface);
      border-left: calc(var(--mk-font-size) * 0.28) solid var(--mk-border-strong);
      border-radius: 0 var(--mk-radius-sm) var(--mk-radius-sm) 0;
    }

    markdown-renderer blockquote blockquote {
      margin-top: var(--mk-space-075);
      background: transparent;
      border-left-color: var(--mk-text-faint);
    }

    markdown-renderer blockquote > :first-child {
      margin-top: 0;
    }

    markdown-renderer blockquote > :last-child {
      margin-bottom: 0;
    }

    markdown-renderer code,
    markdown-renderer kbd,
    markdown-renderer samp {
      font-family: var(--mk-font-family-mono);
      font-size: 0.93em;
    }

    markdown-renderer :not(pre) > code {
      color: var(--mk-inline-code-text);
      background: var(--mk-inline-code-bg);
      border: 1px solid var(--mk-border);
      border-radius: var(--mk-radius-xs);
      padding: 0.08em 0.34em;
      white-space: break-spaces;
    }

    markdown-renderer pre {
      overflow: auto;
      padding: var(--mk-space-100);
      color: var(--mk-text);
      background: var(--mk-block-code-bg);
      border: 1px solid var(--mk-border);
      border-radius: var(--mk-radius-md);
      box-shadow: inset 0 1px 0 color-mix(in srgb, var(--vaadin-text-color) 5%, transparent);
    }

    markdown-renderer pre code {
      display: block;
      min-width: 100%;
      padding: 0;
      color: inherit;
      background: transparent;
      border: 0;
      line-height: 1.55;
      white-space: pre;
    }

    markdown-renderer code-block-viewer {
      display: block;
      width: 100%;
    }

    markdown-renderer table {
      display: table;
      width: max-content;
      min-width: 100%;
      max-width: none;
      background: var(--mk-table-bg);
      border: 1px solid var(--mk-table-border);
      border-collapse: separate;
      border-spacing: 0;
      font-size: var(--mk-table-font-size);
      line-height: 1.42;
    }

    markdown-renderer thead {
      background: var(--mk-table-header-bg);
    }

    markdown-renderer th,
    markdown-renderer td {
      min-width: var(--mk-table-cell-min-width);
      padding: clamp(calc(var(--mk-font-size) * 0.62), 1.1vw, calc(var(--mk-font-size) * 0.94)) clamp(calc(var(--mk-font-size) * 0.82), 1.5vw, calc(var(--mk-font-size) * 1.18));
      border: 0;
      border-right: 1px solid var(--mk-table-border);
      border-bottom: 1px solid var(--mk-table-border);
      color: var(--mk-text-strong);
      vertical-align: top;
      text-align: left;
    }

    markdown-renderer th:last-child,
    markdown-renderer td:last-child {
      border-right: 0;
    }

    markdown-renderer tbody tr:last-child td {
      border-bottom: 0;
    }

    markdown-renderer th {
      font-weight: 700;
      letter-spacing: -0.015em;
    }

    markdown-renderer tr:nth-child(even) td {
      background: var(--mk-table-row-alt-bg);
    }

    markdown-renderer tbody tr:hover td {
      background: var(--mk-table-row-hover-bg);
    }

    markdown-renderer th[align="center"],
    markdown-renderer td[align="center"] {
      text-align: center;
    }

    markdown-renderer th[align="right"],
    markdown-renderer td[align="right"] {
      text-align: right;
    }

    markdown-renderer table :not(pre) > code {
      color: var(--mk-table-code-text);
      background: var(--mk-table-code-bg);
      border-color: transparent;
      border-radius: calc(var(--mk-font-size) * 0.42);
      padding: 0.12em 0.42em 0.16em;
      font-size: 0.9em;
      font-weight: 700;
      letter-spacing: 0.02em;
      white-space: break-spaces;
    }

    markdown-renderer img,
    markdown-renderer video,
    markdown-renderer canvas,
    markdown-renderer svg {
      max-width: 100%;
      height: auto;
    }

    markdown-renderer img {
      display: block;
      margin: var(--mk-space-100) auto;
      border-radius: var(--mk-radius-md);
    }

    markdown-renderer figure {
      margin-inline: 0;
    }

    markdown-renderer figcaption {
      margin-top: var(--mk-space-050);
      color: var(--mk-text-muted);
      font-size: var(--mk-step--1);
      text-align: center;
    }

    markdown-renderer kbd {
      display: inline-block;
      min-width: 1.8em;
      padding: 0.1em 0.45em 0.16em;
      color: var(--mk-text);
      background: var(--mk-surface-raised);
      border: 1px solid var(--mk-border-strong);
      border-radius: var(--mk-radius-xs);
      box-shadow: 0 1px 0 var(--mk-border-strong);
      text-align: center;
    }

    markdown-renderer details {
      padding: var(--mk-space-075) var(--mk-space-100);
      background: var(--mk-surface);
      border: 1px solid var(--mk-border);
      border-radius: var(--mk-radius-md);
    }

    markdown-renderer summary {
      cursor: pointer;
      color: var(--mk-text-strong);
      font-weight: 620;
    }

    markdown-renderer summary::marker {
      color: var(--mk-text-faint);
    }

    markdown-renderer details > :not(summary):first-of-type {
      margin-top: var(--mk-space-075);
    }

    markdown-renderer dl {
      display: grid;
      grid-template-columns: minmax(8rem, max-content) 1fr;
      gap: var(--mk-space-050) var(--mk-space-100);
    }

    markdown-renderer dt {
      color: var(--mk-text-strong);
      font-weight: 650;
    }

    markdown-renderer dd {
      margin: 0;
      color: var(--mk-text);
    }

    markdown-renderer sup,
    markdown-renderer sub {
      font-size: 0.75em;
      line-height: 0;
    }

    markdown-renderer .footnotes {
      margin-top: var(--mk-space-300);
      padding-top: var(--mk-space-100);
      color: var(--mk-text-muted);
      border-top: 1px solid var(--mk-border);
      font-size: var(--mk-step--1);
    }

    markdown-renderer .anchor,
    markdown-renderer .heading-anchor {
      color: var(--mk-text-faint);
      text-decoration: none;
    }

    @media (max-width: 560px) {
      markdown-renderer {
        --mk-table-cell-min-width: calc(var(--mk-font-size) * 8.5);
      }

      markdown-renderer h1 {
        letter-spacing: -0.035em;
      }

      markdown-renderer dl {
        grid-template-columns: 1fr;
        gap: var(--mk-space-025);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      markdown-renderer *,
      markdown-renderer *::before,
      markdown-renderer *::after {
        transition-duration: 0.001ms !important;
        animation-duration: 0.001ms !important;
        animation-iteration-count: 1 !important;
      }
    }
  `;

  document.head.appendChild(style);
}

function parseLanguage(info: string | undefined): string {
  return info?.trim().split(/\s+/)[0] ?? '';
}

function sanitizeMarkdownHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['data-code-block-index'],
    CUSTOM_ELEMENT_HANDLING: {
      tagNameCheck: (tagName) => tagName === 'code-block-viewer',
      attributeNameCheck: (attributeName) => attributeName === 'data-code-block-index',
    },
  }).replace(/\r?\n$/, '');
}

function createMarkdown(blocks: CodeBlock[]): Marked {
  const renderer: RendererObject = {
    code(token: Tokens.Code): string {
      const index = blocks.length;

      blocks.push({
        code: token.text,
        lang: parseLanguage(token.lang),
      });

      return `<code-block-viewer data-code-block-index="${index}"></code-block-viewer>`;
    },
  };

  const hooks: HooksObject = {
    postprocess: sanitizeMarkdownHtml,
  };

  return new Marked({
    gfm: true,
    breaks: true,
    renderer,
    hooks,
  });
}

type SynchronizableChildNode = Node & ChildNode;

function synchronizeAttributes(targetElement: Element, sourceElement: Element): void {
  for (const { name } of Array.from(targetElement.attributes)) {
    if (!sourceElement.hasAttribute(name)) {
      targetElement.removeAttribute(name);
    }
  }

  for (const { name, value } of Array.from(sourceElement.attributes)) {
    if (targetElement.getAttribute(name) !== value) {
      targetElement.setAttribute(name, value);
    }
  }
}

function canSynchronizeNode(targetChild: Node, sourceChild: Node): boolean {
  return targetChild.nodeType === sourceChild.nodeType && targetChild.nodeName === sourceChild.nodeName;
}

function synchronizeExistingNode(targetChild: SynchronizableChildNode, sourceChild: Node): void {
  if (!canSynchronizeNode(targetChild, sourceChild)) {
    targetChild.replaceWith(sourceChild.cloneNode(true));
    return;
  }

  if (targetChild.nodeType === Node.ELEMENT_NODE && sourceChild.nodeType === Node.ELEMENT_NODE) {
    const targetElement = targetChild as Element;
    const sourceElement = sourceChild as Element;

    synchronizeAttributes(targetElement, sourceElement);

    if (targetElement.localName === 'code-block-viewer') {
      return;
    }

    synchronizeNodes(targetElement, sourceElement);
    return;
  }

  if (targetChild.nodeType === Node.TEXT_NODE && targetChild.nodeValue !== sourceChild.nodeValue) {
    targetChild.nodeValue = sourceChild.nodeValue;
  }
}

function synchronizeNodeAtIndex(
  targetNode: Node,
  sourceChild: Node | undefined,
  targetChild: SynchronizableChildNode | undefined,
): void {
  if (!sourceChild) {
    targetChild?.remove();
    return;
  }

  if (!targetChild) {
    targetNode.appendChild(sourceChild.cloneNode(true));
    return;
  }

  synchronizeExistingNode(targetChild, sourceChild);
}

function synchronizeNodes(targetNode: Node, sourceNode: Node): void {
  const sourceChildren = Array.from(sourceNode.childNodes);
  const targetChildren = Array.from(targetNode.childNodes) as SynchronizableChildNode[];
  const maxChildren = Math.max(sourceChildren.length, targetChildren.length);

  for (let index = 0; index < maxChildren; index += 1) {
    synchronizeNodeAtIndex(targetNode, sourceChildren[index], targetChildren[index]);
  }
}

class MarkdownRenderer extends LitElement {
  static readonly properties = {
    content: { type: String },
    debuggableCodeBlocks: { type: Boolean, attribute: 'debuggable-code-blocks' },
  };

  declare content: string;
  declare debuggableCodeBlocks: boolean;

  private renderFrame = 0;
  private renderSpaceClearFrame = 0;
  private renderedContent = '';
  private renderedDebuggableCodeBlocks = false;

  constructor() {
    super();
    this.content = '';
    this.debuggableCodeBlocks = false;
  }

  connectedCallback(): void {
    super.connectedCallback();
    ensureMarkdownRendererStyles();
  }

  disconnectedCallback(): void {
    if (this.renderFrame) {
      globalThis.cancelAnimationFrame(this.renderFrame);
      this.renderFrame = 0;
    }

    if (this.renderSpaceClearFrame) {
      globalThis.cancelAnimationFrame(this.renderSpaceClearFrame);
      this.renderSpaceClearFrame = 0;
    }

    super.disconnectedCallback();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    return html`<div class="markdown-renderer__content" data-markdown-content></div>`;
  }

  protected firstUpdated(): void {
    this.scheduleMarkdownRender();
  }

  protected updated(changedProperties: PropertyValues<this>): void {
    super.updated(changedProperties);

    if (changedProperties.has('content') || changedProperties.has('debuggableCodeBlocks')) {
      this.scheduleMarkdownRender();
    }
  }

  private markdownContentElement(): HTMLElement | null {
    return this.querySelector<HTMLElement>(MARKDOWN_CONTENT_SELECTOR);
  }

  private scheduleMarkdownRender(): void {
    if (this.content === this.renderedContent && this.debuggableCodeBlocks === this.renderedDebuggableCodeBlocks) {
      return;
    }

    if (this.renderFrame) {
      globalThis.cancelAnimationFrame(this.renderFrame);
    }

    if (this.shouldReservePendingRenderSpace()) {
      this.reservePendingRenderSpace();
    }
    else {
      this.clearPendingRenderSpace();
    }

    this.renderFrame = globalThis.requestAnimationFrame(() => {
      this.renderFrame = 0;
      this.renderMarkdown();
    });
  }

  private renderMarkdown(): void {
    const target = this.markdownContentElement();

    if (!target) {
      return;
    }

    if (this.content === this.renderedContent && this.debuggableCodeBlocks === this.renderedDebuggableCodeBlocks) {
      return;
    }

    const blocks: CodeBlock[] = [];
    const marked = createMarkdown(blocks);
    const template = document.createElement('template');

    template.innerHTML = marked.parse(this.content || '') as string;

    synchronizeNodes(target, template.content);
    this.bindCodeBlocks(target, blocks);

    this.renderedContent = this.content;
    this.renderedDebuggableCodeBlocks = this.debuggableCodeBlocks;
    this.clearPendingRenderSpaceAfterLayout();
  }

  private shouldReservePendingRenderSpace(): boolean {
    const nextContent = this.content || '';
    const previousContent = this.renderedContent || '';

    if (!nextContent.trim()) {
      return false;
    }

    if (previousContent && nextContent.startsWith(previousContent)) {
      return false;
    }

    return true;
  }

  private reservePendingRenderSpace(): void {
    if (this.renderSpaceClearFrame) {
      globalThis.cancelAnimationFrame(this.renderSpaceClearFrame);
      this.renderSpaceClearFrame = 0;
    }

    const estimatedBlockSize = estimateMarkdownBlockSize(this.content || '');

    if (estimatedBlockSize < MIN_PENDING_RENDER_BLOCK_SIZE_PX) {
      this.clearPendingRenderSpace();
      return;
    }

    this.style.minBlockSize = `${estimatedBlockSize}px`;
    this.toggleAttribute('data-pending-render', true);
  }

  private clearPendingRenderSpaceAfterLayout(): void {
    if (this.renderSpaceClearFrame) {
      globalThis.cancelAnimationFrame(this.renderSpaceClearFrame);
    }

    this.renderSpaceClearFrame = globalThis.requestAnimationFrame(() => {
      this.renderSpaceClearFrame = 0;
      this.clearPendingRenderSpace();
    });
  }

  private clearPendingRenderSpace(): void {
    this.style.removeProperty('min-block-size');
    this.removeAttribute('data-pending-render');
  }

  private bindCodeBlocks(target: HTMLElement, blocks: CodeBlock[]): void {
    target.querySelectorAll<CodeBlockViewer>('code-block-viewer[data-code-block-index]').forEach((viewer) => {
      const index = Number(viewer.dataset.codeBlockIndex);
      const block = blocks[index];

      if (!block) {
        return;
      }

      viewer.value = block.code;
      viewer.lang = block.lang;
      viewer.debuggable = this.debuggableCodeBlocks;
    });
  }
}

if (!globalThis.customElements.get('markdown-renderer')) {
  globalThis.customElements.define('markdown-renderer', MarkdownRenderer);
}