let sharedCanvasContext: CanvasRenderingContext2D | null | undefined;

function measureContext(): CanvasRenderingContext2D | null {
  if (sharedCanvasContext === undefined) {
    sharedCanvasContext = document.createElement('canvas').getContext('2d');
  }
  return sharedCanvasContext;
}

class WidthAwareLabel extends HTMLElement {
  static observedAttributes = ['full-text', 'safety-pixels'];

  private resizeObserver: ResizeObserver | null = null;
  private applyFrame: number | null = null;
  private text = '';
  private safety = 12;

  get fullText(): string {
    return this.text;
  }

  set fullText(value: string | null | undefined) {
    this.text = value ?? '';
    this.scheduleApplyText();
  }

  get safetyPixels(): number {
    return this.safety;
  }

  set safetyPixels(value: number | string | null | undefined) {
    const parsed = typeof value === 'number' ? value : Number(value);
    this.safety = Number.isFinite(parsed) ? parsed : 12;
    this.scheduleApplyText();
  }

  connectedCallback(): void {
    this.resizeObserver = new ResizeObserver(() => this.scheduleApplyText());
    this.resizeObserver.observe(this);
    this.scheduleApplyText();
  }

  disconnectedCallback(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    if (this.applyFrame !== null) {
      cancelAnimationFrame(this.applyFrame);
      this.applyFrame = null;
    }
  }

  attributeChangedCallback(name: string, _oldValue: string | null, newValue: string | null): void {
    if (name === 'full-text') {
      this.fullText = newValue;
      return;
    }
    if (name === 'safety-pixels') {
      this.safetyPixels = newValue;
    }
  }

  private scheduleApplyText(): void {
    if (!this.isConnected || this.applyFrame !== null) {
      return;
    }
    this.applyFrame = requestAnimationFrame(() => {
      this.applyFrame = null;
      this.applyText();
    });
  }

  private applyText(): void {
    const fullText = this.text;
    this.title = fullText;
    this.setAttribute('aria-label', fullText);
    this.dataset.fullText = fullText;

    if (!fullText) {
      this.textContent = '';
      return;
    }

    const availableWidth = Math.max(0, this.clientWidth - Math.max(0, this.safety));
    if (availableWidth === 0) {
      this.textContent = fullText;
      return;
    }

    const context = measureContext();
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
}

if (!customElements.get('width-aware-label')) {
  customElements.define('width-aware-label', WidthAwareLabel);
}
