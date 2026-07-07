import './c-debug-source-viewer.js';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/popover';
import '@vaadin/text-area';
import { LitElement, html, nothing } from 'lit';
import { repeat } from 'lit/directives/repeat.js';

type CompilerDiagnostic = {
  severity?: 'ERROR' | 'WARNING' | 'INFO' | string;
  message?: string;
  line?: number | null;
  column?: number | null;
  endLine?: number | null;
  endColumn?: number | null;
  fromOffset?: number | null;
  toOffset?: number | null;
  ruleId?: string | null;
};

type DebugVariable = {
  name: string;
  value: string;
  scope: string;
};

type DebugSourceViewerElement = HTMLElement & {
  value: string;
  lang: string;
  diagnostics: CompilerDiagnostic[] | string;
  activeLine: number;
  editable: boolean;
};

function normalizeDiagnostics(value: unknown): CompilerDiagnostic[] {
  const parsed = typeof value === 'string' ? JSON.parse(value) as unknown : value;
  return Array.isArray(parsed) ? parsed.map(normalizeDiagnostic) : [];
}

function normalizeVariables(value: unknown): DebugVariable[] {
  const parsed = typeof value === 'string' ? JSON.parse(value) as unknown : value;
  return Array.isArray(parsed) ? parsed.map(normalizeVariable) : [];
}

function normalizeDiagnostic(value: unknown): CompilerDiagnostic {
  return isRecord(value) ? { ...value } : {};
}

function normalizeVariable(value: unknown): DebugVariable {
  const source = isRecord(value) ? value : {};
  return {
    name: stringValue(source.name),
    value: stringValue(source.value),
    scope: stringValue(source.scope) || 'local',
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

class CDebuggerPanel extends LitElement {
  static readonly properties = {
    activeLine: { type: Number, attribute: 'active-line' },
    controlsEnabled: { type: Boolean, attribute: 'controls-enabled' },
    diagnostics: { type: Array },
    editable: { type: Boolean },
    lang: { type: String },
    locals: { type: Array },
    source: { type: String },
    statusText: { type: String, attribute: 'status-text' },
    stdin: { type: String },
    stdout: { type: String },
  };

  declare activeLine: number;
  declare controlsEnabled: boolean;
  declare diagnostics: CompilerDiagnostic[];
  declare editable: boolean;
  declare lang: string;
  declare locals: DebugVariable[];
  declare source: string;
  declare statusText: string;
  declare stdin: string;
  declare stdout: string;

  constructor() {
    super();
    this.activeLine = 0;
    this.controlsEnabled = true;
    this.diagnostics = [];
    this.editable = true;
    this.lang = 'c';
    this.locals = [];
    this.source = '';
    this.statusText = 'Pega codigo C o abre un ejemplo del asistente para visualizar la ejecucion.';
    this.stdin = '';
    this.stdout = '';
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setSource(value: string | null | undefined): void {
    this.source = value ?? '';
  }

  setDiagnostics(value: unknown): void {
    this.diagnostics = normalizeDiagnostics(value);
  }

  setLocals(value: unknown): void {
    this.locals = normalizeVariables(value);
  }

  protected render() {
    return html`
      <div class="c-runner-scroll-shell">
        <div class="c-runner-panel">
          <div class="c-runner-header">
            <button class="c-runner-panel-toggle" type="button" title="Ocultar depurador" @click=${this.closePanel}>
              <vaadin-icon icon="vaadin:angle-right"></vaadin-icon>
            </button>
            <h2 class="c-runner-title">Depurador Visual</h2>
          </div>
          ${this.renderStateCard()}
          <span class="c-runner-status-text">${this.statusText}</span>
          <div class="c-runner-code-frame">
            <div class="c-runner-viewer-shell">
              <c-debug-source-viewer
                class="c-runner-source-viewer"
                .value=${this.source}
                .lang=${this.lang}
                .diagnostics=${this.diagnostics}
                .activeLine=${this.activeLine}
                .editable=${this.editable}
                @value-changed=${this.handleSourceChanged}
              ></c-debug-source-viewer>
            </div>
          </div>
          ${this.renderTerminalCard()}
        </div>
      </div>
    `;
  }

  private renderStateCard() {
    return html`
      <div class="c-runner-state-card">
        <div class="c-runner-state-header">
          <div class="c-runner-state-heading">
            <span class="c-runner-state-title">Estado</span>
            <span class="c-runner-state-pill">${this.locals.length} vars</span>
          </div>
          ${this.renderControls()}
        </div>
        <div class="c-runner-state-body">
          <div class="c-runner-vars-scroll">
            <table class="c-runner-vars-table">
              <thead>
                <tr>
                  <th class="c-runner-col-name">Name</th>
                  <th class="c-runner-col-value">Value</th>
                </tr>
              </thead>
              <tbody>
                ${repeat(this.locals, (variable) => variable.name, (variable) => this.renderVariableRow(variable))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;
  }

  private renderVariableRow(variable: DebugVariable) {
    const popoverId = `c-runner-value-${variable.name.replaceAll(/[^a-zA-Z0-9_-]/g, '-')}`;
    const value = variable.value || '(empty)';
    return html`
      <tr>
        <td class="c-runner-col-name">
          <span class="c-runner-var-name">${variable.name}</span>
        </td>
        <td class="c-runner-col-value">
          <span
            id=${popoverId}
            class="c-runner-var-value"
            title=${variable.value}
            tabindex="0"
            role="button"
            aria-label=${`Ver valor completo de ${variable.name}`}
          >
            ${variable.value}
          </span>
          <vaadin-popover class="c-runner-value-popover" for=${popoverId}>
            <span class="c-runner-value-popover-title">${variable.name || 'value'}</span>
            <pre class="c-runner-value-popover-content">${value}</pre>
          </vaadin-popover>
        </td>
      </tr>
    `;
  }

  private renderControls() {
    return html`
      <div class="c-runner-controls">
        ${this.renderControlButton('vaadin:play', 'Ejecutar depuracion', 'c-runner-validate-button', this.validateDebug)}
        ${this.renderControlButton('vaadin:arrow-right', 'Paso siguiente', '', this.stepDebug)}
        ${this.renderControlButton('vaadin:rotate-left', 'Reiniciar', '', this.resetDebug)}
      </div>
    `;
  }

  private renderControlButton(icon: string, label: string, extraClass: string, listener: () => void) {
    return html`
      <vaadin-button
        class="c-runner-control-button ${extraClass}"
        aria-label=${label}
        title=${label}
        ?disabled=${!this.controlsEnabled}
        @click=${listener}
      >
        <vaadin-icon icon=${icon}></vaadin-icon>
      </vaadin-button>
    `;
  }

  private renderTerminalCard() {
    const stdoutEmpty = this.stdout.trim().length === 0;
    return html`
      <div class="c-runner-terminal-card">
        <span class="c-runner-terminal-title">Terminal</span>
        <div class="c-runner-terminal-body">
          <vaadin-text-area
            class="c-runner-stdin"
            label="stdin"
            placeholder="stdin antes de ejecutar, ej: 42"
            .value=${this.stdin}
            ?disabled=${!this.controlsEnabled}
            @value-changed=${this.handleStdinChanged}
          ></vaadin-text-area>
          <div class="c-runner-stdout-block">
            <span class="c-runner-terminal-label">stdout</span>
            <pre class="c-runner-stdout" data-empty=${stdoutEmpty ? 'true' : 'false'}>
              ${stdoutEmpty ? 'No hay salida' : this.stdout}
            </pre>
          </div>
        </div>
      </div>
    `;
  }

  private closePanel = (): void => {
    this.dispatchEvent(new CustomEvent('close-panel-requested', { bubbles: true, composed: true }));
  };

  private validateDebug = (): void => {
    this.dispatchEvent(new CustomEvent('validate-debug-requested', {
      detail: { source: this.source, stdin: this.stdin },
      bubbles: true,
      composed: true,
    }));
  };

  private stepDebug = (): void => {
    this.dispatchEvent(new CustomEvent('step-debug-requested', { bubbles: true, composed: true }));
  };

  private resetDebug = (): void => {
    this.dispatchEvent(new CustomEvent('reset-debug-requested', { bubbles: true, composed: true }));
  };

  private handleSourceChanged = (event: CustomEvent<{ value?: string }>): void => {
    const viewer = event.currentTarget as DebugSourceViewerElement;
    this.source = event.detail.value ?? viewer.value ?? '';
    this.dispatchEvent(new CustomEvent('source-value-changed', {
      detail: { value: this.source },
      bubbles: true,
      composed: true,
    }));
  };

  private handleStdinChanged = (event: CustomEvent<{ value?: string }>): void => {
    this.stdin = event.detail.value ?? '';
    this.dispatchEvent(new CustomEvent('stdin-value-changed', {
      detail: { value: this.stdin },
      bubbles: true,
      composed: true,
    }));
  };
}

if (!customElements.get('c-debugger-panel')) {
  customElements.define('c-debugger-panel', CDebuggerPanel);
}
