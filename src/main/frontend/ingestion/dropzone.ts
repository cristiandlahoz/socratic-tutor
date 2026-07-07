import { haptic } from 'Frontend/shared/haptics.js';
import { css, html, LitElement } from 'lit';

class DocumentUploadDropzoneElement extends LitElement {
  private uploadElement: HTMLElement | null = null;

  private readonly handleFileRejected = (): void => haptic('error');
  private readonly handleUploadError = (): void => haptic('error');
  private readonly handleUploadSuccess = (): void => haptic('success');
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

  connectedCallback(): void {
    super.connectedCallback();
    this.updateComplete.then(() => {
      if (this.isConnected) {
        this.attachUploadElement();
      }
    });
  }

  disconnectedCallback(): void {
    this.detachUploadElement();
    super.disconnectedCallback();
  }

  protected render() {
    return html`
      <div class="dropzone" @drop=${this.handleDrop}>
        <span class="title">Sube materiales PDF</span>
        <span class="hint">Arrastra uno o varios archivos para transformarlos antes de indexar.</span>
        <div class="upload-slot">
          <slot @slotchange=${this.attachUploadElement}></slot>
        </div>
      </div>
    `;
  }

  private readonly handleDrop = (): void => haptic('selection');

  private attachUploadElement = (): void => {
    const upload = this.querySelector<HTMLElement>('vaadin-upload');

    if (upload === this.uploadElement) {
      return;
    }

    this.detachUploadElement();
    this.uploadElement = upload;
    this.uploadElement?.addEventListener('file-reject', this.handleFileRejected);
    this.uploadElement?.addEventListener('upload-error', this.handleUploadError);
    this.uploadElement?.addEventListener('upload-success', this.handleUploadSuccess);
  };

  private detachUploadElement(): void {
    this.uploadElement?.removeEventListener('file-reject', this.handleFileRejected);
    this.uploadElement?.removeEventListener('upload-error', this.handleUploadError);
    this.uploadElement?.removeEventListener('upload-success', this.handleUploadSuccess);
    this.uploadElement = null;
  }
}

if (!customElements.get('document-upload-dropzone')) {
  customElements.define('document-upload-dropzone', DocumentUploadDropzoneElement);
}
