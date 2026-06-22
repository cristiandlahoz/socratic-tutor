import { createRoot, type Root } from 'react-dom/client';
import CodeMirror from '@uiw/react-codemirror';
import { codeMirrorLanguageExtensions, resolveCodeMirrorTheme } from './code-mirror-extensions';

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

    this.root.render(
      <>
        <style>{`
          :host {
             display: block;
           }

          .code-block-viewer__shell {
            background: var(--theme-app-background);
            border-radius: inherit;
            overflow: hidden;
          }

          .code-block-viewer__toolbar {
            display: flex;
            justify-content: flex-end;
            align-items: center;
            padding: 0.4rem 0.48rem;
            border-bottom: 1px solid color-mix(in srgb, var(--chat-border-visible) 72%, transparent);
          }

          .cm-editor {
            border: 0;
            background: var(--theme-app-background) !important;
            font-family: var(--chat-font-mono);
            font-size: 0.75rem;
            line-height: 1rem;
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
          }

          .code-block-viewer__debug-button {
            border: 1px solid color-mix(in srgb, var(--chat-interactive) 30%, var(--chat-border-visible));
            border-radius: 999px;
            background: color-mix(in srgb, var(--chat-interactive) 10%, var(--chat-code-background));
            color: var(--chat-text-primary);
            cursor: pointer;
            font: 600 0.72rem var(--chat-font-mono);
            letter-spacing: 0.02em;
            padding: 0.28rem 0.58rem;
            transition:
              background var(--chat-transition-fast),
              border-color var(--chat-transition-fast),
              color var(--chat-transition-fast);
          }

          .code-block-viewer__debug-button:hover {
            background: color-mix(in srgb, var(--chat-interactive-strong) 14%, var(--chat-code-background));
            border-color: color-mix(in srgb, var(--chat-interactive-strong) 42%, var(--chat-border-visible));
          }

          .code-block-viewer__debug-button:focus-visible {
            outline: 2px solid var(--chat-focus-ring);
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
            theme={resolveCodeMirrorTheme(
              this.currentThemePreference,
              this.systemThemeQuery.matches,
            )}
            extensions={codeMirrorLanguageExtensions(this.internalLang)}
            root={this.shadowRoot}
            editable={false}
            readOnly={true}
            basicSetup={{
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
