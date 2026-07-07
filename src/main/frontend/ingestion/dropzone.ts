import { css, html, LitElement } from 'lit';

class DocumentUploadDropzoneElement extends LitElement {
  static readonly styles = css`
    :host {
      display: block;
      width: 100%;
      color: var(--vaadin-text-color);
      font-family: var(--aura-font-family);
    }

    .dropzone {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--vaadin-gap-s);
      padding: var(--vaadin-padding-l);
      border: var(--vaadin-input-field-border-width, 1px) dashed var(--vaadin-border-color);
      border-radius: var(--vaadin-radius-l);
      background: var(--aura-surface-color);
      text-align: center;
    }

    .title {
      color: var(--vaadin-text-color);
      font-size: var(--aura-font-size-m);
      font-weight: var(--aura-font-weight-semibold);
      line-height: var(--aura-line-height-m);
    }

    .hint {
      max-width: 34ch;
      color: var(--vaadin-text-color-secondary);
      font-size: var(--aura-font-size-s);
      line-height: 1.45;
    }

    .upload-slot {
      display: grid;
      justify-items: center;
      width: 100%;
      margin-top: var(--vaadin-gap-xs);
    }

    ::slotted(vaadin-upload) {
      width: 100%;
      text-align: center;
    }

    ::slotted(vaadin-upload)::part(primary-buttons) {
      display: flex;
      justify-content: center;
      width: 100%;
    }

    ::slotted(vaadin-upload)::part(drop-label) {
      display: none;
    }
  `;

  protected render() {
    return html`
      <div class="dropzone">
        <span class="title">Sube materiales PDF</span>
        <span class="hint">Arrastra uno o varios archivos para transformarlos antes de indexar.</span>
        <div class="upload-slot">
          <slot></slot>
        </div>
      </div>
    `;
  }
}

if (!customElements.get('document-upload-dropzone')) {
  customElements.define('document-upload-dropzone', DocumentUploadDropzoneElement);
}
