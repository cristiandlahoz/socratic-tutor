import { createAsciify, supportsHtmlInCanvas, type AsciifyInstance } from './asciify';

const RADIUS = 0.25;
const SCALE = 3;
const SPACING = 1;
const STRENGTH = 0.55;
const FOLLOW_SPEED = 4;

class LoginAsciify extends HTMLElement {
  private asciify: AsciifyInstance | null = null;
  private observer: MutationObserver | null = null;
  private sourceObserver: ResizeObserver | null = null;
  private source: HTMLCanvasElement | null = null;
  private output: HTMLCanvasElement | null = null;

  connectedCallback(): void {
    this.observer = new MutationObserver(() => this.mount());
    this.observer.observe(this.parentElement ?? this, { childList: true });
    this.mount();
  }

  disconnectedCallback(): void {
    this.observer?.disconnect();
    this.observer = null;
    this.sourceObserver?.disconnect();
    this.sourceObserver = null;
    this.asciify?.destroy();
    this.asciify = null;
    this.source?.remove();
    this.output?.remove();
    this.source = null;
    this.output = null;
  }

  private mount(): void {
    if (this.output || !this.firstElementChild) {
      return;
    }

    const panel = this.firstElementChild as HTMLElement;

    const content = document.createElement('div');
    content.className = 'login-view__asciify-content login-view__asciify-art';
    content.textContent = '?';
    content.setAttribute('aria-hidden', 'true');

    const source = document.createElement('canvas');
    source.className = 'login-view__asciify-source';
    source.setAttribute('layoutsubtree', 'true');
    const native = supportsHtmlInCanvas();
    source.hidden = !native;
    source.append(content);

    const output = document.createElement('canvas');
    output.className = 'login-view__asciify-output';
    output.setAttribute('aria-hidden', 'true');

    panel.prepend(source, output);
    this.source = source;
    this.output = output;
    this.observer?.disconnect();
    this.observer = null;

    if (!native) {
      this.paintSource(source, content, panel);
    }

    this.asciify = createAsciify(
      { source, content, output },
      {
        radius: RADIUS,
        softness: 1,
        scale: SCALE,
        spacing: SPACING,
        backgroundOpacity: 0,
        contrast: 1,
        brightness: 0,
        invert: 0,
        strength: STRENGTH,
        baseStrength: 0,
        followSpeed: FOLLOW_SPEED,
        charset: 'ascii',
        background: [0, 0, 0],
      },
    );

    if (!native) {
      const repaint = () => {
        this.paintSource(source, content, panel);
        this.asciify?.updateSource();
        this.asciify?.resize();
      };
      this.sourceObserver = new ResizeObserver(repaint);
      this.sourceObserver.observe(panel);
      void document.fonts.ready.then(repaint);
    }
  }

  private paintSource(source: HTMLCanvasElement, content: HTMLElement, panel: HTMLElement): void {
    const width = panel.clientWidth;
    const height = panel.clientHeight;
    if (!width || !height) {
      return;
    }

    const dpr = Math.min(devicePixelRatio || 1, 2);
    source.width = Math.round(width * dpr);
    source.height = Math.round(height * dpr);

    const context = source.getContext('2d');
    if (!context) {
      return;
    }

    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    const style = getComputedStyle(content);
    const gradient = context.createRadialGradient(
      width * 0.72,
      height * 0.54,
      0,
      width * 0.72,
      height * 0.54,
      height * 0.78,
    );
    gradient.addColorStop(0, style.color);
    gradient.addColorStop(1, 'transparent');
    context.globalAlpha = 0.42;
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);

    context.globalAlpha = 0.85;
    context.fillStyle = style.color;
    context.font = `${Math.min(width * 0.9, height * 0.72)}px ${style.fontFamily}`;
    context.textAlign = 'right';
    context.textBaseline = 'bottom';
    context.fillText('?', width * 1.03, height * 1.05);
    context.globalAlpha = 1;
  }
}

if (!customElements.get('login-asciify')) {
  customElements.define('login-asciify', LoginAsciify);
}
