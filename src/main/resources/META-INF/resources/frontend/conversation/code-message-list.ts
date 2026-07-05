import './code-message-body.ts';
import './braille-spinner.ts';
import '@vaadin/message-list/src/vaadin-message.js';
import { LitElement, html } from 'lit';
import { ifDefined } from 'lit/directives/if-defined.js';
import type { BrailleSpinnerName } from './braille-spinners';

type MessageItem = {
  text?: string;
  time?: string;
  userName?: string;
  userColorIndex?: number;
  className?: string;
  theme?: string;
};

type ScrollMode = 'auto' | 'force' | 'none';

function normalizeItems(items: unknown): MessageItem[] {
  if (typeof items === 'string') {
    return JSON.parse(items) as MessageItem[];
  }
  return Array.isArray(items) ? (items as MessageItem[]) : [];
}

function sameMessageIdentity(a: MessageItem | undefined, b: MessageItem | undefined): boolean {
  return a?.time === b?.time && a?.userName === b?.userName && a?.className === b?.className && a?.theme === b?.theme;
}

function isAppendOrTextGrowth(previous: MessageItem[], next: MessageItem[]): boolean {
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

class CodeMessageList extends LitElement {
  static properties = {
    items: { type: Array },
    thinkingSpinner: { type: String, attribute: 'thinking-spinner' },
  };

  declare items: MessageItem[];
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
      <div part="list" role="list" class="code-message-list__list">
        ${this.items.map((item) => this.renderMessage(item))}
      </div>
    `;
  }

  private updateItems(items: MessageItem[], scrollMode: ScrollMode): void {
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

  private renderMessage(item: MessageItem) {
    const loading = this.isLoadingItem(item);
    return html`
      <vaadin-message
        role="listitem"
        .time=${loading ? '' : item.time ?? ''}
        .userName=${loading ? '' : item.userName ?? ''}
        .userColorIndex=${item.userColorIndex ?? 0}
        theme=${ifDefined(item.theme)}
        class=${ifDefined(item.className)}
      >${loading
        ? html`<braille-spinner .spinner=${this.thinkingSpinner}></braille-spinner>`
        : html`<code-message-body
            .text=${item.text ?? ''}
            .markdown=${this.isAssistantItem(item)}
            .debuggableCodeBlocks=${this.isAssistantItem(item)}
          ></code-message-body>`}</vaadin-message>
    `;
  }

  private isLoadingItem(item: MessageItem): boolean {
    return item.className?.split(/\s+/).includes('is-loading') ?? false;
  }

  private isAssistantItem(item: MessageItem): boolean {
    return item.className?.split(/\s+/).includes('conversation-message--assistant') ?? false;
  }
}

if (!customElements.get('code-message-list')) {
  customElements.define('code-message-list', CodeMessageList);
}
