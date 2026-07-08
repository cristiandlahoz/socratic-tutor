import './message-item.js';
import { normalizeArrayProperty } from 'Frontend/shared/dom-utils.js';
import { haptic } from 'Frontend/shared/haptics.js';
import { LitElement, html } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import type { BrailleSpinnerName } from './braille-spinners.js';

type MessageVariant = 'user' | 'assistant';

type MessageItemModel = {
  text?: string;
  time?: string;
  userName?: string;
  variant?: MessageVariant;
  loading?: boolean;
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

  setItemText(text: string | null | undefined, index: number): void {
    if (!this.hasItemAt(index)) {
      return;
    }

    const nextItems = [...this.items];
    nextItems[index] = { ...nextItems[index], text: text ?? '' };

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
    const busy = this.items.some(item => Boolean(item.loading));

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
