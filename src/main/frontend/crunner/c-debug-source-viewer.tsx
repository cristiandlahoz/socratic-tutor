import { linter, lintGutter, type Diagnostic } from '@codemirror/lint';
import type { Extension, Text } from '@codemirror/state';
import { Decoration, EditorView } from '@codemirror/view';
import CodeMirror from '@uiw/react-codemirror';
import { useEffect, useRef } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { codeMirrorLanguageExtensions, resolveCodeMirrorTheme } from 'Frontend/shared/code/code-mirror-extensions';

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
  onCursorLineChange: (line: number) => void;
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

const debuggerEditorTheme: Extension = EditorView.theme({
  '&.cm-editor': {
    border: '0',
    height: '100%',
    minHeight: '0',
    background: 'var(--c-runner-editor-background, transparent)',
    color: 'var(--c-runner-editor-text, inherit)',
    fontFamily: 'var(--font-mono)',
    fontSize: 'var(--c-runner-editor-font-size, 14px)',
    lineHeight: 'var(--c-runner-editor-line-height, 1.55)',
  },

  '&.cm-editor .cm-scroller': {
    height: '100%',
    minHeight: '0',
    background: 'transparent',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-content': {
    minHeight: '100%',
    padding: '0.78rem 0.88rem 0.82rem 0.2rem',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-gutters': {
    border: '0',
    background: 'var(--c-runner-editor-background, transparent)',
    color: 'var(--c-runner-editor-gutter, var(--vaadin-text-color-secondary))',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-lineNumbers': {
    background: 'var(--c-runner-editor-background, transparent)',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-lineNumbers .cm-gutterElement': {
    background: 'var(--c-runner-editor-background, transparent)',
    minWidth: '2.6rem',
    paddingInline: '0.85rem 0.55rem',
  },

  '&.cm-editor .cm-line': {
    position: 'relative',
    paddingInline: '1.05rem 0.45rem',
  },

  '&.cm-editor .cm-activeLine': {
    background: 'transparent',
  },

  '&.cm-editor .cm-activeLineGutter': {
    background: 'transparent',
  },

  '&.cm-editor .cm-debug-active-line': {
    borderRadius: 'var(--vaadin-radius-xs, 3px)',
    background:
      'var(--c-runner-active-line-background, color-mix(in srgb, var(--aura-accent-text-color) 16%, transparent))',
    color: 'var(--c-runner-active-line-foreground, inherit)',
    marginInlineEnd: '0.2rem',
    boxShadow: 'none',
  },

  '&.cm-editor .cm-debug-active-line *': {
    color: 'var(--c-runner-active-line-foreground, inherit)',
  },

  '&.cm-editor .cm-debug-active-line::before': {
    content: 'var(--c-runner-active-line-marker, "->")',
    position: 'absolute',
    insetInlineStart: '-1.05rem',
    color: 'var(--c-runner-active-line-marker-color, var(--aura-accent-text-color))',
  },

  '&.cm-editor .cm-lintRange-error': {
    backgroundImage:
      'linear-gradient(45deg, transparent 65%, color-mix(in srgb, var(--lumo-error-color, var(--aura-red)) 86%, transparent) 80%, transparent 92%)',
  },

  '&.cm-editor .cm-lintRange-warning': {
    backgroundImage:
      'linear-gradient(45deg, transparent 65%, color-mix(in srgb, var(--lumo-warning-color, var(--aura-yellow)) 86%, transparent) 80%, transparent 92%)',
  },

  '&.cm-editor .cm-tooltip.cm-tooltip-lint': {
    maxWidth: 'min(42rem, calc(100vw - 2rem))',
    maxHeight: 'min(22rem, 60vh)',
    overflow: 'auto',
    border: '1px solid var(--c-runner-card-border, var(--vaadin-border-color))',
    borderRadius: '8px',
    background:
      'color-mix(in srgb, var(--vaadin-background-container) 94%, var(--color-black))',
    boxShadow: 'var(--aura-shadow-m)',
    color: 'var(--vaadin-text-color)',
    fontFamily: 'var(--font-mono, monospace)',
    fontSize: '12px',
    lineHeight: '1.45',
    whiteSpace: 'pre-wrap',
    overflowWrap: 'anywhere',
    wordBreak: 'break-word',
  },

  '&.cm-editor .cm-tooltip.cm-tooltip-lint ul, &.cm-editor .cm-tooltip.cm-tooltip-lint li': {
    maxWidth: '100%',
    whiteSpace: 'inherit',
    overflowWrap: 'inherit',
    wordBreak: 'inherit',
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
  onCursorLineChange,
  onValueChange,
}: CDebugSourceEditorProps) {
  const editorView = useRef<EditorView | null>(null);
  const cursorLine = useRef(0);

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
        debuggerEditorTheme,
        ...codeMirrorLanguageExtensions(lang),
        lintGutter(),
        linter((view) => toCodeMirrorDiagnostics(diagnostics, view.state.doc), {
          delay: 0,
        }),
        EditorView.updateListener.of((update) => {
          if (!update.selectionSet && !update.docChanged) {
            return;
          }
          const nextCursorLine = update.state.doc.lineAt(update.state.selection.main.head).number;
          if (nextCursorLine === cursorLine.current) {
            return;
          }
          cursorLine.current = nextCursorLine;
          onCursorLineChange(nextCursorLine);
        }),
        ...activeLineExtension(activeLine),
      ]}
      root={shadowRoot}
      editable={editable}
      readOnly={!editable}
      onCreateEditor={(view) => {
        editorView.current = view;
        const nextCursorLine = view.state.doc.lineAt(view.state.selection.main.head).number;
        cursorLine.current = nextCursorLine;
        onCursorLineChange(nextCursorLine);
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
  private internalCursorLine = 0;
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

  get cursorLine(): number {
    return this.internalCursorLine;
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
          onCursorLineChange={(line) => {
            this.internalCursorLine = line;
            this.dispatchEvent(
              new CustomEvent('cursor-line-changed', {
                detail: { line },
                bubbles: true,
                composed: true,
              }),
            );
          }}
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
