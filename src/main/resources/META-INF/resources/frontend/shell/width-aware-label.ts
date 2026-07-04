import { LitElement } from 'lit';

class WidthAwareLabel extends LitElement {
  static properties = {
    fullText: { type: String },
    safetyPixels: { type: Number },
  };

  declare fullText: string;
  declare safetyPixels: number;

  private resizeObserver?: ResizeObserver;
  private canvasContext?: CanvasRenderingContext2D | null;

  constructor() {
    super();
    this.fullText = '';
    this.safetyPixels = 12;
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.resizeObserver = new ResizeObserver(() => this.applyText());
    this.resizeObserver.observe(this);
    this.applyText();
  }

  disconnectedCallback(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = undefined;
    super.disconnectedCallback();
  }

  protected updated(): void {
    this.applyText();
  }

  private applyText(): void {
    const fullText = this.fullText ?? '';
    this.title = fullText;
    this.setAttribute('aria-label', fullText);
    this.dataset.fullText = fullText;

    if (!fullText) {
      this.textContent = '';
      return;
    }

    const availableWidth = Math.max(0, this.clientWidth - Math.max(0, this.safetyPixels ?? 0));
    if (availableWidth === 0) {
      this.textContent = fullText;
      return;
    }

    const context = this.measureContext();
    if (!context) {
      this.textContent = fullText;
      return;
    }

    const styles = getComputedStyle(this);
    context.font = [
      styles.fontStyle,
      styles.fontVariant,
      styles.fontWeight,
      styles.fontSize,
      styles.fontFamily,
    ].join(' ');

    const measure = (value: string) => context.measureText(value).width;
    if (measure(fullText) <= availableWidth) {
      this.textContent = fullText;
      return;
    }

    let low = 0;
    let high = fullText.length;
    let best = '...';

    while (low <= high) {
      const mid = Math.floor((low + high) / 2);
      const nextValue = mid <= 3 ? '...' : `${fullText.slice(0, mid - 3)}...`;
      if (measure(nextValue) <= availableWidth) {
        best = nextValue;
        low = mid + 1;
      }
      else {
        high = mid - 1;
      }
    }

    this.textContent = best;
  }

  private measureContext(): CanvasRenderingContext2D | null {
    if (this.canvasContext === undefined) {
      this.canvasContext = document.createElement('canvas').getContext('2d');
    }
    return this.canvasContext;
  }
}

customElements.define('width-aware-label', WidthAwareLabel);
