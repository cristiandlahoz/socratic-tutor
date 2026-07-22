import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
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
    this.scrollToBottomVisible = !detail?.atBottom;
  };

  private readonly handleBusyChanged = (event: Event): void => {
    const detail = (event as CustomEvent<{ busy?: boolean }>).detail;
    this.responseBusy = Boolean(detail?.busy);
  };

  static readonly properties = {
    value: { type: String },
    promptLimit: { type: Number, attribute: 'prompt-limit' },
    composerEnabled: { type: Boolean, attribute: 'composer-enabled' },
    sendAvailable: { type: Boolean, attribute: 'send-available' },
    modelStatus: { type: String, attribute: 'model-status' },
    devResponseAvailable: { type: Boolean, attribute: 'dev-response-available' },
    devResponseEnabled: { type: Boolean, attribute: 'dev-response-enabled' },
    usageInputTokens: { type: Number, attribute: 'usage-input-tokens' },
    usagePercent: { type: Number, attribute: 'usage-percent' },
    conversationCompacted: { type: Boolean, attribute: 'conversation-compacted' },
    scrollToBottomVisible: { type: Boolean, attribute: 'scroll-to-bottom-visible' },
    responseBusy: { type: Boolean, attribute: 'response-busy' },
    activity: { type: String },
    allowEmptySubmit: { type: Boolean, attribute: 'allow-empty-submit' },
  };

  declare value: string;
  declare promptLimit: number;
  declare composerEnabled: boolean;
  declare sendAvailable: boolean;
  declare modelStatus: ModelStatus;
  declare devResponseAvailable: boolean;
  declare devResponseEnabled: boolean;
  declare usageInputTokens: number;
  declare usagePercent: number;
  declare conversationCompacted: boolean;
  declare scrollToBottomVisible: boolean;
  declare responseBusy: boolean;
  declare activity: ChatActivity;
  declare allowEmptySubmit: boolean;

  constructor() {
    super();
    this.value = '';
    this.promptLimit = 0;
    this.composerEnabled = true;
    this.sendAvailable = false;
    this.modelStatus = 'checking';
    this.devResponseAvailable = false;
    this.devResponseEnabled = false;
    this.usageInputTokens = -1;
    this.usagePercent = -1;
    this.conversationCompacted = false;
    this.scrollToBottomVisible = false;
    this.responseBusy = false;
    this.activity = 'idle';
    this.allowEmptySubmit = false;
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
      <div class="conversation-composer__input">
        <textarea
          class="conversation-composer__native-input"
          .value=${this.value}
          ?disabled=${this.inputDisabled()}
          maxlength=${this.promptLimit}
          placeholder=""
          aria-label="Escribe tu mensaje aquí"
          rows="1"
          @input=${this.handleInput}
          @keydown=${this.handleKeyDown}
        ></textarea>
      </div>
      <span class="conversation-composer__helper" aria-live="polite">${this.helperText()}</span>
      <div class="conversation-composer__status-group">
        <span class=${this.modelStatusClass()} aria-live="polite">${this.modelStatusLabel()}</span>
        ${this.renderDevResponseToggle()}
      </div>
      ${this.renderUsage()}
      <span class="conversation-composer__prompt-prefix" aria-hidden="true">~</span>
      ${this.renderSendControl()}
      ${renderConversationDisclaimer()}
    `;
  }

  protected updated(): void {
    this.resizeInput();
  }

  private handleInput(event: Event): void {
    const textarea = event.currentTarget as HTMLTextAreaElement;
    this.value = textarea.value;
    this.resizeInput(textarea);
  }

  private handleKeyDown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || !this.canSubmit()) {
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

  private resizeInput(textarea = this.querySelector<HTMLTextAreaElement>('.conversation-composer__native-input')): void {
    if (!textarea) {
      return;
    }

    textarea.style.height = 'auto';
    const maxHeight = Number.parseFloat(getComputedStyle(textarea).maxHeight);
    const nextHeight = Number.isFinite(maxHeight)
      ? Math.min(textarea.scrollHeight, maxHeight)
      : textarea.scrollHeight;

    textarea.style.height = `${nextHeight}px`;
    textarea.style.overflowY = Number.isFinite(maxHeight) && textarea.scrollHeight > maxHeight ? 'auto' : 'hidden';
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
        <vaadin-icon src="/icons/IconChevron.svg" aria-hidden="true"></vaadin-icon>
        <span>Bajar al final</span>
      </vaadin-button>
    `;
  }

  private canSubmit(): boolean {
    return this.composerEnabled
      && !this.busy()
      && this.sendAvailable
      && (this.allowEmptySubmit || this.value.trim().length > 0);
  }

  private inputDisabled(): boolean {
    return !this.composerEnabled && !this.busy();
  }

  private busy(): boolean {
    return this.responseBusy || this.activity === 'compacting';
  }

  private renderSendControl() {
    if (this.busy()) {
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
        <vaadin-icon src="/icons/IconArrowRightShort.svg" style="transform: rotate(-90deg);" aria-hidden="true"></vaadin-icon>
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

  private renderDevResponseToggle() {
    if (!this.devResponseAvailable) {
      return null;
    }

    const label = this.devResponseEnabled ? 'Desactivar modo de prueba' : 'Activar modo de prueba';
    return html`
      <button
        class="conversation-composer__test-toggle"
        type="button"
        title=${label}
        aria-label=${label}
        aria-pressed=${this.devResponseEnabled}
        ?disabled=${this.busy()}
        @click=${this.toggleDevResponse}
      >
        <vaadin-icon src="/icons/test-mode.svg" aria-hidden="true"></vaadin-icon>
      </button>
    `;
  }

  private readonly toggleDevResponse = (): void => {
    const enabled = !this.devResponseEnabled;
    this.devResponseEnabled = enabled;
    haptic('toggle');
    this.dispatchEvent(new CustomEvent('toggle-dev-response', {
      detail: { enabled },
      bubbles: true,
      composed: true,
    }));
  };

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
    if (this.devResponseEnabled) {
      return 'Modo de prueba';
    }
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
    return `conversation-composer__model-status is-${this.devResponseEnabled ? 'test' : this.modelStatus}`;
  }
}

customElements.define('conversation-composer', ConversationComposer);
