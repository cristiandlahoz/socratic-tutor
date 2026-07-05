import '../shared/code/code-block-viewer.tsx';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { LitElement } from 'lit';

const MARKDOWN_STYLE_ID = 'conversation-markdown-styles';
const RAW_TEXT_CONTAINERS = new Set(['CODE', 'PRE', 'TEXTAREA']);

type CodeBlockViewer = HTMLElement & {
  value: string;
  lang: string;
  debuggable: boolean;
};

function ensureConversationMarkdownStyles(): void {
  if (document.getElementById(MARKDOWN_STYLE_ID)) {
    return;
  }

  const style = document.createElement('style');
  style.id = MARKDOWN_STYLE_ID;
  style.textContent = `
    conversation-markdown {
      --conversation-markdown-font-size: var(--message-font-size, var(--aura-font-size-m, 14px));
      --conversation-markdown-line-height: var(--message-line-height, var(--aura-line-height-m, 1.5));
      --conversation-markdown-font-weight: var(--message-font-weight, var(--aura-font-weight-regular, 400));
      --conversation-markdown-text-color: var(--message-prose-color, var(--vaadin-text-color, currentColor));
      --conversation-markdown-heading-color: var(--vaadin-text-color, currentColor);
      --conversation-markdown-heading-weight: var(--markdown-heading-font-weight, var(--aura-font-weight-semibold, 600));
      --conversation-markdown-h1-size: 22.75px;
      --conversation-markdown-h2-size: 19.5px;
      --conversation-markdown-h3-size: 16.9px;
      --conversation-markdown-heading-spacing: -0.018em;
      --conversation-markdown-list-padding: var(--vaadin-padding-xl, 1rem);
      --conversation-markdown-block-gap: var(--vaadin-gap-xs, 0.25rem);

      display: block;
      color: var(--conversation-markdown-text-color);
      font-size: var(--conversation-markdown-font-size);
      font-weight: var(--conversation-markdown-font-weight);
      line-height: var(--conversation-markdown-line-height);
    }

    conversation-markdown :is(p, ul, ol, li, blockquote, table, figure, hr, h1, h2, h3, h4, h5, h6) {
      margin: 0;
      margin-block: 0;
    }

    conversation-markdown ol > li::marker {
      font-family: sans-serif;
    }

    conversation-markdown :is(p, ul, ol, li, blockquote) {
      color: var(--conversation-markdown-text-color);
      line-height: var(--conversation-markdown-line-height);
    }

    conversation-markdown :is(h1, h2, h3, h4, h5, h6) {
      color: var(--conversation-markdown-heading-color);
      font-weight: var(--conversation-markdown-heading-weight);
      line-height: 1.22;
      letter-spacing: var(--conversation-markdown-heading-spacing);
      text-wrap: balance;
    }

    conversation-markdown h1 {
      font-size: var(--conversation-markdown-h1-size);
    }

    conversation-markdown h2 {
      font-size: var(--conversation-markdown-h2-size);
    }

    conversation-markdown h3 {
      font-size: var(--conversation-markdown-h3-size);
    }

    conversation-markdown :is(h4, h5, h6) {
      font-size: var(--conversation-markdown-font-size);
    }

    conversation-markdown strong {
      color: var(--conversation-markdown-heading-color);
      font-weight: var(--conversation-markdown-heading-weight);
    }

    conversation-markdown :is(ul, ol) {
      padding-left: var(--conversation-markdown-list-padding);
    }

    conversation-markdown li + li,
    conversation-markdown p + p {
      margin-top: var(--conversation-markdown-block-gap);
    }

    conversation-markdown code-block-viewer {
      display: block;
      margin-block: var(--conversation-markdown-block-gap);
      width: 100%;
    }
  `;
  document.head.appendChild(style);
}

function pruneFormattingWhitespace(node: Node): void {
  if (node.nodeType === Node.ELEMENT_NODE && RAW_TEXT_CONTAINERS.has((node as Element).tagName)) {
    return;
  }

  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === Node.TEXT_NODE && child.textContent?.trim() === '') {
      child.remove();
      continue;
    }
    pruneFormattingWhitespace(child);
  }
}

function languageFromCodeElement(code: Element): string {
  const languageClass = Array.from(code.classList).find((className) => className.startsWith('language-'));
  return languageClass?.replace(/^language-/, '') ?? '';
}

function upgradeCodeFences(fragment: DocumentFragment, debuggableCodeBlocks: boolean): void {
  for (const pre of Array.from(fragment.querySelectorAll('pre'))) {
    const code = pre.querySelector(':scope > code');
    if (!code) {
      continue;
    }

    const viewer = document.createElement('code-block-viewer') as CodeBlockViewer;
    viewer.value = (code.textContent ?? '').replace(/\n$/, '');
    viewer.lang = languageFromCodeElement(code);
    viewer.debuggable = debuggableCodeBlocks;
    pre.replaceWith(viewer);
  }
}

class ConversationMarkdown extends LitElement {
  static properties = {
    content: { type: String },
    debuggableCodeBlocks: { type: Boolean, attribute: 'debuggable-code-blocks' },
  };

  declare content: string;
  declare debuggableCodeBlocks: boolean;

  constructor() {
    super();
    this.content = '';
    this.debuggableCodeBlocks = false;
  }

  connectedCallback(): void {
    super.connectedCallback();
    ensureConversationMarkdownStyles();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected updated(changedProperties: Map<string, unknown>): void {
    super.updated(changedProperties);
    if (changedProperties.has('content') || changedProperties.has('debuggableCodeBlocks')) {
      this.renderMarkdown();
    }
  }

  private renderMarkdown(): void {
    const template = document.createElement('template');
    template.innerHTML = DOMPurify.sanitize(marked.parse(this.content || '') as string, {
      CUSTOM_ELEMENT_HANDLING: {
        tagNameCheck: () => true,
      },
    });
    upgradeCodeFences(template.content, this.debuggableCodeBlocks);
    pruneFormattingWhitespace(template.content);
    this.replaceChildren(template.content);
  }
}

if (!customElements.get('conversation-markdown')) {
  customElements.define('conversation-markdown', ConversationMarkdown);
}
