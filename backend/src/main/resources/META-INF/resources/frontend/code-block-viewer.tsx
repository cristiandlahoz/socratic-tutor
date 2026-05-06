import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import CodeMirror from '@uiw/react-codemirror';
import { gruvboxDark } from '@fsegurai/codemirror-theme-gruvbox-dark';
import { solarizedDark } from '@fsegurai/codemirror-theme-solarized-dark';
import { json } from '@codemirror/lang-json';
import { xml } from '@codemirror/lang-xml';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { java } from '@codemirror/lang-java';
import { cpp } from '@codemirror/lang-cpp';
import type { Extension } from '@codemirror/state';

function langExtension(lang: string | null | undefined): Extension[] {
  switch ((lang ?? '').toLowerCase()) {
    case 'java':
      return [java()];
    case 'c':
    case 'h':
    case 'hpp':
    case 'cpp':
    case 'c++':
      return [cpp()];
    case 'json':
      return [json()];
    case 'xml':
    case 'html':
      return [xml()];
    case 'js':
    case 'jsx':
    case 'javascript':
    case 'ts':
    case 'tsx':
    case 'typescript':
      return [javascript({ jsx: true, typescript: true })];
    case 'py':
    case 'python':
      return [python()];
    default:
      return [];
  }
}

class CodeBlockViewerElement extends HTMLElement {
  private root: Root | null = null;
  private shadowReady = false;
  private internalValue = '';
  private internalLang = '';
  private currentThemePreference = 'system';
  private themePreferenceObserver: MutationObserver | null = null;
  private systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)');

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

  private readonly handleSystemThemeChange = (): void => {
    if (this.currentThemePreference === 'system') {
      this.renderEditor();
    }
  };

  private resolveTheme(): Extension | undefined {
    if (this.currentThemePreference === 'light') {
      return gruvboxDark;
    }
    if (this.currentThemePreference === 'dark') {
      return solarizedDark;
    }
    return this.systemThemeQuery.matches ? solarizedDark : gruvboxDark;
  }

  private renderEditor(): void {
    if (!this.shadowReady || !this.root || !this.shadowRoot) {
      return;
    }

    this.root.render(
      <>
        <style>{`
         :host {
            display: block;
          }

          .cm-editor {
            border: 0;
            background: transparent !important;
            font-family: var(--chat-font-body);
            font-size: var(--chat-font-size);
            line-height: var(--chat-leading-code);
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
        `}</style>
        <CodeMirror
          value={this.internalValue}
          height="auto"
          width="100%"
          theme={this.resolveTheme()}
          extensions={langExtension(this.internalLang)}
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
      </>,
    );
  }
}

if (!customElements.get('code-block-viewer')) {
  customElements.define('code-block-viewer', CodeBlockViewerElement);
}
