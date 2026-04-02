import React from 'react';
import { createRoot, type Root } from 'react-dom/client';
import CodeMirror from '@uiw/react-codemirror';
import { oneDark } from '@codemirror/theme-one-dark';
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

  connectedCallback(): void {
    if (!this.shadowRoot) {
      this.attachShadow({ mode: 'open' });
    }
    if (!this.root && this.shadowRoot) {
      this.root = createRoot(this.shadowRoot);
    }
    this.shadowReady = true;
    this.renderEditor();
  }

  disconnectedCallback(): void {
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

  private renderEditor(): void {
    if (!this.shadowReady || !this.root || !this.shadowRoot) {
      return;
    }

    this.root.render(
      <CodeMirror
        value={this.internalValue}
        height="auto"
        width="100%"
        theme={oneDark}
        extensions={langExtension(this.internalLang)}
        root={this.shadowRoot}
        editable={false}
        readOnly={true}
        basicSetup={{
          highlightActiveLine: false,
          highlightActiveLineGutter: false,
          foldGutter: false,
          dropCursor: false,
          allowMultipleSelections: false,
          indentOnInput: false,
        }}
      />,
    );
  }
}

if (!customElements.get('code-block-viewer')) {
  customElements.define('code-block-viewer', CodeBlockViewerElement);
}
