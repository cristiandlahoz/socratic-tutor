import { LitElement, css, html } from 'lit';

class AsciiFrameAnimation extends LitElement {
  static properties = {
    frameFolder: { type: String, attribute: 'frame-folder' },
    frameCount: { type: Number, attribute: 'frame-count' },
    fps: { type: Number },
    loop: { type: Boolean },
    bouncing: { type: Boolean },
    loading: { state: true },
    error: { state: true },
    currentFrame: { state: true },
  };

  static styles = css`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }

    .frame-shell {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    }

    pre {
      margin: 0;
      white-space: pre;
      font-family: var(--chat-font-mono, monospace);
      font-size: var(--ascii-frame-font-size, clamp(2.3px, 0.3vw, 3.34px));
      line-height: 1;
      color: var(--chat-ascii-animation-color, #d4af37);
      text-shadow: var(
        --chat-ascii-animation-shadow,
        0 0 0.28rem color-mix(in srgb, var(--chat-ascii-animation-color, #d4af37) 20%, transparent)
      );
      text-rendering: optimizeSpeed;
      transform: translateY(var(--ascii-frame-translate-y, 1%)) scale(var(--ascii-frame-scale, 1));
      transform-origin: center;
      user-select: none;
    }

    .status {
      font-family: var(--chat-font-mono, monospace);
      font-size: 0.75rem;
      color: var(--chat-text-disabled, #888888);
    }
  `;

  declare frameFolder: string;
  declare frameCount: number;
  declare fps: number;
  declare loop: boolean;
  declare bouncing: boolean;
  declare loading: boolean;
  declare error: string;
  declare currentFrame: number;

  private frames: string[] = [];
  private animationHandle: number | null = null;
  private lastFrameTime = -1;
  private hasLoadedOnce = false;
  private frameDirection: 1 | -1 = 1;
  private reachedLastFrameInBounce = false;

  constructor() {
    super();
    this.frameFolder = 'crow3-frames';
    this.frameCount = 240;
    this.fps = 30;
    this.loop = true;
    this.bouncing = false;
    this.loading = true;
    this.error = '';
    this.currentFrame = 0;
  }

  connectedCallback(): void {
    super.connectedCallback();
  }

  disconnectedCallback(): void {
    this.stopAnimation();
    super.disconnectedCallback();
  }

  protected updated(changedProperties: Map<string, unknown>): void {
    if (!this.hasLoadedOnce) {
      return;
    }
    if (changedProperties.has('frameFolder') || changedProperties.has('frameCount')) {
      this.loadFrames();
    }
  }

  protected firstUpdated(): void {
    this.hasLoadedOnce = true;
    this.loadFrames();
  }

  protected render() {
    if (this.loading) {
      return html`<div class="frame-shell"><div class="status">Loading ASCII animation...</div></div>`;
    }

    if (this.error) {
      return html`<div class="frame-shell"><div class="status">${this.error}</div></div>`;
    }

    const frame = this.frames[this.currentFrame] ?? '';
    return html`<div class="frame-shell"><pre aria-label="ASCII crow animation">${frame}</pre></div>`;
  }

  private async loadFrames(): Promise<void> {
    this.stopAnimation();
    this.loading = true;
    this.error = '';
    this.currentFrame = 0;
    this.frameDirection = 1;
    this.reachedLastFrameInBounce = false;
    this.frames = [];

    try {
      const frameFiles = Array.from(
        { length: this.frameCount },
        (_, index) => `frame_${String(index + 1).padStart(4, '0')}.txt`
      );

      const loadedFrames = await Promise.all(
        frameFiles.map(async (filename) => {
          const response = await fetch(`/${this.frameFolder}/${filename}`);
          if (!response.ok) {
            throw new Error(`Failed to fetch ${filename}: ${response.status}`);
          }
          return response.text();
        })
      );

      this.frames = loadedFrames;
      this.loading = false;
      this.startAnimation();
      this.requestUpdate();
    } catch (_error) {
      this.loading = false;
      this.error = 'ASCII animation unavailable';
      this.requestUpdate();
    }
  }

  private startAnimation(): void {
    if (this.animationHandle != null || this.frames.length === 0 || this.reducedMotionEnabled()) {
      return;
    }
    this.lastFrameTime = -1;
    this.frameDirection = 1;
    this.reachedLastFrameInBounce = false;
    this.animationHandle = requestAnimationFrame(this.tick);
  }

  private stopAnimation(): void {
    if (this.animationHandle == null) {
      return;
    }
    cancelAnimationFrame(this.animationHandle);
    this.animationHandle = null;
    this.lastFrameTime = -1;
    this.frameDirection = 1;
    this.reachedLastFrameInBounce = false;
  }

  private tick = (now: number): void => {
    if (this.lastFrameTime < 0) {
      this.lastFrameTime = now;
    } else {
      const frameDuration = 1000 / Math.max(this.fps, 1);
      let delta = now - this.lastFrameTime;
      while (delta >= frameDuration) {
        if (this.bouncing) {
          if (this.frames.length <= 1) {
            this.stopAnimation();
            return;
          }

          const lastIndex = this.frames.length - 1;
          const nextFrame = this.currentFrame + this.frameDirection;

          if (nextFrame >= lastIndex) {
            this.currentFrame = lastIndex;
            this.frameDirection = -1;
            this.reachedLastFrameInBounce = true;
          } else if (nextFrame <= 0) {
            this.currentFrame = 0;
            this.frameDirection = 1;
          } else {
            this.currentFrame = nextFrame;
          }

          if (!this.loop && this.reachedLastFrameInBounce && this.currentFrame === 0) {
            this.stopAnimation();
            return;
          }
        } else {
          if (this.loop) {
            this.currentFrame = (this.currentFrame + 1) % this.frames.length;
          } else if (this.currentFrame < this.frames.length - 1) {
            this.currentFrame += 1;
          }

          if (!this.loop && this.currentFrame >= this.frames.length - 1) {
            this.stopAnimation();
            return;
          }
        }

        delta -= frameDuration;
        this.lastFrameTime += frameDuration;
      }
    }

    this.animationHandle = requestAnimationFrame(this.tick);
  };

  private reducedMotionEnabled(): boolean {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}

if (!customElements.get('ascii-frame-animation')) {
  customElements.define('ascii-frame-animation', AsciiFrameAnimation);
}
