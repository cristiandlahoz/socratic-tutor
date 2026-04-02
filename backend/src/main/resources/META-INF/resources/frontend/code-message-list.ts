import './code-message-body.ts';
import '@vaadin/message-list/src/vaadin-message.js';
import { LitElement, html } from 'lit';
import { ifDefined } from 'lit/directives/if-defined.js';

type MessageItem = {
  text?: string;
  time?: string;
  userName?: string;
  userColorIndex?: number;
  className?: string;
  theme?: string;
};

function normalizeItems(items: unknown): MessageItem[] {
  if (typeof items === 'string') {
    return JSON.parse(items) as MessageItem[];
  }
  return Array.isArray(items) ? (items as MessageItem[]) : [];
}

class CodeMessageList extends LitElement {
  static properties = {
    items: { type: Array },
    markdown: { type: Boolean, reflect: true },
  };

  declare items: MessageItem[];
  declare markdown: boolean;

  constructor() {
    super();
    this.items = [];
    this.markdown = false;
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setItems(items: unknown): void {
    this.items = normalizeItems(items);
  }

  addItems(items: unknown): void {
    this.items = [...this.items, ...normalizeItems(items)];
  }

  setItemText(text: string, index: number): void {
    const nextItems = [...this.items];
    nextItems[index] = { ...nextItems[index], text };
    this.items = nextItems;
  }

  appendItemText(diff: string, index: number): void {
    const nextItems = [...this.items];
    const item = nextItems[index] ?? {};
    nextItems[index] = { ...item, text: `${item.text ?? ''}${diff ?? ''}` };
    this.items = nextItems;
  }

  protected render() {
    return html`
      <div part="list" role="list" class="code-message-list__list">
        ${this.items.map((item) => this.renderMessage(item))}
      </div>
    `;
  }

  private renderMessage(item: MessageItem) {
    return html`
      <vaadin-message
        role="listitem"
        .time=${item.time ?? ''}
        .userName=${item.userName ?? ''}
        .userColorIndex=${item.userColorIndex ?? 0}
        theme=${ifDefined(item.theme)}
        class=${ifDefined(item.className)}
      ><code-message-body .text=${item.text ?? ''} .markdown=${this.markdown}></code-message-body></vaadin-message>
    `;
  }
}

if (!customElements.get('code-message-list')) {
  customElements.define('code-message-list', CodeMessageList);
}
