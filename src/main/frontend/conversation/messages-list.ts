import './message-item.js';
import { LitElement, html } from 'lit';
import type { BrailleSpinnerName } from './braille-spinners.js';

type MessageVariant = 'user' | 'assistant';

type MessageItemModel = {
  text?: string;
  time?: string;
  userName?: string;
  variant?: MessageVariant;
  loading?: boolean;
};

type ScrollMode = 'auto' | 'force' | 'none';

function normalizeItems(items: unknown): MessageItemModel[] {
  if (typeof items === 'string') {
    return JSON.parse(items) as MessageItemModel[];
  }
  return Array.isArray(items) ? (items as MessageItemModel[]) : [];
}

function sameMessageIdentity(a: MessageItemModel | undefined, b: MessageItemModel | undefined): boolean {
  return (
    a?.time === b?.time &&
    a?.userName === b?.userName &&
    a?.variant === b?.variant &&
    a?.loading === b?.loading
  );
}

function isAppendOrTextGrowth(previous: MessageItemModel[], next: MessageItemModel[]): boolean {
  if (previous.length === 0) {
    return next.length === 0;
  }
  if (next.length < previous.length) {
    return false;
  }

  const sharedCount = Math.min(previous.length, next.length);
  for (let index = 0; index < sharedCount - 1; index += 1) {
    const previousItem = previous[index];
    const nextItem = next[index];
    if (!sameMessageIdentity(previousItem, nextItem) || previousItem?.text !== nextItem?.text) {
      return false;
    }
  }

  const previousLast = previous[sharedCount - 1];
  const nextLast = next[sharedCount - 1];
  return (
    sameMessageIdentity(previousLast, nextLast) &&
    (nextLast?.text ?? '').startsWith(previousLast?.text ?? '')
  );
}

class MessagesList extends LitElement {
  static properties = {
    items: { type: Array },
    thinkingSpinner: { type: String, attribute: 'thinking-spinner' },
  };

  declare items: MessageItemModel[];
  declare thinkingSpinner: BrailleSpinnerName;

  private readonly minAutoScrollThreshold = 72;
  private readonly maxAutoScrollThresholdRatio = 0.12;
  private readonly rapidScrollIntervalMs = 120;
  private readonly smoothScrollReleaseMs = 260;
  private autoScrollEnabled = true;
  private programmaticScroll = false;
  private scrollTarget: HTMLElement | null = null;
  private scrollFrame = 0;
  private scrollReleaseTimer = 0;
  private lastScrollAt = 0;
  private readonly handleScroll = () => this.updateAutoScrollState();

  constructor() {
    super();
    this.items = [];
    this.thinkingSpinner = 'braille';
  }

  connectedCallback(): void {
    super.connectedCallback();
    requestAnimationFrame(() => this.attachScrollTarget());
  }

  disconnectedCallback(): void {
    this.detachScrollTarget();
    super.disconnectedCallback();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setItems(items: unknown): void {
    const nextItems = normalizeItems(items);
    const forceScroll = this.items.length === 0 || !isAppendOrTextGrowth(this.items, nextItems);
    this.updateItems(nextItems, forceScroll ? 'force' : 'auto');
  }

  addItems(items: unknown): void {
    const nextItems = [...this.items, ...normalizeItems(items)];
    this.updateItems(nextItems, 'auto');
  }

  setItemText(text: string, index: number): void {
    const nextItems = [...this.items];
    nextItems[index] = { ...nextItems[index], text };
    this.updateItems(nextItems, 'auto');
  }

  appendItemText(diff: string, index: number): void {
    const nextItems = [...this.items];
    const item = nextItems[index] ?? {};
    nextItems[index] = { ...item, text: `${item.text ?? ''}${diff ?? ''}` };
    this.updateItems(nextItems, 'auto');
  }

  protected render() {
    return html`
      <div role="list" class="messages-list__items">
        ${this.items.map((item) => this.renderMessage(item))}
      </div>
    `;
  }

  private updateItems(items: MessageItemModel[], scrollMode: ScrollMode): void {
    this.attachScrollTarget();
    const shouldKeepPinnedToBottom = scrollMode === 'force' || this.shouldAutoScroll();
    this.items = items;

    if (scrollMode === 'none' || !shouldKeepPinnedToBottom) {
      return;
    }

    this.updateComplete.then(() => {
      this.scheduleScrollToBottom(scrollMode === 'auto' ? this.resolveAutoScrollBehavior() : 'auto');
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
    if (this.scrollFrame) {
      cancelAnimationFrame(this.scrollFrame);
      this.scrollFrame = 0;
    }
    if (this.scrollReleaseTimer) {
      window.clearTimeout(this.scrollReleaseTimer);
      this.scrollReleaseTimer = 0;
    }
  }

  private resolveScrollTarget(): HTMLElement {
    return this.closest<HTMLElement>('.conversation-view__scroll-region') ?? this;
  }

  private shouldAutoScroll(): boolean {
    return this.autoScrollEnabled || this.isCloseToBottom();
  }

  private isCloseToBottom(): boolean {
    const target = this.scrollTarget ?? this.resolveScrollTarget();
    const threshold = Math.max(this.minAutoScrollThreshold, target.clientHeight * this.maxAutoScrollThresholdRatio);
    const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    return distanceToBottom <= threshold;
  }

  private updateAutoScrollState(): void {
    if (this.isCloseToBottom()) {
      this.autoScrollEnabled = true;
      return;
    }
    if (!this.programmaticScroll) {
      this.autoScrollEnabled = false;
    }
  }

  private scheduleScrollToBottom(behavior: ScrollBehavior): void {
    if (this.scrollFrame) {
      cancelAnimationFrame(this.scrollFrame);
    }

    this.scrollFrame = requestAnimationFrame(() => {
      this.scrollFrame = 0;
      this.scrollToBottom(behavior);
    });
  }

  private resolveAutoScrollBehavior(): ScrollBehavior {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return 'auto';
    }

    const now = performance.now();
    const rapidUpdate = now - this.lastScrollAt < this.rapidScrollIntervalMs;
    this.lastScrollAt = now;
    return rapidUpdate ? 'auto' : 'smooth';
  }

  private scrollToBottom(behavior: ScrollBehavior): void {
    const target = this.scrollTarget ?? this.resolveScrollTarget();
    this.autoScrollEnabled = true;
    this.programmaticScroll = true;
    target.scrollTo({ top: target.scrollHeight, behavior });

    if (this.scrollReleaseTimer) {
      window.clearTimeout(this.scrollReleaseTimer);
    }

    this.scrollReleaseTimer = window.setTimeout(() => {
      this.programmaticScroll = false;
      this.scrollReleaseTimer = 0;
      this.updateAutoScrollState();
    }, behavior === 'smooth' ? this.smoothScrollReleaseMs : 0);
  }

  private renderMessage(item: MessageItemModel) {
    const variant = item.variant ?? 'assistant';

    return html`<message-item
      role="listitem"
      .text=${item.text ?? ''}
      .time=${item.loading ? '' : item.time ?? ''}
      .userName=${item.loading ? '' : item.userName ?? ''}
      .variant=${variant}
      .loading=${Boolean(item.loading)}
      .thinkingSpinner=${this.thinkingSpinner}
    ></message-item>`;
  }
}

if (!customElements.get('messages-list')) {
  customElements.define('messages-list', MessagesList);
}
