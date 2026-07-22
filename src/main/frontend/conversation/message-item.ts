import './markdown-renderer.js';
import { ensureDocumentStyle } from 'Frontend/shared/dom-utils.js';
import { LitElement, html } from 'lit';
import type { BrailleSpinnerName } from './braille-spinners.js';
import { resolveLoadingLabel } from './message-item-loading-label.js';

const MESSAGE_ITEM_STYLE_ID = 'message-item-styles';

type MessageVariant = 'user' | 'assistant';

function ensureMessageItemStyles(): void {
  ensureDocumentStyle(MESSAGE_ITEM_STYLE_ID, `
    message-item {
      --message-item-font-size-base: var(--aura-font-size-m, 13px);
      --message-item-font-size: var(--message-font-size, var(--message-item-font-size-base));
      --message-item-font-weight: var(--message-font-weight, var(--aura-font-weight-regular, 400));
      --message-item-line-height: var(--message-line-height, var(--aura-line-height-m, 1.5));
      --message-item-user-max-inline-size: min(31.65rem, 100%);
      --message-item-assistant-max-inline-size: min(50rem, 100%);
      --message-item-user-padding-block: var(--vaadin-padding-xs);
      --message-item-user-padding-inline: var(--vaadin-padding-s);
      --message-item-user-background: var(--message-user-surface, var(--aura-surface-color));
      --message-item-user-border-color: var(--message-user-border, var(--vaadin-border-color));
      --message-item-user-shadow: var(--message-user-shadow, var(--aura-shadow-s));
      --message-item-assistant-color: var(--message-assistant-text-color);

      box-sizing: border-box;
      display: block;
      min-width: 0;
      color: var(--message-item-assistant-color);
      font-size: var(--message-item-font-size);
      font-weight: var(--message-item-font-weight);
      line-height: var(--message-item-line-height);
    }

    message-item[variant="assistant"] {
      max-inline-size: var(--message-item-assistant-max-inline-size);
    }

    message-item[variant="user"] {
      inline-size: fit-content;
      max-inline-size: var(--message-item-user-max-inline-size);
      margin-inline-start: auto;
      padding: var(--message-item-user-padding-block) var(--message-item-user-padding-inline);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--message-item-user-border-color);
      border-radius: var(--vaadin-radius-m);
      background: var(--message-item-user-background);
      box-shadow: var(--message-item-user-shadow);
      color: var(--vaadin-text-color);
      backdrop-filter: blur(18px) saturate(1.05);
    }

    message-item[variant="user"][steered] {
      animation: message-item-steered-swap 220ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    message-item[variant="user"][steered] markdown-renderer {
      animation: message-item-steered-content 220ms cubic-bezier(0.22, 1, 0.36, 1);
    }

    message-item[variant="user"] markdown-renderer {
      --markdown-renderer-text-color: var(--vaadin-text-color);
      inline-size: fit-content;
      max-inline-size: 100%;
      white-space: pre-wrap;
    }

    message-item[variant="assistant"] markdown-renderer {
      --markdown-renderer-text-color: var(--message-item-assistant-color);
    }

    message-item[loading] {
      inline-size: fit-content;
      min-height: auto;
      color: var(--vaadin-text-color-disabled);
      font-family: var(--aura-font-family);
      letter-spacing: 0;
    }

    message-item[loading] .message-item__loading {
      display: inline-flex;
      align-items: center;
      gap: var(--vaadin-gap-s);
      min-width: 0;
    }

    message-item[loading] solving-orb {
      display: block;
      flex: none;
    }

    message-item[loading] .message-item__loading-label {
      color: var(--aura-neutral);
      font-family: var(--aura-font-family);
      font-size: var(--aura-font-size-s);
      font-weight: var(--aura-font-weight-medium);
      line-height: 1.2;
      white-space: nowrap;
    }

    message-item code,
    message-item pre,
    message-item kbd,
    message-item samp {
      font-family: var(--aura-font-family);
    }

    message-item a {
      color: var(--aura-accent-text-color);
      text-decoration-color: color-mix(in srgb, var(--aura-accent-text-color) 35%, transparent);
      transition: text-decoration-color var(--motion-fast);
    }

    message-item a:hover {
      text-decoration-color: var(--aura-accent-text-color);
    }

    @media (max-width: 960px) {
      message-item[variant="user"] {
        max-inline-size: 100%;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      message-item[variant="user"][steered],
      message-item[variant="user"][steered] markdown-renderer {
        animation: none;
      }
    }

    @keyframes message-item-steered-swap {
      0% {
        background: color-mix(in srgb, var(--aura-accent-color) 12%, var(--message-item-user-background));
        transform: translateY(0.125rem);
      }
      100% {
        background: var(--message-item-user-background);
        transform: translateY(0);
      }
    }

    @keyframes message-item-steered-content {
      0% {
        opacity: 0;
        filter: blur(2px);
      }
      100% {
        opacity: 1;
        filter: blur(0);
      }
    }
  `);
}

class MessageItem extends LitElement {
  static properties = {
    text: { type: String },
    time: { type: String },
    userName: { type: String, attribute: 'user-name' },
    variant: { type: String, reflect: true },
    loading: { type: Boolean, reflect: true },
    steered: { type: Boolean, reflect: true },
    thinkingSpinner: { type: String, attribute: 'thinking-spinner' },
    loadingLabel: { type: String, attribute: 'loading-label' },
    debuggableCodeBlocks: { type: Boolean, attribute: 'debuggable-code-blocks' },
  };

  declare text: string;
  declare time: string;
  declare userName: string;
  declare variant: MessageVariant;
  declare loading: boolean;
  declare steered: boolean;
  declare thinkingSpinner: BrailleSpinnerName;
  declare loadingLabel: string;
  declare debuggableCodeBlocks: boolean;

  constructor() {
    super();
    this.text = '';
    this.time = '';
    this.userName = '';
    this.variant = 'assistant';
    this.loading = false;
    this.steered = false;
    this.debuggableCodeBlocks = false;
    this.thinkingSpinner = 'braille';
    this.loadingLabel = 'Generando respuesta';
  }

  connectedCallback(): void {
    super.connectedCallback();
    ensureMessageItemStyles();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected willUpdate(): void {
    this.setAttribute('aria-label', this.accessibleLabel());
  }

  protected render() {
    if (this.loading) {
      return html`
        <span class="message-item__loading">
          <solving-orb></solving-orb>
          <span class="message-item__loading-label">${resolveLoadingLabel(this.loadingLabel)}</span>
        </span>
      `;
    }

      return html`<markdown-renderer
        .content=${this.text ?? ''}
        .debuggableCodeBlocks=${this.debuggableCodeBlocks}
      ></markdown-renderer>`;
  }

  private accessibleLabel(): string {
    if (this.loading) {
      return resolveLoadingLabel(this.loadingLabel);
    }

    const author = this.userName?.trim() || 'Mensaje';
    const time = this.time?.trim();
    return time ? `${author}, ${time}` : author;
  }
}

if (!customElements.get('message-item')) {
  customElements.define('message-item', MessageItem);
}
