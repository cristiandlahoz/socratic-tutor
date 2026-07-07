import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/text-area';
import './braille-spinner.js';
import { haptic } from 'Frontend/shared/haptics.js';
import { LitElement, html } from 'lit';
import { renderConversationDisclaimer } from './conversation-disclaimer.js';

type ModelStatus = 'connected' | 'offline' | 'checking';
type ChatActivity = 'idle' | 'generating' | 'compacting';

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
    usageInputTokens: { type: Number, attribute: 'usage-input-tokens' },
    usagePercent: { type: Number, attribute: 'usage-percent' },
    conversationCompacted: { type: Boolean, attribute: 'conversation-compacted' },
    scrollToBottomVisible: { type: Boolean, attribute: 'scroll-to-bottom-visible' },
    responseBusy: { type: Boolean, attribute: 'response-busy' },
    activity: { type: String },
  };

  declare value: string;
  declare promptLimit: number;
  declare composerEnabled: boolean;
  declare sendAvailable: boolean;
  declare modelStatus: ModelStatus;
  declare usageInputTokens: number;
  declare usagePercent: number;
  declare conversationCompacted: boolean;
  declare scrollToBottomVisible: boolean;
  declare responseBusy: boolean;
  declare activity: ChatActivity;

  constructor() {
    super();
    this.value = '';
    this.promptLimit = 0;
    this.composerEnabled = true;
    this.sendAvailable = false;
    this.modelStatus = 'checking';
    this.usageInputTokens = -1;
    this.usagePercent = -1;
    this.conversationCompacted = false;
    this.scrollToBottomVisible = false;
    this.responseBusy = false;
    this.activity = 'idle';
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
      ${this.renderUsage()}
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

    const prompt = this.value.trim();
    this.value = '';
    haptic('messageSent');

    this.dispatchEvent(new CustomEvent('submit-prompt', {
      detail: { prompt },
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
          aria-label=${this.busyLabel()}
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

  private busyLabel(): string {
    if (this.activity === 'compacting') {
      return 'Compactando el contexto…';
    }
    return 'Generando respuesta…';
  }

  private helperText(): string {
    return `${this.value.length}/${this.promptLimit} caracteres`;
  }

  private renderUsage() {
    if (!this.usageVisible()) {
      return null;
    }

    return html`
      <span
        class="conversation-composer__usage"
        data-tooltip=${this.usageTooltip()}
        aria-label=${this.usageTooltip()}
      >${this.usageLabel()}</span>
    `;
  }

  private usageVisible(): boolean {
    return (this.usageInputTokens >= 0 && this.usagePercent >= 0) || this.conversationCompacted;
  }

  private usageLabel(): string {
    if (this.usageInputTokens >= 0 && this.usagePercent >= 0) {
      return `${this.formatTokenCount(this.usageInputTokens)} (${this.usagePercent}%)`;
    }
    return 'Contexto compactado';
  }

  private usageTooltip(): string {
    const usageDescription = 'Tokens de entrada del contexto activo y porcentaje usado respecto al umbral de compactación.';
    if (this.conversationCompacted) {
      return `${usageDescription} Historial resumido para el contexto activo.`;
    }
    return usageDescription;
  }

  private formatTokenCount(tokens: number): string {
    if (tokens >= 1_000_000) {
      return `${this.compact(tokens / 1_000_000)}M`;
    }
    if (tokens >= 1_000) {
      return `${this.compact(tokens / 1_000)}K`;
    }
    return String(tokens);
  }

  private compact(value: number): string {
    const rounded = Math.round(value * 10) / 10;
    return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
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
