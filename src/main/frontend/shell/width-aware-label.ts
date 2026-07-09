class WidthAwareLabel extends HTMLElement {
  static observedAttributes = ['full-text'];

  private text = '';

  get fullText(): string {
    return this.text;
  }

  set fullText(value: string | null | undefined) {
    this.text = value ?? '';
    this.applyText();
  }

  connectedCallback(): void {
    this.applyText();
  }

  attributeChangedCallback(_name: string, _oldValue: string | null, newValue: string | null): void {
    this.fullText = newValue;
  }

  private applyText(): void {
    this.textContent = this.text;
    this.title = this.text;
    this.setAttribute('aria-label', this.text);
    this.dataset.fullText = this.text;
  }
}

if (!customElements.get('width-aware-label')) {
  customElements.define('width-aware-label', WidthAwareLabel);
}
