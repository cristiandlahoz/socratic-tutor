import './code-block-viewer.tsx';
import '@vaadin/markdown/src/vaadin-markdown.js';
import { LitElement, html, nothing } from 'lit';

const CODE_MESSAGE_BODY_STYLE_ID = 'code-message-body-styles';

function ensureCodeMessageBodyStyles(): void {
  if (document.getElementById(CODE_MESSAGE_BODY_STYLE_ID)) {
    return;
  }

  const style = document.createElement('style');
  style.id = CODE_MESSAGE_BODY_STYLE_ID;
  style.textContent = `
    code-message-body {
      display: block;
      color: inherit;
    }

    code-message-body .code-message-body__content {
      min-width: 0;
    }

    code-message-body .segment {
      display: block;
      min-width: 0;
    }

    code-message-body code-block-viewer {
      display: block;
      width: 100%;
      overflow: visible;
      border: 0;
      border-radius: 0;
      box-shadow: none;
      background: transparent;
      backdrop-filter: none;
      font-size: 0.75rem;
      line-height: 1rem;
    }

    code-message-body vaadin-markdown {
      display: block;
      color: inherit;
    }

    code-message-body code-block-viewer + vaadin-markdown {
      margin-top: 0.75rem;
    }

    code-message-body :where(vaadin-markdown > :is(h1, h2, h3, h4, h5, h6, p, ul, ol, hr, blockquote, pre):first-child) {
      margin-top: 0;
    }

    code-message-body :where(vaadin-markdown > :is(h1, h2, h3, h4, h5, h6, p, ul, ol, hr, blockquote, pre):last-child) {
      margin-bottom: 0;
    }
  `;
  document.head.appendChild(style);
}

type Segment =
  | { type: 'prose'; content: string }
  | { type: 'code'; content: string; lang: string };

function parseSegments(text: string): Segment[] {
  const normalized = text ?? '';
  const fencePattern = /```([\w#+.-]*)\n([\s\S]*?)```/g;
  const segments: Segment[] = [];
  let cursor = 0;

  for (const match of normalized.matchAll(fencePattern)) {
    const start = match.index ?? 0;
    if (start > cursor) {
      segments.push({ type: 'prose', content: normalized.slice(cursor, start) });
    }
    const content = (match[2] ?? '').replace(/\n$/, '');
    segments.push({
      type: 'code',
      lang: match[1] ?? '',
      content,
    });
    cursor = start + match[0].length;
  }

  if (cursor < normalized.length) {
    segments.push({ type: 'prose', content: normalized.slice(cursor) });
  }

  return segments.length > 0 ? segments : [{ type: 'prose', content: normalized }];
}

class CodeMessageBody extends LitElement {
  static properties = {
    text: { type: String },
    markdown: { type: Boolean },
    debuggableCodeBlocks: { type: Boolean, attribute: 'debuggable-code-blocks' },
  };

  declare text: string;
  declare markdown: boolean;
  declare debuggableCodeBlocks: boolean;

  constructor() {
    super();
    this.text = '';
    this.markdown = false;
    this.debuggableCodeBlocks = false;
  }

  connectedCallback(): void {
    super.connectedCallback();
    ensureCodeMessageBodyStyles();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    const segments = parseSegments(this.text ?? '');
    return html`<div class="code-message-body__content">${segments.map((segment) => this.renderSegment(segment))}</div>`;
  }

  private renderSegment(segment: Segment) {
    if (segment.type === 'code') {
      return html`<code-block-viewer
        class="segment"
        .value=${segment.content}
        .lang=${segment.lang}
        .debuggable=${this.debuggableCodeBlocks}
      ></code-block-viewer>`;
    }

    if (!segment.content.trim()) {
      return nothing;
    }

    if (this.markdown) {
      return html`<vaadin-markdown class="segment" .content=${segment.content}></vaadin-markdown>`;
    }

    return html`<div class="segment">${segment.content}</div>`;
  }
}

if (!customElements.get('code-message-body')) {
  customElements.define('code-message-body', CodeMessageBody);
}
