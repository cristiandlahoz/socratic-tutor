import { LitElement, css, html } from 'lit';
import { BRAILLE_SPINNER_DEFAULT, resolveSpinner, type BrailleSpinnerName } from './braille-spinners';

class BrailleSpinner extends LitElement {
  static properties = {
    spinner: { type: String },
    interval: { type: Number },
  };

  static styles = css`
    :host {
      display: inline-block;
      font-family: var(--thinking-spinner-font-family, inherit);
      font-size: var(--thinking-spinner-size, 1.625rem);
      font-weight: var(--thinking-spinner-weight, 700);
      line-height: 1;
      min-width: 2ch;
      white-space: pre;
      letter-spacing: var(--thinking-spinner-letter-spacing, 0.02em);
      color: var(--thinking-spinner-color, #d4af37);
      background-image: var(
        --thinking-spinner-gradient,
        linear-gradient(135deg, #f2c94c 0%, #d4af37 46%, #b3261e 100%)
      );
      background-clip: text;
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      text-shadow: var(
        --thinking-spinner-shadow,
        0 0 0.25rem color-mix(in srgb, var(--thinking-spinner-accent-color, #b71c1c) 22%, transparent)
      );
    }
  `;

  declare spinner: BrailleSpinnerName;
  declare interval?: number;

  private frameIndex = 0;
  private timerId: number | null = null;

  constructor() {
    super();
    this.spinner = BRAILLE_SPINNER_DEFAULT;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.startAnimation();
  }

  disconnectedCallback(): void {
    this.stopAnimation();
    super.disconnectedCallback();
  }

  protected updated(changedProperties: Map<string, unknown>): void {
    if (!changedProperties.has('spinner') && !changedProperties.has('interval')) {
      return;
    }
    this.frameIndex = 0;
    this.startAnimation();
  }

  protected render() {
    const spinner = resolveSpinner(this.spinner);
    const frame = spinner.frames[this.frameIndex] ?? spinner.frames[0] ?? '';
    return html`${frame}`;
  }

  private startAnimation(): void {
    this.stopAnimation();
    const spinner = resolveSpinner(this.spinner);
    const cadence = this.interval ?? spinner.interval;
    this.timerId = window.setInterval(() => {
      this.frameIndex = (this.frameIndex + 1) % spinner.frames.length;
      this.requestUpdate();
    }, cadence);
  }

  private stopAnimation(): void {
    if (this.timerId == null) {
      return;
    }
    window.clearInterval(this.timerId);
    this.timerId = null;
  }
}

if (!customElements.get('braille-spinner')) {
  customElements.define('braille-spinner', BrailleSpinner);
}
