import './conversation-markdown.ts';
import { LitElement, html } from 'lit';

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

    code-message-body .segment--plain {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      font-size: var(--message-font-size);
      line-height: var(--message-line-height);
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
      font-size: var(--message-font-size);
      line-height: var(--message-line-height);
    }
  `;
  document.head.appendChild(style);
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
    return html`<div class="code-message-body__content">${this.renderContent()}</div>`;
  }

  private renderContent() {
    if (this.markdown) {
      return html`<conversation-markdown
        class="segment"
        .content=${this.text ?? ''}
        .debuggableCodeBlocks=${this.debuggableCodeBlocks}
      ></conversation-markdown>`;
    }

    return html`<div class="segment segment--plain">${this.text ?? ''}</div>`;
  }
}

if (!customElements.get('code-message-body')) {
  customElements.define('code-message-body', CodeMessageBody);
}
