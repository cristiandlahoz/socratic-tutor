import '@vaadin/split-layout';
import { LitElement } from 'lit';

const SPLIT_CLASS = 'conversation-view__debug-split';
const COLLAPSED_CLASS = 'conversation-view__debug-split--collapsed';
const ANIMATING_CLASS = 'conversation-view__debug-split--animating';
const PRIMARY_SLOT = 'primary';
const SECONDARY_SLOT = 'secondary';
const ANIMATION_MS = 220;

type SplitLayoutElement = HTMLElement & {
  orientation: string;
};

class ConversationDebugSplit extends LitElement {
  static readonly properties = {
    debuggerVisible: { type: Boolean, attribute: 'debugger-visible' },
  };

  declare debuggerVisible: boolean;

  private splitLayout: SplitLayoutElement | null = null;
  private animationTimer: ReturnType<typeof globalThis.setTimeout> | undefined;
  private animationFrame = 0;
  private mutationObserver: MutationObserver | null = null;

  constructor() {
    super();
    this.debuggerVisible = false;
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.style.display = 'block';
    this.style.width = '100%';
    this.style.height = '100%';
    this.ensureSplitLayout();
    this.installMutationObserver();
    this.updateSplitState(false);
  }

  disconnectedCallback(): void {
    this.clearAnimation();
    this.mutationObserver?.disconnect();
    this.mutationObserver = null;
    super.disconnectedCallback();
  }

  protected updated(changedProperties: Map<PropertyKey, unknown>): void {
    if (changedProperties.has('debuggerVisible')) {
      this.updateSplitState(true);
    }
  }

  setDebuggerVisible(value: boolean): void {
    this.debuggerVisible = Boolean(value);
  }

  private installMutationObserver(): void {
    if (this.mutationObserver) {
      return;
    }

    this.mutationObserver = new MutationObserver(() => this.ensureSplitLayout());
    this.mutationObserver.observe(this, { childList: true });
  }

  private ensureSplitLayout(): SplitLayoutElement {
    const existing = this.querySelector<SplitLayoutElement>('vaadin-split-layout');
    if (existing) {
      this.splitLayout = existing;
      this.configureSplitLayout(existing);
      this.moveLooseChildren(existing);
      return existing;
    }

    const split = document.createElement('vaadin-split-layout') as SplitLayoutElement;
    this.configureSplitLayout(split);
    this.splitLayout = split;
    this.moveLooseChildren(split);
    this.append(split);
    return split;
  }

  private configureSplitLayout(split: SplitLayoutElement): void {
    split.classList.add(SPLIT_CLASS);
    split.orientation = 'horizontal';
    split.setAttribute('splitter-position', '58');
    split.style.width = '100%';
    split.style.height = '100%';
  }

  private moveLooseChildren(split: SplitLayoutElement): void {
    const looseChildren = Array.from(this.children).filter((child) => child !== split);
    for (const child of looseChildren) {
      if (!child.hasAttribute('slot')) {
        child.setAttribute('slot', split.querySelector('[slot="primary"]') ? SECONDARY_SLOT : PRIMARY_SLOT);
      }
      split.append(child);
    }
  }

  private updateSplitState(animate: boolean): void {
    const split = this.ensureSplitLayout();
    const primary = split.querySelector<HTMLElement>('[slot="primary"]');
    const secondary = split.querySelector<HTMLElement>('[slot="secondary"]');

    if (!primary || !secondary) {
      return;
    }

    this.clearAnimation();
    if (animate) {
      split.classList.add(ANIMATING_CLASS);
      this.animationTimer = globalThis.setTimeout(() => {
        split.classList.remove(ANIMATING_CLASS);
        this.animationTimer = undefined;
      }, ANIMATION_MS);
    }

    if (this.debuggerVisible) {
      split.classList.add(COLLAPSED_CLASS);
      this.collapse(primary, secondary);
      this.animationFrame = globalThis.requestAnimationFrame(() => {
        split.classList.remove(COLLAPSED_CLASS);
        this.expand(primary, secondary);
      });
      return;
    }

    split.classList.remove(COLLAPSED_CLASS);
    this.animationFrame = globalThis.requestAnimationFrame(() => {
      split.classList.add(COLLAPSED_CLASS);
      this.collapse(primary, secondary);
    });
  }

  private expand(primary: HTMLElement, secondary: HTMLElement): void {
    primary.style.flex = '1 1 calc(100% - var(--vaadin-app-layout-drawer-width))';
    secondary.style.flex = '0 0 var(--vaadin-app-layout-drawer-width)';
  }

  private collapse(primary: HTMLElement, secondary: HTMLElement): void {
    primary.style.flex = '1 1 100%';
    secondary.style.flex = '0 1 0%';
  }

  private clearAnimation(): void {
    if (this.animationTimer) {
      globalThis.clearTimeout(this.animationTimer);
      this.animationTimer = undefined;
    }
    if (this.animationFrame) {
      globalThis.cancelAnimationFrame(this.animationFrame);
      this.animationFrame = 0;
    }
    this.splitLayout?.classList.remove(ANIMATING_CLASS);
  }
}

if (!customElements.get('conversation-debug-split')) {
  customElements.define('conversation-debug-split', ConversationDebugSplit);
}
