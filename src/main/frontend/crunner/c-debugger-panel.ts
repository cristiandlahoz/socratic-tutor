import './c-debug-source-viewer.js';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/popover';
import '@vaadin/text-area';
import '@vaadin/tooltip';
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
  cursorLine: number;
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
    cursorLine: { type: Number, attribute: 'cursor-line' },
    diagnostics: { type: Array },
    editable: { type: Boolean },
    executableLines: { type: Array, attribute: 'executable-lines' },
    lang: { type: String },
    locals: { type: Array },
    source: { type: String },
    statusText: { type: String, attribute: 'status-text' },
    stdin: { type: String },
    stdout: { type: String },
  };

  declare activeLine: number;
  declare controlsEnabled: boolean;
  declare cursorLine: number;
  declare diagnostics: CompilerDiagnostic[];
  declare editable: boolean;
  declare executableLines: number[];
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
    this.cursorLine = 0;
    this.diagnostics = [];
    this.editable = true;
    this.executableLines = [];
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

  setExecutableLines(value: unknown): void {
    this.executableLines = Array.isArray(value)
      ? value.map(Number).filter((line) => Number.isInteger(line) && line > 0)
      : [];
  }

  protected render() {
    return html`
      <div class="c-runner-scroll-shell">
        <div class="c-runner-panel">
          <div class="c-runner-header">
            <button class="ui-icon-toggle c-runner-panel-toggle" type="button" title="Ocultar depurador" aria-label="Ocultar depurador" @click=${this.closePanel}>
              <vaadin-icon src="/icons/IconSidebarOpen.svg" aria-hidden="true"></vaadin-icon>
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
                @cursor-line-changed=${this.handleCursorLineChanged}
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
    const canMoveToCursorLine = this.controlsEnabled && this.executableLines.includes(this.cursorLine);
    const cursorButtonLabel = this.cursorLine > 0
      ? `Ir a la linea ${this.cursorLine}`
      : 'Ir a la linea del cursor';
    return html`
      <div class="c-runner-controls">
        ${this.renderControlButton('/icons/IconDebuggerRun.svg', 'Ejecutar depuracion', 'c-runner-validate-button', this.validateDebug)}
        ${this.renderControlButton('/icons/IconDebuggerStep.svg', 'Paso siguiente', 'c-runner-step-button', this.stepDebug)}
        ${canMoveToCursorLine
          ? this.renderControlButton('/icons/IconCursor.svg', cursorButtonLabel, 'c-runner-cursor-button', this.moveToCursorLine)
          : nothing}
        ${this.renderControlButton('/icons/IconDebuggerReload.svg', 'Reiniciar', 'c-runner-reset-button', this.resetDebug)}
      </div>
    `;
  }

  private renderControlButton(
    icon: string,
    label: string,
    extraClass: string,
    listener: () => void,
    disabled = !this.controlsEnabled,
  ) {
    return html`
      <vaadin-button
        class="c-runner-control-button ${extraClass}"
        aria-label=${label}
        title=${label}
        ?disabled=${disabled}
        @click=${listener}
      >
        <vaadin-icon src=${icon} aria-hidden="true"></vaadin-icon>
      </vaadin-button>
    `;
  }

  private renderTerminalCard() {
    const stdoutEmpty = this.stdout.trim().length === 0;
    return html`
      <div class="c-runner-terminal-card">
        <span class="c-runner-terminal-title">Terminal</span>
        <div class="c-runner-terminal-body">
          <div class="c-runner-stdin-label">
            <span>stdin</span>
            <button
              id="stdin-help"
              class="ui-icon-toggle c-runner-stdin-help"
              type="button"
              aria-label="Cómo ingresar datos en stdin"
            >
              <vaadin-icon icon="vaadin:info-circle" aria-hidden="true"></vaadin-icon>
            </button>
          </div>
          <vaadin-text-area
            id="stdin-field"
            class="c-runner-stdin"
            accessible-name="stdin"
            placeholder=${`Ej: '42' "hola mundo"`}
            .value=${this.stdin}
            ?disabled=${!this.controlsEnabled}
            @value-changed=${this.handleStdinChanged}
          ></vaadin-text-area>
          <vaadin-tooltip
            for="stdin-help"
            text=${`Después de cambiar las entradas, vuelve a compilar y ejecutar. Cada entrada debe estar entre comillas simples ('') o dobles (""); scanf y los demás mecanismos de stdin de C las leen en orden.`}
          ></vaadin-tooltip>
          <div class="c-runner-stdout-block">
            <span class="c-runner-terminal-label">stdout</span>
            <pre class="c-runner-stdout" data-empty=${stdoutEmpty ? 'true' : 'false'}>${stdoutEmpty ? 'No hay salida' : this.stdout}</pre>
          </div>
        </div>
      </div>
    `;
  }

  private closePanel = (): void => {
    this.dispatchEvent(new CustomEvent('close-panel-requested', { bubbles: true, composed: true }));
  };

  private isAtFinalSnapshot(): boolean {
    const match = /^Snapshot\s+(\d+)\/(\d+)/.exec(this.statusText.trim());
    if (!match) {
      return false;
    }
    const current = Number(match[1]);
    const total = Number(match[2]);
    return total > 0 && current >= total;
  }

  private validateDebug = (): void => {
    this.dispatchEvent(new CustomEvent('validate-debug-requested', {
      detail: { source: this.source, stdin: this.stdin },
      bubbles: true,
      composed: true,
    }));
  };

  private stepDebug = (): void => {
    this.dispatchEvent(new CustomEvent(this.isAtFinalSnapshot() ? 'reset-debug-requested' : 'step-debug-requested', {
      bubbles: true,
      composed: true,
    }));
  };

  private resetDebug = (): void => {
    this.dispatchEvent(new CustomEvent('reset-debug-requested', { bubbles: true, composed: true }));
  };

  private moveToCursorLine = (): void => {
    this.dispatchEvent(new CustomEvent('move-to-cursor-line-requested', {
      detail: { line: this.cursorLine },
      bubbles: true,
      composed: true,
    }));
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

  private handleCursorLineChanged = (event: CustomEvent<{ line?: number }>): void => {
    const viewer = event.currentTarget as DebugSourceViewerElement;
    this.cursorLine = event.detail.line ?? viewer.cursorLine ?? 0;
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
