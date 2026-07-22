import './message-item.js';
import { normalizeArrayProperty } from 'Frontend/shared/dom-utils.js';
import { haptic } from 'Frontend/shared/haptics.js';
import { LitElement, css, html } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { BrailleSpinnerName } from './braille-spinners.js';

type MessageVariant = 'user' | 'assistant';

/*
 * The thinking and solving orbs below are adapted from thinking-orbs:
 * https://github.com/Jakubantalik/thinking-orbs
 *
 * MIT License
 * Copyright (c) 2026 Jakub Antalik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

type OrbDot = {
  x: number;
  y: number;
  z: number;
  radius: number;
  opacity: number;
};

type OrbMove = {
  axis: 0 | 1 | 2;
  low: number;
  high: number;
  angle: number;
};

type SolveCycle = {
  amounts: number[];
  active: number;
};

const SOLVING_ORB_SIZE = 20;
const SOLVING_ORB_SPEED = 1.95;
const SOLVING_ORB_MOVE_COUNT = 14;
const THINKING_ORB_DURATION_SECONDS = 2;
const THINKING_ORB_SPEED = 3.12;

function fibonacciDirection(index: number, count: number): [number, number, number] {
  const goldenAngle = Math.PI * (3 - Math.sqrt(5));
  const y = 1 - (2 * (index + 0.5)) / count;
  const radius = Math.sqrt(1 - y * y);
  const angle = index * goldenAngle;
  return [radius * Math.cos(angle), y, radius * Math.sin(angle)];
}

function drawThinkingOrb(
  context: CanvasRenderingContext2D,
  time: number,
  color: string,
): void {
  const center = SOLVING_ORB_SIZE / 2;
  const scale = center * 0.78;
  const tilt = 0.3;
  const sineTilt = Math.sin(tilt);
  const cosineTilt = Math.cos(tilt);
  const radiusScale = (SOLVING_ORB_SIZE / 300) ** 0.6;
  const dots: OrbDot[] = [];

  const project = (x: number, y: number, z: number): [number, number, number] => {
    const projectedY = y * cosineTilt - z * sineTilt;
    const projectedZ = y * sineTilt + z * cosineTilt;
    return [center + x * scale, center - projectedY * scale, projectedZ];
  };

  for (let index = 0; index < 8; index++) {
    const [x, y, z] = fibonacciDirection(index, 8);
    const [projectedX, projectedY, projectedZ] = project(x, y, z);
    const depth = (projectedZ + 1) / 2;
    dots.push({
      x: projectedX,
      y: projectedY,
      z: projectedZ,
      radius: 0.8 * radiusScale,
      opacity: 0.1 + 0.22 * depth,
    });
  }

  const planeTilt = 0.55;
  const planeX = 1;
  const planeZ = 0;
  const tangentX = -planeZ * Math.sin(planeTilt);
  const tangentY = Math.cos(planeTilt);
  const tangentZ = planeX * Math.sin(planeTilt);
  const normalX = -planeZ * tangentY;
  const normalY = planeZ * tangentX - planeX * tangentZ;
  const normalZ = planeX * tangentY;

  for (let lane = 0; lane < 10; lane++) {
    const laneOffset = (lane - 4.5) * 0.075;
    const edge = Math.abs(lane - 4.5) / 4.5;
    for (let segment = 0; segment < 20; segment++) {
      const angle = (segment / 20) * Math.PI * 2;
      const wobble =
        0.16 * Math.sin(angle * 3 - time * 1.7 + lane * 0.22)
        + 0.07 * Math.sin(angle * 5 + time * 1.1);
      const offset = laneOffset + wobble;
      const x = planeX * Math.cos(angle) + tangentX * Math.sin(angle) + normalX * offset;
      const y = tangentY * Math.sin(angle) + normalY * offset;
      const z = planeZ * Math.cos(angle) + tangentZ * Math.sin(angle) + normalZ * offset;
      const length = Math.sqrt(x * x + y * y + z * z);
      const [projectedX, projectedY, projectedZ] = project(
        x / length,
        y / length,
        z / length,
      );
      const depth = (projectedZ + 1) / 2;

      dots.push({
        x: projectedX,
        y: projectedY,
        z: projectedZ,
        radius: (1.1803 + 1.8241 * depth) * (1 - 0.25 * edge) * radiusScale,
        opacity: 0.4 + 0.6 * depth,
      });
    }
  }

  dots.sort((left, right) => left.z - right.z);
  for (const dot of dots) {
    context.globalAlpha = dot.opacity;
    context.fillStyle = color;
    context.beginPath();
    context.arc(dot.x, dot.y, Math.max(0.3, dot.radius), 0, Math.PI * 2);
    context.fill();
  }
  context.globalAlpha = 1;
}

function orbHash(a: number, b: number): number {
  const hash = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
  return hash - Math.floor(hash);
}

function solvingMoves(): OrbMove[] {
  return Array.from({ length: SOLVING_ORB_MOVE_COUNT }, (_, index) => {
    const axis = Math.min(2, Math.floor(orbHash(index, 2.3) * 3)) as 0 | 1 | 2;
    const low = -1 + 0.5 * Math.min(3, Math.floor(orbHash(index, 5.9) * 4));
    const direction = orbHash(index, 7.7) < 0.5 ? 1 : -1;
    return { axis, low, high: low + 0.5, angle: direction * Math.PI / 2 };
  });
}

const SOLVING_MOVES = solvingMoves();

function solveCycle(time: number): SolveCycle {
  const slotDuration = 0.42;
  const cycleTime = time % (2 * SOLVING_ORB_MOVE_COUNT * slotDuration + 1.2);
  const amounts = new Array<number>(SOLVING_ORB_MOVE_COUNT).fill(0);
  let active = -1;

  if (cycleTime >= 2 * SOLVING_ORB_MOVE_COUNT * slotDuration) {
    return { amounts, active };
  }

  const slot = Math.floor(cycleTime / slotDuration);
  const progress = (cycleTime - slot * slotDuration) / slotDuration;
  const easedProgress = 1 - (1 - Math.min(1, progress / 0.7)) ** 3;

  if (slot < SOLVING_ORB_MOVE_COUNT) {
    amounts.fill(1, 0, slot);
    amounts[slot] = easedProgress;
    active = slot;
  } else {
    const reverseSlot = 2 * SOLVING_ORB_MOVE_COUNT - 1 - slot;
    amounts.fill(1, 0, reverseSlot);
    amounts[reverseSlot] = 1 - easedProgress;
    active = reverseSlot;
  }

  return { amounts, active };
}

function applySolvingMoves(
  point: [number, number, number],
  cycle: SolveCycle,
): [number, number, number, boolean] {
  let [x, y, z] = point;
  let active = false;

  for (let index = 0; index < SOLVING_MOVES.length; index++) {
    if (cycle.amounts[index] <= 0) {
      continue;
    }

    const move = SOLVING_MOVES[index];
    const coordinate = move.axis === 0 ? x : move.axis === 1 ? y : z;
    if (coordinate < move.low || coordinate >= move.high) {
      continue;
    }

    active ||= index === cycle.active;
    const angle = move.angle * cycle.amounts[index];
    const cosine = Math.cos(angle);
    const sine = Math.sin(angle);

    if (move.axis === 0) {
      const nextY = y * cosine - z * sine;
      z = y * sine + z * cosine;
      y = nextY;
    } else if (move.axis === 1) {
      const nextX = x * cosine + z * sine;
      z = -x * sine + z * cosine;
      x = nextX;
    } else {
      const nextX = x * cosine - y * sine;
      y = x * sine + y * cosine;
      x = nextX;
    }
  }

  return [x, y, z, active];
}

function drawSolvingOrb(
  context: CanvasRenderingContext2D,
  time: number,
  color: string,
): void {
  const center = SOLVING_ORB_SIZE / 2;
  const scale = center * 0.82;
  const yaw = time * 0.55;
  const tilt = 0.35 + 0.1 * Math.sin(time * 0.9);
  const sineTilt = Math.sin(tilt);
  const cosineTilt = Math.cos(tilt);
  const sineYaw = Math.sin(yaw);
  const cosineYaw = Math.cos(yaw);
  const radiusScale = (SOLVING_ORB_SIZE / 300) ** 0.6;
  const cycle = solveCycle(time);
  const dots: OrbDot[] = [];

  for (let latitudeIndex = 0; latitudeIndex <= 4; latitudeIndex++) {
    const latitude = -Math.PI / 2 + (latitudeIndex / 4) * Math.PI;
    const cosineLatitude = Math.cos(latitude);
    const sineLatitude = Math.sin(latitude);
    const longitudeCount = Math.max(1, Math.round(Math.abs(cosineLatitude) * 12));

    for (let longitudeIndex = 0; longitudeIndex < longitudeCount; longitudeIndex++) {
      const longitude = (longitudeIndex / longitudeCount) * 2 * Math.PI;
      const [x, y, z, active] = applySolvingMoves([
        cosineLatitude * Math.cos(longitude),
        sineLatitude,
        cosineLatitude * Math.sin(longitude),
      ], cycle);
      const rotatedX = x * cosineYaw + z * sineYaw;
      const rotatedZ = -x * sineYaw + z * cosineYaw;
      const rotatedY = y * cosineTilt - rotatedZ * sineTilt;
      const depthZ = y * sineTilt + rotatedZ * cosineTilt;
      const depth = (depthZ + 1) / 2;

      dots.push({
        x: center + rotatedX * scale,
        y: center - rotatedY * scale,
        z: depthZ,
        radius: (1.14 + 3.23 * depth + (active ? 0.57 : 0)) * radiusScale,
        opacity: 0.38 + 0.54 * depth + (active ? 0.08 : 0),
      });
    }
  }

  dots.sort((left, right) => left.z - right.z);
  for (const dot of dots) {
    context.globalAlpha = Math.min(1, Math.max(0, dot.opacity));
    context.fillStyle = color;
    context.beginPath();
    context.arc(dot.x, dot.y, Math.max(0.3, dot.radius), 0, Math.PI * 2);
    context.fill();
  }
  context.globalAlpha = 1;
}

class SolvingOrb extends LitElement {
  static styles = css`
    :host {
      display: block;
      inline-size: 20px;
      block-size: 20px;
      flex: none;
      color: var(--aura-neutral);
    }

    canvas {
      display: block;
      inline-size: 100%;
      block-size: 100%;
    }
  `;

  private animationFrame = 0;
  private startedAt = 0;
  private visible = true;
  private intersectionObserver?: IntersectionObserver;
  private readonly reducedMotionQuery = globalThis.matchMedia('(prefers-reduced-motion: reduce)');
  private readonly systemThemeQuery = globalThis.matchMedia('(prefers-color-scheme: dark)');
  private readonly themeObserver = new MutationObserver(() => this.drawCurrentFrame());

  connectedCallback(): void {
    super.connectedCallback();
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    this.reducedMotionQuery.addEventListener('change', this.handleReducedMotionChange);
    this.systemThemeQuery.addEventListener('change', this.handleThemeChange);
    this.themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme-preference'],
    });
  }

  disconnectedCallback(): void {
    this.stopAnimation();
    this.intersectionObserver?.disconnect();
    this.themeObserver.disconnect();
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    this.reducedMotionQuery.removeEventListener('change', this.handleReducedMotionChange);
    this.systemThemeQuery.removeEventListener('change', this.handleThemeChange);
    super.disconnectedCallback();
  }

  protected firstUpdated(): void {
    this.startedAt = globalThis.performance.now();
    this.sizeCanvas();

    if (this.reducedMotionQuery.matches) {
      this.drawFrame(0.6);
      return;
    }

    if (typeof globalThis.IntersectionObserver === 'function') {
      this.intersectionObserver = new globalThis.IntersectionObserver(([entry]) => {
        this.visible = entry.isIntersecting;
        this.updateAnimationState();
      });
      this.intersectionObserver.observe(this);
    }

    this.drawCurrentFrame();
    this.updateAnimationState();
  }

  protected render() {
    return html`<canvas aria-hidden="true"></canvas>`;
  }

  private readonly handleVisibilityChange = (): void => this.updateAnimationState();

  private readonly handleReducedMotionChange = (): void => {
    if (this.reducedMotionQuery.matches) {
      this.stopAnimation();
      this.drawFrame(0.6);
      return;
    }
    this.updateAnimationState();
  };

  private readonly handleThemeChange = (): void => this.drawCurrentFrame();

  private sizeCanvas(): void {
    const canvas = this.canvas();
    if (!canvas) {
      return;
    }
    const pixelRatio = Math.min(2, globalThis.devicePixelRatio || 1);
    canvas.width = Math.round(SOLVING_ORB_SIZE * pixelRatio);
    canvas.height = Math.round(SOLVING_ORB_SIZE * pixelRatio);
  }

  private updateAnimationState(): void {
    if (
      !this.reducedMotionQuery.matches
      && this.visible
      && document.visibilityState !== 'hidden'
    ) {
      this.startAnimation();
    } else {
      this.stopAnimation();
    }
  }

  private startAnimation(): void {
    if (this.animationFrame) {
      return;
    }

    const animate = (): void => {
      this.drawCurrentFrame();
      this.animationFrame = globalThis.requestAnimationFrame(animate);
    };
    this.animationFrame = globalThis.requestAnimationFrame(animate);
  }

  private stopAnimation(): void {
    if (!this.animationFrame) {
      return;
    }
    globalThis.cancelAnimationFrame(this.animationFrame);
    this.animationFrame = 0;
  }

  private drawCurrentFrame(): void {
    const now = globalThis.performance.now();
    const thinking = (now - this.startedAt) / 1000 < THINKING_ORB_DURATION_SECONDS;
    const time = this.reducedMotionQuery.matches
      ? 0.6
      : now / 1000 * (thinking ? THINKING_ORB_SPEED : SOLVING_ORB_SPEED);
    this.drawFrame(time, thinking);
  }

  private drawFrame(time: number, thinking = true): void {
    const canvas = this.canvas();
    const context = canvas?.getContext('2d');
    if (!canvas || !context) {
      return;
    }

    const pixelRatio = Math.min(2, globalThis.devicePixelRatio || 1);
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    context.clearRect(0, 0, SOLVING_ORB_SIZE, SOLVING_ORB_SIZE);
    const color = getComputedStyle(this).color;
    if (thinking) {
      drawThinkingOrb(context, time, color);
    } else {
      drawSolvingOrb(context, time, color);
    }
  }

  private canvas(): HTMLCanvasElement | null {
    return this.renderRoot.querySelector('canvas');
  }
}

if (!customElements.get('solving-orb')) {
  customElements.define('solving-orb', SolvingOrb);
}

type MessageItemModel = {
  text?: string;
  time?: string;
  userName?: string;
  variant?: MessageVariant;
  loading?: boolean;
  steered?: boolean;
  loadingLabel?: string;
  debuggableCodeBlocks?: boolean;
};

type ScrollMode = 'auto' | 'force';
type ChatActivity = 'idle' | 'generating' | 'compacting';

function normalizeItems(items: unknown): MessageItemModel[] {
  return normalizeArrayProperty<MessageItemModel>(items);
}

function messageKey(item: MessageItemModel, index: number): string {
  return `${index}\u0000${item.variant ?? 'assistant'}`;
}

class MessagesList extends LitElement {
  static readonly properties = {
    items: { type: Array },
    thinkingSpinner: { type: String, attribute: 'thinking-spinner' },
    activity: { type: String },
  };

  declare items: MessageItemModel[];
  declare thinkingSpinner: BrailleSpinnerName;
  declare activity: ChatActivity;

  private readonly bottomThresholdPx = 72;
  private readonly bottomThresholdRatio = 0.12;

  private readonly rapidScrollIntervalMs = 120;
  private readonly nativeSmoothReleaseMs = 320;

  private readonly landingDistancePx = 720;
  private readonly landingDurationMs = 820;
  private readonly landingReleaseDelayMs = 40;
  private readonly settleFrames = 2;

  private autoScrollEnabled = true;
  private programmaticScroll = false;
  private atBottom = true;
  private busy = false;

  private scrollTarget: HTMLElement | null = null;
  private observedContent: Element | null = null;

  private scrollFrame = 0;
  private landingFrame = 0;
  private settleFrame = 0;
  private remainingSettleFrames = 0;

  private releaseTimer: ReturnType<typeof globalThis.setTimeout> | undefined;
  private lastAutoScrollAt = 0;

  private readonly resizeObserver =
    typeof globalThis.ResizeObserver === 'function'
      ? new globalThis.ResizeObserver(() => this.handleContentResize())
      : undefined;

  private readonly handleScroll = () => this.updateAutoScrollState();

  constructor() {
    super();
    this.items = [];
    this.thinkingSpinner = 'braille';
    this.activity = 'idle';
  }

  connectedCallback(): void {
    super.connectedCallback();

    globalThis.requestAnimationFrame(() => {
      if (!this.isConnected) {
        return;
      }

      this.attachScrollTarget();
      this.observeContent();
    });
  }

  disconnectedCallback(): void {
    this.detachScrollTarget();
    this.unobserveContent();
    super.disconnectedCallback();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected updated(): void {
    this.observeContent();
    this.notifyBusyState();
  }

  setItems(items: unknown): void {
    this.updateItems(normalizeItems(items), 'force');
  }

  scrollToBottom(): void {
    this.attachScrollTarget();

    this.updateComplete.then(() => {
      if (!this.isConnected) {
        return;
      }

      this.scheduleBottomScroll('force');
    });
  }

  addItems(items: unknown): void {
    this.updateItems([...this.items, ...normalizeItems(items)], 'auto');
  }

  setItemText(text: string | null | undefined, index: number, steered = false): void {
    if (!this.hasItemAt(index)) {
      return;
    }

    const nextItems = [...this.items];
    nextItems[index] = { ...nextItems[index], text: text ?? '', steered };

    this.updateItems(nextItems, 'auto');
  }

  setItemLoading(loading: boolean | null | undefined, index: number): void {
    if (!this.hasItemAt(index)) {
      return;
    }

    const nextItems = [...this.items];
    nextItems[index] = { ...nextItems[index], loading: Boolean(loading) };

    this.updateItems(nextItems, 'auto');
  }

  appendItemText(diff: string | null | undefined, index: number): void {
    if (!this.hasItemAt(index)) {
      return;
    }

    const nextItems = [...this.items];
    const item = nextItems[index];

    nextItems[index] = {
      ...item,
      text: `${item.text ?? ''}${diff ?? ''}`,
    };

    this.updateItems(nextItems, 'auto');
  }

  protected render() {
    return html`
      <div role="list" class="messages-list__items">
        ${repeat(this.items, messageKey, (item: MessageItemModel) => this.renderMessage(item))}
        ${this.renderCompactionMessage()}
      </div>
    `;
  }

  private hasItemAt(index: number): boolean {
    return Number.isInteger(index) && index >= 0 && index < this.items.length;
  }

  private updateItems(items: MessageItemModel[], mode: ScrollMode): void {
    this.attachScrollTarget();

    const shouldFollowBottom = mode === 'force' || this.shouldAutoScroll();
    this.items = items;

    if (!shouldFollowBottom) {
      return;
    }

    this.updateComplete.then(() => {
      if (!this.isConnected) {
        return;
      }

      this.scheduleBottomScroll(mode);
    });
  }

  private attachScrollTarget(): void {
    const target = this.resolveScrollTarget();

    if (target === this.scrollTarget) {
      return;
    }

    this.detachScrollTarget();

    this.scrollTarget = target;
    this.scrollTarget.addEventListener('scroll', this.handleScroll, { passive: true });
    this.updateAutoScrollState();
  }

  private detachScrollTarget(): void {
    this.scrollTarget?.removeEventListener('scroll', this.handleScroll);
    this.scrollTarget = null;

    this.cancelScheduledScroll();
    this.cancelLanding();
    this.cancelSettle();
    this.clearReleaseTimer();
  }

  private resolveScrollTarget(): HTMLElement {
    return this.closest<HTMLElement>('.conversation-view__scroll-region') ?? this;
  }

  private observeContent(): void {
    const content = this.querySelector<HTMLElement>('.messages-list__items');

    if (content === this.observedContent) {
      return;
    }

    this.unobserveContent();
    this.observedContent = content;

    if (this.observedContent) {
      this.resizeObserver?.observe(this.observedContent);
    }
  }

  private unobserveContent(): void {
    if (this.observedContent) {
      this.resizeObserver?.unobserve(this.observedContent);
    }

    this.observedContent = null;
  }

  private handleContentResize(): void {
    if (!this.shouldAutoScroll()) {
      return;
    }

    if (this.programmaticScroll || this.landingFrame) {
      return;
    }

    this.jumpToBottom();
  }

  private shouldAutoScroll(): boolean {
    return this.autoScrollEnabled || this.isCloseToBottom();
  }

  private isCloseToBottom(): boolean {
    const target = this.scrollTarget ?? this.resolveScrollTarget();
    const threshold = Math.max(this.bottomThresholdPx, target.clientHeight * this.bottomThresholdRatio);
    const distance = this.distanceToBottom(target);

    return distance <= threshold;
  }

  private updateAutoScrollState(): void {
    const closeToBottom = this.isCloseToBottom();
    this.notifyBottomState(closeToBottom);

    if (closeToBottom) {
      this.autoScrollEnabled = true;
      return;
    }

    if (!this.programmaticScroll) {
      this.autoScrollEnabled = false;
    }
  }

  private notifyBottomState(atBottom: boolean): void {
    if (atBottom === this.atBottom) {
      return;
    }

    this.atBottom = atBottom;
    this.dispatchEvent(new CustomEvent('bottom-state-changed', {
      detail: { atBottom },
      bubbles: true,
      composed: true,
    }));
  }

  private notifyBusyState(): void {
    const busy = this.items.some(item => Boolean(item.loading)) || this.activity === 'compacting';

    if (busy === this.busy) {
      return;
    }

    const wasBusy = this.busy;
    this.busy = busy;

    if (wasBusy && !busy) {
      haptic('done');
    }

    this.dispatchEvent(new CustomEvent('conversation-busy-changed', {
      detail: { busy },
      bubbles: true,
      composed: true,
    }));
  }

  private scheduleBottomScroll(mode: ScrollMode): void {
    this.cancelScheduledScroll();
    this.cancelLanding();

    this.scrollFrame = globalThis.requestAnimationFrame(() => {
      this.scrollFrame = 0;

      if (mode === 'force') {
        this.landAtBottom();
        return;
      }

      this.followBottom();
    });
  }

  private followBottom(): void {
    this.autoScrollEnabled = true;
    this.programmaticScroll = true;

    if (this.shouldUseNativeSmoothScroll()) {
      this.nativeSmoothToBottom();
      this.scheduleSettle();
      this.releaseProgrammaticScrollAfter(this.nativeSmoothReleaseMs);
      return;
    }

    this.jumpToBottom();
    this.scheduleSettle();
    this.releaseProgrammaticScrollAfter(0);
  }

  private landAtBottom(): void {
    this.attachScrollTarget();

    this.autoScrollEnabled = true;
    this.programmaticScroll = true;

    const target = this.scrollTarget ?? this.resolveScrollTarget();
    const bottom = this.bottomScrollTop(target);

    if (this.prefersReducedMotion() || bottom <= 0) {
      this.jumpToBottom(target);
      this.scheduleSettle();
      this.releaseProgrammaticScrollAfter(0);
      return;
    }

    target.scrollTop = this.landingStartScrollTop(bottom);
    this.animateLanding(target);
  }

  private landingStartScrollTop(bottom: number): number {
    const target = this.scrollTarget ?? this.resolveScrollTarget();
    const landingDistance = Math.max(this.landingDistancePx, target.clientHeight * 1.05);

    return Math.max(0, bottom - landingDistance);
  }

  private animateLanding(target: HTMLElement): void {
    const startedAt = globalThis.performance.now();
    let startTop = target.scrollTop;

    const step = (now: number): void => {
      const progress = Math.min((now - startedAt) / this.landingDurationMs, 1);
      const bottom = this.bottomScrollTop(target);

      if (startTop > bottom) {
        startTop = this.landingStartScrollTop(bottom);
      }

      const easedProgress = this.easeOutSine(progress);

      target.scrollTop = startTop + (bottom - startTop) * easedProgress;

      if (progress < 1) {
        this.landingFrame = globalThis.requestAnimationFrame(step);
        return;
      }

      this.landingFrame = 0;
      this.jumpToBottom(target);
      this.scheduleSettle();
      this.releaseProgrammaticScrollAfter(this.landingReleaseDelayMs);
    };

    this.landingFrame = globalThis.requestAnimationFrame(step);
  }

  private shouldUseNativeSmoothScroll(): boolean {
    if (this.prefersReducedMotion()) {
      return false;
    }

    const now = globalThis.performance.now();
    const rapidUpdate = now - this.lastAutoScrollAt < this.rapidScrollIntervalMs;

    this.lastAutoScrollAt = now;

    return !rapidUpdate;
  }

  private nativeSmoothToBottom(): void {
    const target = this.scrollTarget ?? this.resolveScrollTarget();

    target.scrollTo({
      top: this.bottomScrollTop(target),
      behavior: 'smooth',
    });
  }

  private scheduleSettle(): void {
    this.remainingSettleFrames = Math.max(this.remainingSettleFrames, this.settleFrames);

    if (this.settleFrame) {
      return;
    }

    this.settleFrame = globalThis.requestAnimationFrame(() => this.settleBottom());
  }

  private settleBottom(): void {
    this.settleFrame = 0;

    if (!this.autoScrollEnabled || this.remainingSettleFrames <= 0) {
      this.remainingSettleFrames = 0;
      return;
    }

    this.jumpToBottom();
    this.remainingSettleFrames -= 1;

    if (this.remainingSettleFrames > 0) {
      this.settleFrame = globalThis.requestAnimationFrame(() => this.settleBottom());
    }
  }

  private jumpToBottom(target = this.scrollTarget ?? this.resolveScrollTarget()): void {
    target.scrollTop = this.bottomScrollTop(target);
  }

  private bottomScrollTop(target: HTMLElement): number {
    return Math.max(0, target.scrollHeight - target.clientHeight);
  }

  private distanceToBottom(target: HTMLElement): number {
    return this.bottomScrollTop(target) - target.scrollTop;
  }

  private releaseProgrammaticScrollAfter(delayMs: number): void {
    this.clearReleaseTimer();

    this.releaseTimer = globalThis.setTimeout(() => {
      this.programmaticScroll = false;
      this.releaseTimer = undefined;
      this.updateAutoScrollState();
    }, delayMs);
  }

  private cancelScheduledScroll(): void {
    if (!this.scrollFrame) {
      return;
    }

    globalThis.cancelAnimationFrame(this.scrollFrame);
    this.scrollFrame = 0;
  }

  private cancelLanding(): void {
    if (!this.landingFrame) {
      return;
    }

    globalThis.cancelAnimationFrame(this.landingFrame);
    this.landingFrame = 0;
  }

  private cancelSettle(): void {
    if (this.settleFrame) {
      globalThis.cancelAnimationFrame(this.settleFrame);
      this.settleFrame = 0;
    }

    this.remainingSettleFrames = 0;
  }

  private clearReleaseTimer(): void {
    if (this.releaseTimer === undefined) {
      return;
    }

    globalThis.clearTimeout(this.releaseTimer);
    this.releaseTimer = undefined;
  }

  private prefersReducedMotion(): boolean {
    return globalThis.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private easeOutSine(progress: number): number {
    return Math.sin((progress * Math.PI) / 2);
  }

  private renderMessage(item: MessageItemModel) {
    const variant = item.variant ?? 'assistant';

    return html`
      <message-item
        role="listitem"
        .text=${item.text ?? ''}
        .time=${item.loading ? '' : item.time ?? ''}
        .userName=${item.loading ? '' : item.userName ?? ''}
        .variant=${variant}
        .loading=${Boolean(item.loading)}
        .steered=${Boolean(item.steered)}
        .loadingLabel=${item.loadingLabel ?? this.loadingLabel()}
        .debuggableCodeBlocks=${Boolean(item.debuggableCodeBlocks)}
        .thinkingSpinner=${this.thinkingSpinner}
      ></message-item>
    `;
  }

  private renderCompactionMessage() {
    if (this.activity !== 'compacting' || this.items.some(item => Boolean(item.loading))) {
      return null;
    }

    return html`
      <message-item
        role="listitem"
        .variant=${'assistant'}
        .loading=${true}
        .thinkingSpinner=${this.thinkingSpinner}
        .loadingLabel=${this.loadingLabel()}
      ></message-item>
    `;
  }

  private loadingLabel(): string {
    if (this.activity === 'compacting') {
      return 'Compactando el contexto…';
    }
    return 'Generando respuesta…';
  }
}

if (!customElements.get('messages-list')) {
  customElements.define('messages-list', MessagesList);
}
