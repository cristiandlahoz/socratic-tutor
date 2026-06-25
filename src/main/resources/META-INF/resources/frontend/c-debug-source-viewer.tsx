import { linter, lintGutter, type Diagnostic } from '@codemirror/lint';
import type { Extension, Text } from '@codemirror/state';
import { Decoration, EditorView } from '@codemirror/view';
import CodeMirror from '@uiw/react-codemirror';
import { useEffect, useRef } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { codeMirrorLanguageExtensions, resolveCodeMirrorTheme } from './code-mirror-extensions';

const ACTIVE_LINE_SCROLL_MARGIN_PX = 72;

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

interface CDebugSourceEditorProps {
  activeLine: number;
  diagnostics: CompilerDiagnostic[];
  editable: boolean;
  lang: string;
  shadowRoot: ShadowRoot;
  systemThemeMatches: boolean;
  themePreference: string;
  value: string;
  onValueChange: (value: string) => void;
}

function normalizeDiagnostics(value: unknown): CompilerDiagnostic[] {
  if (typeof value === 'string') {
    return JSON.parse(value) as CompilerDiagnostic[];
  }
  return Array.isArray(value) ? (value as CompilerDiagnostic[]) : [];
}

function lintSeverity(severity: CompilerDiagnostic['severity']): Diagnostic['severity'] {
  switch ((severity ?? '').toUpperCase()) {
    case 'ERROR':
      return 'error';
    case 'WARNING':
      return 'warning';
    default:
      return 'info';
  }
}

function offsetFromLineColumn(doc: Text, line?: number | null, column?: number | null): number | null {
  if (!line || line < 1 || line > doc.lines) {
    return null;
  }
  const docLine = doc.line(line);
  const columnOffset = Math.max(0, (column ?? 1) - 1);
  return Math.min(docLine.to, docLine.from + columnOffset);
}

function toCodeMirrorDiagnostics(
  diagnostics: CompilerDiagnostic[],
  doc: Text,
): Diagnostic[] {
  return diagnostics.map((diagnostic) => {
    const from =
      diagnostic.fromOffset ?? offsetFromLineColumn(doc, diagnostic.line, diagnostic.column) ?? 0;
    const fallbackTo =
      offsetFromLineColumn(
        doc,
        diagnostic.endLine ?? diagnostic.line,
        diagnostic.endColumn ?? ((diagnostic.column ?? 1) + 1),
      ) ?? from + 1;
    const to = Math.max(from + 1, diagnostic.toOffset ?? fallbackTo);
    return {
      from: Math.max(0, Math.min(from, doc.length)),
      to: Math.max(0, Math.min(to, doc.length)),
      severity: lintSeverity(diagnostic.severity),
      message: diagnostic.message ?? '',
      source: diagnostic.ruleId ?? 'gcc',
    };
  });
}

function activeLineExtension(activeLine: number): Extension[] {
  if (!activeLine || activeLine < 1) {
    return [];
  }
  return [
    EditorView.decorations.compute([], (state) => {
      if (activeLine > state.doc.lines) {
        return Decoration.none;
      }
      return Decoration.set([
        Decoration.line({ class: 'cm-debug-active-line' }).range(state.doc.line(activeLine).from),
      ]);
    }),
  ];
}

const debuggerScrollTheme = EditorView.theme({
  '&': {
    height: '100%',
    maxHeight: '100%',
  },
  '.cm-scroller': {
    overflow: 'auto',
  },
});

function scrollActiveLineIntoView(view: EditorView, activeLine: number): void {
  if (!activeLine || activeLine < 1 || activeLine > view.state.doc.lines) {
    return;
  }

  const line = view.state.doc.line(activeLine);
  view.focus();
  view.dispatch({
    effects: EditorView.scrollIntoView(line.from, {
      y: 'center',
      yMargin: ACTIVE_LINE_SCROLL_MARGIN_PX,
    }),
  });
}

function CDebugSourceEditor({
  activeLine,
  diagnostics,
  editable,
  lang,
  shadowRoot,
  systemThemeMatches,
  themePreference,
  value,
  onValueChange,
}: CDebugSourceEditorProps) {
  const editorView = useRef<EditorView | null>(null);

  useEffect(() => {
    const view = editorView.current;
    if (!view) {
      return;
    }

    scrollActiveLineIntoView(view, activeLine);
  }, [activeLine, value]);

  return (
    <CodeMirror
      className="c-debug-source-viewer-editor"
      value={value}
      height="100%"
      width="100%"
      theme={resolveCodeMirrorTheme(themePreference, systemThemeMatches)}
      extensions={[
        debuggerScrollTheme,
        ...codeMirrorLanguageExtensions(lang),
        lintGutter(),
        linter((view) => toCodeMirrorDiagnostics(diagnostics, view.state.doc), {
          delay: 0,
        }),
        ...activeLineExtension(activeLine),
      ]}
      root={shadowRoot}
      editable={editable}
      readOnly={!editable}
      onCreateEditor={(view) => {
        editorView.current = view;
      }}
      onChange={onValueChange}
      basicSetup={{
        highlightActiveLine: false,
        highlightActiveLineGutter: false,
        foldGutter: false,
        dropCursor: false,
        allowMultipleSelections: true,
        indentOnInput: false,
      }}
    />
  );
}

class CDebugSourceViewerElement extends HTMLElement {
  private root: Root | null = null;
  private shadowReady = false;
  private internalValue = '';
  private internalLang = 'c';
  private internalDiagnostics: CompilerDiagnostic[] = [];
  private internalActiveLine = 0;
  private internalEditable = false;
  private currentThemePreference = 'system';
  private themePreferenceObserver: MutationObserver | null = null;
  private readonly systemThemeQuery = globalThis.matchMedia('(prefers-color-scheme: dark)');

  connectedCallback(): void {
    if (!this.shadowRoot) {
      this.attachShadow({ mode: 'open' });
    }
    if (!this.root && this.shadowRoot) {
      this.root = createRoot(this.shadowRoot);
    }
    this.shadowReady = true;
    this.currentThemePreference =
      document.documentElement.getAttribute('data-theme-preference') ?? 'system';
    this.themePreferenceObserver = new MutationObserver(() => {
      this.currentThemePreference =
        document.documentElement.getAttribute('data-theme-preference') ?? 'system';
      this.renderEditor();
    });
    this.themePreferenceObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme-preference'],
    });
    this.systemThemeQuery.addEventListener('change', this.handleSystemThemeChange);
    this.renderEditor();
  }

  disconnectedCallback(): void {
    this.systemThemeQuery.removeEventListener('change', this.handleSystemThemeChange);
    this.themePreferenceObserver?.disconnect();
    this.themePreferenceObserver = null;
    this.root?.unmount();
    this.root = null;
    this.shadowReady = false;
  }

  set value(value: string) {
    this.internalValue = value ?? '';
    this.renderEditor();
  }

  get value(): string {
    return this.internalValue;
  }

  set lang(value: string) {
    this.internalLang = value ?? 'c';
    this.renderEditor();
  }

  get lang(): string {
    return this.internalLang;
  }

  set diagnostics(value: CompilerDiagnostic[] | string) {
    this.internalDiagnostics = normalizeDiagnostics(value);
    this.renderEditor();
  }

  get diagnostics(): CompilerDiagnostic[] {
    return this.internalDiagnostics;
  }

  set activeLine(value: number) {
    this.internalActiveLine = Number(value) || 0;
    this.renderEditor();
  }

  get activeLine(): number {
    return this.internalActiveLine;
  }

  set editable(value: boolean) {
    this.internalEditable = Boolean(value);
    this.renderEditor();
  }

  get editable(): boolean {
    return this.internalEditable;
  }

  private readonly handleSystemThemeChange = (): void => {
    if (this.currentThemePreference === 'system') {
      this.renderEditor();
    }
  };

  private renderEditor(): void {
    if (!this.shadowReady || !this.root || !this.shadowRoot) {
      return;
    }

    this.root.render(
      <>
        <style>{`
          :host {
            display: block;
            height: 100%;
            min-height: 0;
          }

          .c-debug-source-viewer-editor {
            height: 100%;
            min-height: 0;
          }

          .cm-editor {
            border: 0;
            height: 100%;
            min-height: 0;
            background: var(--c-runner-editor-background, transparent) !important;
            color: var(--c-runner-editor-text, inherit);
            font-family: var(--chat-font-mono);
            font-size: var(--c-runner-editor-font-size, 14px);
            line-height: var(--c-runner-editor-line-height, 1.55);
          }

          .cm-editor,
          .cm-scroller {
            height: 100%;
          }

          .cm-scroller {
            min-height: 0;
          }

          .cm-scroller,
          .cm-content,
          .cm-gutters,
          .cm-lineNumbers {
            font-family: inherit;
            font-size: inherit;
            line-height: inherit;
          }

          .cm-scroller,
          .cm-gutters,
          .cm-activeLine,
          .cm-activeLineGutter {
            background: transparent !important;
          }

          .cm-gutters {
            border: 0;
            color: var(--c-runner-editor-gutter, var(--chat-text-secondary));
          }

          .cm-lineNumbers .cm-gutterElement {
            min-width: 2.6rem;
            padding-inline: 0.85rem 0.55rem;
          }

          .cm-content {
            min-height: 100%;
            padding: 0.78rem 0.88rem 0.82rem 0.2rem;
          }

          .cm-line {
            position: relative;
            padding-inline: 1.05rem 0.45rem;
          }

          .cm-debug-active-line {
            border-radius: var(--vaadin-radius-xs, 3px);
            background: var(
              --c-runner-active-line-background,
              color-mix(in srgb, var(--chat-accent) 16%, transparent)
            );
            color: var(--c-runner-active-line-foreground, inherit) !important;
            margin-inline-end: 0.2rem;
            box-shadow: none;
          }

          .cm-debug-active-line * {
            color: var(--c-runner-active-line-foreground, inherit) !important;
          }

          .cm-debug-active-line::before {
            content: var(--c-runner-active-line-marker, "->");
            position: absolute;
            inset-inline-start: -1.05rem;
            color: var(--c-runner-active-line-marker-color, var(--chat-accent));
          }

          .cm-lintRange-error {
            background-image: linear-gradient(
              45deg,
              transparent 65%,
              color-mix(in srgb, var(--lumo-error-color, #d93025) 86%, transparent) 80%,
              transparent 92%
            );
          }

          .cm-lintRange-warning {
            background-image: linear-gradient(
              45deg,
              transparent 65%,
              color-mix(in srgb, var(--lumo-warning-color, #fbbc04) 86%, transparent) 80%,
              transparent 92%
            );
          }

          .cm-tooltip.cm-tooltip-lint {
            max-width: min(42rem, calc(100vw - 2rem));
            max-height: min(22rem, 60vh);
            overflow: auto;
            border: 1px solid var(--c-runner-card-border, var(--chat-border-section));
            border-radius: 8px;
            background: color-mix(in srgb, var(--chat-code-background, #001f27) 94%, black);
            box-shadow: var(--chat-shadow-dark-card, 0 18px 48px rgb(0 0 0 / 0.35));
            color: var(--chat-text-primary, inherit);
            font-family: var(--chat-font-mono, monospace);
            font-size: 12px;
            line-height: 1.45;
            white-space: pre-wrap;
            overflow-wrap: anywhere;
            word-break: break-word;
          }

          .cm-tooltip.cm-tooltip-lint ul,
          .cm-tooltip.cm-tooltip-lint li {
            max-width: 100%;
            white-space: inherit;
            overflow-wrap: inherit;
            word-break: inherit;
          }
        `}</style>
        <CDebugSourceEditor
          activeLine={this.internalActiveLine}
          diagnostics={this.internalDiagnostics}
          editable={this.internalEditable}
          lang={this.internalLang}
          shadowRoot={this.shadowRoot}
          systemThemeMatches={this.systemThemeQuery.matches}
          themePreference={this.currentThemePreference}
          value={this.internalValue}
          onValueChange={(value) => {
            this.internalValue = value;
            this.dispatchEvent(
              new CustomEvent('value-changed', {
                detail: { value },
                bubbles: true,
                composed: true,
              }),
            );
          }}
        />
      </>,
    );
  }
}

if (!customElements.get('c-debug-source-viewer')) {
  customElements.define('c-debug-source-viewer', CDebugSourceViewerElement);
}
