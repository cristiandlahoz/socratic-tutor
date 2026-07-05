import { createRoot, type Root } from 'react-dom/client';
import CodeMirror from '@uiw/react-codemirror';
import { EditorView } from '@codemirror/view';
import type { Extension } from '@codemirror/state';
import {
  codeMirrorLanguageExtensions,
  resolveCodeMirrorTheme,
  resolveCodeMirrorThemeMode,
} from './code-mirror-extensions';

const codeBlockViewerEditorTheme: Extension = EditorView.theme({
  '&.cm-editor': {
    border: '0',
    backgroundColor: 'transparent',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
    boxShadow: 'none',
  },

  '&.cm-editor.cm-focused': {
    outline: 'none',
    boxShadow: 'none',
  },

  '&.cm-editor .cm-scroller': {
    backgroundColor: 'transparent',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-content': {
    backgroundColor: 'transparent',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
    paddingTop: '0',
    paddingBottom: '0',
    paddingRight: '4.25rem',
  },

  '&.cm-editor .cm-line': {
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-gutters': {
    display: 'none',
    border: '0',
    backgroundColor: 'transparent',
  },

  '&.cm-editor .cm-lineNumbers': {
    fontFamily: 'inherit',
    fontSize: 'inherit',
    lineHeight: 'inherit',
  },

  '&.cm-editor .cm-activeLine': {
    backgroundColor: 'transparent',
  },

  '&.cm-editor .cm-activeLineGutter': {
    backgroundColor: 'transparent',
  },
});

class CodeBlockViewerElement extends HTMLElement {
  private root: Root | null = null;
  private shadowReady = false;
  private internalValue = '';
  private internalLang = '';
  private internalDebuggable = false;
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
    this.internalLang = value ?? '';
    this.renderEditor();
  }

  get lang(): string {
    return this.internalLang;
  }

  set debuggable(value: boolean) {
    this.internalDebuggable = Boolean(value);
    this.renderEditor();
  }

  get debuggable(): boolean {
    return this.internalDebuggable;
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

    const canDebug = this.internalDebuggable && isSupportedDebugLanguage(this.internalLang);

    const codeMirrorTheme = resolveCodeMirrorTheme(
      this.currentThemePreference,
      this.systemThemeQuery.matches,
    );

    const codeMirrorThemeMode = resolveCodeMirrorThemeMode(
      this.currentThemePreference,
      this.systemThemeQuery.matches,
    );

    this.root.render(
      <>
        <style>{`
          :host {
            display: block;
            font-family: var(
              --aura-font-family,
              ui-monospace,
              SFMono-Regular,
              Menlo,
              Monaco,
              Consolas,
              "Liberation Mono",
              "Courier New",
              monospace
            );
            font-size: var(--message-font-size, 0.90rem);
            line-height: var(--message-line-height, 1.55);
          }

          .code-block-viewer__shell {
            background: transparent;
            overflow: visible;
            position: relative;
          }

          .code-block-viewer__toolbar {
            align-items: center;
            display: flex;
            justify-content: flex-end;
            opacity: 0;
            padding: 0;
            pointer-events: none;
            position: absolute;
            right: 0;
            top: 0;
            transition: opacity var(--motion-fast);
            z-index: 1;
          }

          .code-block-viewer__shell:hover .code-block-viewer__toolbar,
          .code-block-viewer__shell:focus-within .code-block-viewer__toolbar {
            opacity: 1;
          }

          .code-block-viewer__debug-button {
            border: 1px solid color-mix(
              in srgb,
              var(--aura-accent-text-color) 30%,
              var(--vaadin-border-color)
            );
            border-radius: 999px;
            background: color-mix(
              in srgb,
              var(--aura-accent-text-color) 10%,
              var(--vaadin-background-container)
            );
            color: var(--vaadin-text-color);
            cursor: pointer;
            font: 600 0.72rem var(
              --aura-font-family,
              ui-monospace,
              SFMono-Regular,
              Menlo,
              Monaco,
              Consolas,
              "Liberation Mono",
              "Courier New",
              monospace
            );
            letter-spacing: 0.02em;
            padding: 0.28rem 0.58rem;
            pointer-events: auto;
            transition:
              background var(--motion-fast),
              border-color var(--motion-fast),
              color var(--motion-fast);
          }

          .code-block-viewer__debug-button:hover {
            background: color-mix(
              in srgb,
              var(--aura-accent-color) 14%,
              var(--vaadin-background-container)
            );
            border-color: color-mix(
              in srgb,
              var(--aura-accent-color) 42%,
              var(--vaadin-border-color)
            );
          }

          .code-block-viewer__debug-button:focus-visible {
            outline: 2px solid var(--aura-accent-border-color);
            outline-offset: 2px;
          }
        `}</style>

        <div className="code-block-viewer__shell">
          {canDebug ? (
            <div className="code-block-viewer__toolbar">
              <button
                type="button"
                className="code-block-viewer__debug-button"
                aria-label="Debug this C example"
                onClick={this.handleDebugClick}
              >
                Debug
              </button>
            </div>
          ) : null}

          <CodeMirror
            value={this.internalValue}
            height="auto"
            width="100%"
            theme={codeMirrorThemeMode}
            extensions={[
              codeMirrorTheme,
              ...codeMirrorLanguageExtensions(this.internalLang),
              codeBlockViewerEditorTheme,
            ]}
            root={this.shadowRoot}
            editable={false}
            readOnly={true}
            basicSetup={{
              lineNumbers: false,
              highlightActiveLine: false,
              highlightActiveLineGutter: false,
              foldGutter: false,
              dropCursor: false,
              allowMultipleSelections: true,
              indentOnInput: false,
            }}
          />
        </div>
      </>,
    );
  }

  private readonly handleDebugClick = (): void => {
    this.dispatchEvent(
      new CustomEvent('debug-code-requested', {
        detail: {
          code: this.internalValue,
          lang: this.internalLang,
        },
        bubbles: true,
        composed: true,
      }),
    );
  };
}

function isSupportedDebugLanguage(lang: string): boolean {
  const normalized = (lang ?? '').trim().toLowerCase();

  return normalized === 'c' || normalized === 'c17';
}

if (!customElements.get('code-block-viewer')) {
  customElements.define('code-block-viewer', CodeBlockViewerElement);
}