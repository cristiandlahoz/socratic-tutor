import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/text-area';
import './braille-spinner.js';
import { LitElement, html } from 'lit';
import { renderConversationDisclaimer } from './conversation-disclaimer.js';

type ModelStatus = 'connected' | 'offline' | 'checking';

class ConversationComposer extends LitElement {
  private scrollPane: HTMLElement | null = null;

  private readonly handleBottomStateChanged = (event: Event): void => {
    const detail = (event as CustomEvent<{ atBottom?: boolean }>).detail;
    this.scrollToBottomVisible = !Boolean(detail?.atBottom);
  };

  private readonly handleBusyChanged = (event: Event): void => {
    const detail = (event as CustomEvent<{ busy?: boolean }>).detail;
    this.responseBusy = Boolean(detail?.busy);
  };

  static properties = {
    value: { type: String },
    promptLimit: { type: Number, attribute: 'prompt-limit' },
    composerEnabled: { type: Boolean, attribute: 'composer-enabled' },
    sendAvailable: { type: Boolean, attribute: 'send-available' },
    modelStatus: { type: String, attribute: 'model-status' },
    scrollToBottomVisible: { type: Boolean, attribute: 'scroll-to-bottom-visible' },
    responseBusy: { type: Boolean, attribute: 'response-busy' },
  };

  declare value: string;
  declare promptLimit: number;
  declare composerEnabled: boolean;
  declare sendAvailable: boolean;
  declare modelStatus: ModelStatus;
  declare scrollToBottomVisible: boolean;
  declare responseBusy: boolean;

  constructor() {
    super();
    this.value = '';
    this.promptLimit = 0;
    this.composerEnabled = true;
    this.sendAvailable = false;
    this.modelStatus = 'checking';
    this.scrollToBottomVisible = false;
    this.responseBusy = false;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.attachScrollPane();
  }

  disconnectedCallback(): void {
    this.detachScrollPane();
    super.disconnectedCallback();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    return html`
      ${this.renderScrollToBottomButton()}
      <vaadin-text-area
        class="conversation-composer__input"
        .value=${this.value}
        ?disabled=${this.inputDisabled()}
        maxlength=${this.promptLimit}
        helper-text=${this.helperText()}
        placeholder=""
        aria-label="Escribe tu mensaje aquí"
        @value-changed=${this.handleValueChanged}
        @keydown=${this.handleKeyDown}
      ></vaadin-text-area>
      <span class=${this.modelStatusClass()} aria-live="polite">${this.modelStatusLabel()}</span>
      <span class="conversation-composer__prompt-prefix" aria-hidden="true">~</span>
      ${this.renderSendControl()}
      ${renderConversationDisclaimer()}
    `;
  }

  private handleValueChanged(event: CustomEvent<{ value?: string }>): void {
    this.value = event.detail.value ?? '';
  }

  private handleKeyDown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || !this.canSubmit()) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    this.submit();
  }

  private submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.dispatchEvent(new CustomEvent('submit-prompt', {
      detail: { prompt: this.value.trim() },
      bubbles: true,
      composed: true,
    }));
  }

  private scrollToBottom(): void {
    this.closest('.conversation-view__pane')
      ?.querySelector<HTMLElement & { scrollToBottom?: () => void }>('messages-list')
      ?.scrollToBottom?.();
  }

  private attachScrollPane(): void {
    const pane = this.closest<HTMLElement>('.conversation-view__pane');

    if (pane === this.scrollPane) {
      return;
    }

    this.detachScrollPane();
    this.scrollPane = pane;
    this.scrollPane?.addEventListener('bottom-state-changed', this.handleBottomStateChanged);
    this.scrollPane?.addEventListener('conversation-busy-changed', this.handleBusyChanged);
  }

  private detachScrollPane(): void {
    this.scrollPane?.removeEventListener('bottom-state-changed', this.handleBottomStateChanged);
    this.scrollPane?.removeEventListener('conversation-busy-changed', this.handleBusyChanged);
    this.scrollPane = null;
  }

  private renderScrollToBottomButton() {
    if (!this.scrollToBottomVisible) {
      return null;
    }

    return html`
      <vaadin-button
        class="conversation-composer__scroll-bottom-button"
        aria-label="Bajar al final de la conversación"
        @click=${this.scrollToBottom}
      >
        <vaadin-icon icon="vaadin:angle-down"></vaadin-icon>
        <span>Bajar al final</span>
      </vaadin-button>
    `;
  }

  private canSubmit(): boolean {
    return this.composerEnabled && !this.responseBusy && this.sendAvailable && this.value.trim().length > 0;
  }

  private inputDisabled(): boolean {
    return !this.composerEnabled && !this.responseBusy;
  }

  private renderSendControl() {
    if (this.responseBusy) {
      return html`
        <span
          class="conversation-composer__send-spinner"
          aria-label="Generando respuesta"
          aria-live="polite"
          role="status"
        >
          <braille-spinner spinner="braille"></braille-spinner>
        </span>
      `;
    }

    return html`
      <vaadin-button
        class="conversation-composer__send-button"
        aria-label="Enviar mensaje"
        ?disabled=${!this.canSubmit()}
        @click=${this.submit}
      >
        <vaadin-icon icon="vaadin:arrow-up"></vaadin-icon>
      </vaadin-button>
    `;
  }

  private helperText(): string {
    return `${this.value.length}/${this.promptLimit} caracteres`;
  }

  private modelStatusLabel(): string {
    switch (this.modelStatus) {
      case 'connected':
        return 'Connected';
      case 'offline':
        return 'Offline';
      case 'checking':
      default:
        return 'Checking';
    }
  }

  private modelStatusClass(): string {
    return `conversation-composer__model-status is-${this.modelStatus}`;
  }
}

customElements.define('conversation-composer', ConversationComposer);
