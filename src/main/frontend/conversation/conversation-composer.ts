import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/text-area';
import { LitElement, html } from 'lit';

type ModelStatus = 'connected' | 'offline' | 'checking';

class ConversationComposer extends LitElement {
  static properties = {
    value: { type: String },
    promptLimit: { type: Number, attribute: 'prompt-limit' },
    composerEnabled: { type: Boolean, attribute: 'composer-enabled' },
    sendAvailable: { type: Boolean, attribute: 'send-available' },
    modelStatus: { type: String, attribute: 'model-status' },
  };

  declare value: string;
  declare promptLimit: number;
  declare composerEnabled: boolean;
  declare sendAvailable: boolean;
  declare modelStatus: ModelStatus;

  constructor() {
    super();
    this.value = '';
    this.promptLimit = 0;
    this.composerEnabled = true;
    this.sendAvailable = false;
    this.modelStatus = 'checking';
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    return html`
      <vaadin-text-area
        class="conversation-composer__input"
        .value=${this.value}
        ?disabled=${!this.composerEnabled}
        maxlength=${this.promptLimit}
        helper-text=${this.helperText()}
        placeholder=""
        aria-label="Escribe tu mensaje aquí"
        @value-changed=${this.handleValueChanged}
        @keydown=${this.handleKeyDown}
      ></vaadin-text-area>
      <span class=${this.modelStatusClass()} aria-live="polite">${this.modelStatusLabel()}</span>
      <span class="conversation-composer__prompt-prefix" aria-hidden="true">~</span>
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

  private canSubmit(): boolean {
    return this.composerEnabled && this.sendAvailable && this.value.trim().length > 0;
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
