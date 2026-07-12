import 'Frontend/shared/code/code-block-viewer.js';
import DOMPurify from 'dompurify';
import { LitElement, html, type PropertyValues } from 'lit';
import { Marked, type HooksObject, type RendererObject, type TokenizerAndRendererExtension, type Tokens } from 'marked';
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

const markExtension: TokenizerAndRendererExtension = {
  name: 'mark',
  level: 'inline',
  start(src: string): number | void {
    return src.indexOf('==');
  },
  tokenizer(src: string): Tokens.Generic | undefined {
    const match = /^==(?=\S)([\s\S]*?\S)==(?![=])/.exec(src);

    if (!match) {
      return undefined;
    }

    return {
      type: 'mark',
      raw: match[0],
      text: match[1],
      tokens: this.lexer.inlineTokens(match[1]),
    };
  },
  renderer(token: Tokens.Generic): string {
    const tokens = Array.isArray(token.tokens) ? token.tokens : [];

    return `<mark>${this.parser.parseInline(tokens)}</mark>`;
  },
  childTokens: ['tokens'],
};

function ensureMarkdownRendererStyles(): void {
  if (document.getElementById(MARKDOWN_RENDERER_STYLE_ID)) {
    return;
  }

  const style = document.createElement('style');
  style.id = MARKDOWN_RENDERER_STYLE_ID;
  style.textContent = `
    markdown-renderer {
      display: block;
      width: 100%;
      min-width: 0;
      color: var(--markdown-renderer-text-color, var(--message-prose-color, var(--vaadin-text-color)));
      overflow-wrap: break-word;
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

    markdown-renderer code-block-viewer {
      display: block;
      width: 100%;
      margin-block: var(--typeset-flow);
    }
  `;

  document.head.appendChild(style);
}

function sanitizeMarkdownHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['data-code-block-index', 'data-not-typeset'],
    CUSTOM_ELEMENT_HANDLING: {
      tagNameCheck: (tagName) => tagName === 'code-block-viewer',
      attributeNameCheck: (attributeName) => attributeName === 'data-code-block-index' || attributeName === 'data-not-typeset',
    },
  }).replace(/\r?\n$/, '');
}

function parseLanguage(info: string | undefined): string {
  return info?.trim().split(/\s+/)[0] ?? '';
}

function createMarkdown(blocks: CodeBlock[]): Marked {
  const renderer: RendererObject = {
    code(token: Tokens.Code): string {
      const index = blocks.length;

      blocks.push({
        code: token.text,
        lang: parseLanguage(token.lang),
      });

      return `<code-block-viewer data-code-block-index="${index}" data-not-typeset></code-block-viewer>`;
    },
  };
  const hooks: HooksObject = {
    postprocess: sanitizeMarkdownHtml,
  };

  return new Marked({
    gfm: true,
    breaks: true,
    extensions: [markExtension],
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
    return html`<div class="markdown-renderer__content typeset typeset-chat" data-markdown-content></div>`;
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
    const markdown = createMarkdown(blocks);
    const template = document.createElement('template');
    template.innerHTML = markdown.parse(this.content || '') as string;

    synchronizeNodes(target, template.content);

    for (const viewer of target.querySelectorAll<CodeBlockViewer>('code-block-viewer[data-code-block-index]')) {
      const block = blocks[Number(viewer.dataset.codeBlockIndex)];

      if (!block) {
        continue;
      }

      viewer.value = block.code;
      viewer.lang = block.lang;
      viewer.debuggable = this.debuggableCodeBlocks;
    }

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
}

if (!globalThis.customElements.get('markdown-renderer')) {
  globalThis.customElements.define('markdown-renderer', MarkdownRenderer);
}
