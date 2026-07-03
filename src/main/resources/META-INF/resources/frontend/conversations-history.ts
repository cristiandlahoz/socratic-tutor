import '@vaadin/button';
import { LitElement, html, nothing } from 'lit';
import { classMap } from 'lit/directives/class-map.js';
import { repeat } from 'lit/directives/repeat.js';

const SPANISH_LOCALE = 'es-DO';
const SVG_NS = 'http://www.w3.org/2000/svg';

type ConversationHistoryItem = {
  id: string;
  title: string;
  updatedAt: string;
};

type TimelineDividerEntry = {
  type: 'divider';
  label: string;
  nodeId: string;
  lane: 'divider';
};

type TimelineThreadEntry = {
  type: 'thread';
  conversation: ConversationHistoryItem;
  label: string;
  nodeId: string;
  lane: 'thread';
};

type TimelineEntry = TimelineDividerEntry | TimelineThreadEntry;

type TimelinePoint = {
  node: HTMLElement;
  id: string;
  x: number;
  y: number;
};

function normalizeConversations(value: unknown): ConversationHistoryItem[] {
  if (typeof value === 'string') {
    return JSON.parse(value) as ConversationHistoryItem[];
  }
  return Array.isArray(value) ? (value as ConversationHistoryItem[]) : [];
}

class ConversationsHistory extends LitElement {
  static properties = {
    conversations: { type: Array },
    activeConversationId: { type: String, attribute: 'active-conversation-id' },
    disabled: { type: Boolean, reflect: true },
  };

  declare conversations: ConversationHistoryItem[];
  declare activeConversationId: string | null;
  declare disabled: boolean;

  private resizeObserver: ResizeObserver | null = null;
  private animationFrame: number | null = null;
  private previousActiveNodeId: string | null = null;
  private drawFrame: number | null = null;
  private readonly handleTimelineScroll = () => this.scheduleDrawGraph();

  constructor() {
    super();
    this.conversations = [];
    this.activeConversationId = null;
    this.disabled = false;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.style.display = 'block';
    this.style.width = '100%';
    this.style.height = '100%';
  }

  disconnectedCallback(): void {
    this.cleanupGraph();
    super.disconnectedCallback();
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setConversations(conversations: unknown): void {
    this.conversations = normalizeConversations(conversations);
  }

  setActiveConversationId(activeConversationId: string | null | undefined): void {
    this.activeConversationId = activeConversationId ?? null;
  }

  setDisabled(disabled: boolean): void {
    this.disabled = disabled;
  }

  protected updated(): void {
    this.installGraphObservers();
    this.scheduleDrawGraph();
  }

  protected render() {
    const entries = this.buildTimelineEntries(this.conversations);

    return html`
      ${this.renderComponentStyles()}
      <div class="conversation-history__header">
        <div
          class="conversations-history-title-row"
          title="Hilos recientes ordenados por fecha y conectados como una sola ruta."
        >
          <h1 class="conversations-history-title">Historial</h1>
          <span class="conversations-history-count">${this.formatConversationCount(this.conversations.length)}</span>
        </div>
      </div>
      <div class="conversations-history-section">
        <div class="conversations-history-body">
          ${this.conversations.length === 0 ? this.renderEmptyState() : nothing}
          <div class="conversations-history-timeline" ?hidden=${this.conversations.length === 0}>
            <svg class="conversations-history-timeline-edges" aria-hidden="true"></svg>
            <svg class="conversations-history-timeline-effects" aria-hidden="true"></svg>
            <div class="conversations-history-timeline-rows">
              ${repeat(entries, (entry) => entry.nodeId, (entry) => this.renderEntry(entry))}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  private renderComponentStyles() {
    return html`
      <style>
        .conversations-history-timeline-edges {
          position: absolute;
          inset: 0;
          z-index: 0;
          pointer-events: none;
          overflow: visible;
        }

        .conversations-history-timeline-effects {
          position: absolute;
          inset: 0;
          z-index: 2;
          pointer-events: none;
          overflow: visible;
        }

        .conversations-history-edge {
          fill: none;
          stroke: var(--trail-line-color);
          stroke-width: var(--trail-line-width);
          stroke-linecap: round;
          stroke-linejoin: round;
          opacity: 0.62;
          transition: stroke var(--motion-fast), opacity var(--motion-fast);
        }

        .conversations-history-edge.touches-active {
          stroke: var(--trail-line-active-color);
          opacity: 0.82;
        }

        .conversations-history-travel-path {
          fill: none;
          stroke: transparent;
        }

        .conversations-history-traveler-glow {
          fill: color-mix(in srgb, var(--trail-node-active-color) 22%, transparent);
        }

        .conversations-history-traveler-core {
          fill: var(--trail-node-active-color);
        }
      </style>
    `;
  }

  private renderEmptyState() {
    return html`
      <div class="conversations-history-empty">
        <span class="conversations-history-empty-title">No hay conversaciones</span>
        <p class="conversations-history-empty-description">
          Inicia una nueva conversación para empezar a construir tu historial del tutor.
        </p>
      </div>
    `;
  }

  private renderEntry(entry: TimelineEntry) {
    return entry.type === 'divider' ? this.renderDividerEntry(entry) : this.renderThreadEntry(entry);
  }

  private renderDividerEntry(entry: TimelineDividerEntry) {
    return html`
      <div class="conversations-history-entry-row conversations-history-divider-row">
        ${this.renderNode(entry)}
        <span class="conversations-history-divider-label">${entry.label}</span>
      </div>
    `;
  }

  private renderThreadEntry(entry: TimelineThreadEntry) {
    const active = entry.conversation.id === this.activeConversationId;
    const rowClasses = {
      'conversations-history-entry-row': true,
      'conversations-history-thread-row': true,
      'is-active': active,
    };

    return html`
      <vaadin-button
        class="conversations-history-item-button"
        theme="tertiary"
        title=${entry.conversation.title}
        aria-label=${entry.conversation.title}
        aria-current=${active ? 'page' : nothing}
        ?disabled=${this.disabled}
        @click=${() => this.openConversation(entry.conversation.id)}
      >
        <div class=${classMap(rowClasses)}>
          ${this.renderNode(entry, active)}
          <span class="conversations-history-item-title">${entry.conversation.title}</span>
        </div>
      </vaadin-button>
    `;
  }

  private renderNode(entry: TimelineEntry, active = false) {
    const classes = {
      'conversations-history-node': true,
      'is-active': active,
    };

    return html`
      <div class=${classMap(classes)} data-node-id=${entry.nodeId} data-lane=${entry.lane}></div>
    `;
  }

  private openConversation(conversationId: string): void {
    if (this.disabled) {
      return;
    }

    this.dispatchEvent(new CustomEvent('conversation-open-requested', {
      detail: { conversationId },
      bubbles: true,
      composed: true,
    }));
  }

  private buildTimelineEntries(conversations: ConversationHistoryItem[]): TimelineEntry[] {
    const grouped = new Map<string, ConversationHistoryItem[]>();

    for (const conversation of conversations) {
      const dayKey = this.toConversationDayKey(conversation);
      grouped.set(dayKey, [...(grouped.get(dayKey) ?? []), conversation]);
    }

    const entries: TimelineEntry[] = [];
    let dividerIndex = 0;
    for (const [dayKey, groupedConversations] of grouped) {
      entries.push({
        type: 'divider',
        label: this.formatDayLabel(dayKey),
        nodeId: `divider-${dividerIndex++}`,
        lane: 'divider',
      });
      for (const conversation of groupedConversations) {
        entries.push({
          type: 'thread',
          conversation,
          label: conversation.title,
          nodeId: `thread-${conversation.id}`,
          lane: 'thread',
        });
      }
    }

    return entries;
  }

  private toConversationDayKey(conversation: ConversationHistoryItem): string {
    const date = new Date(conversation.updatedAt);
    if (Number.isNaN(date.getTime())) {
      return conversation.updatedAt.slice(0, 10);
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private formatDayLabel(dayKey: string): string {
    const day = this.fromDayKey(dayKey);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);

    if (this.sameLocalDay(day, today)) {
      return 'Hoy';
    }
    if (this.sameLocalDay(day, yesterday)) {
      return 'Ayer';
    }

    return new Intl.DateTimeFormat(SPANISH_LOCALE, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(day);
  }

  private fromDayKey(dayKey: string): Date {
    const [year = '0', month = '1', day = '1'] = dayKey.split('-');
    return new Date(Number(year), Number(month) - 1, Number(day));
  }

  private sameLocalDay(first: Date, second: Date): boolean {
    return first.getFullYear() === second.getFullYear()
      && first.getMonth() === second.getMonth()
      && first.getDate() === second.getDate();
  }

  private formatConversationCount(count: number): string {
    return `${count} hilos`;
  }

  private installGraphObservers(): void {
    const timeline = this.timelineRoot;
    if (!timeline || timeline.dataset.graphObserversInstalled === 'true') {
      return;
    }

    this.resizeObserver?.disconnect();
    this.resizeObserver = new ResizeObserver(() => this.scheduleDrawGraph());
    this.resizeObserver.observe(timeline);

    const rows = this.timelineRows;
    if (rows) {
      this.resizeObserver.observe(rows);
    }

    timeline.addEventListener('scroll', this.handleTimelineScroll, { passive: true });
    timeline.dataset.graphObserversInstalled = 'true';
  }

  private cleanupGraph(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.timelineRoot?.removeEventListener('scroll', this.handleTimelineScroll);
    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
    if (this.drawFrame !== null) {
      cancelAnimationFrame(this.drawFrame);
      this.drawFrame = null;
    }
  }

  private scheduleDrawGraph(): void {
    if (this.drawFrame !== null) {
      cancelAnimationFrame(this.drawFrame);
    }
    this.drawFrame = requestAnimationFrame(() => {
      this.drawFrame = null;
      this.drawGraph();
    });
  }

  private drawGraph(): void {
    const timeline = this.timelineRoot;
    const staticSvg = this.staticSvg;
    const fxSvg = this.fxSvg;
    if (!timeline || !staticSvg || !fxSvg) {
      return;
    }

    const nodes = [...timeline.querySelectorAll<HTMLElement>('.conversations-history-node')];
    const rootRect = timeline.getBoundingClientRect();
    const width = Math.max(1, timeline.clientWidth);
    const height = Math.max(1, timeline.scrollHeight);

    [staticSvg, fxSvg].forEach((layer) => {
      layer.setAttribute('viewBox', `0 0 ${width} ${height}`);
      layer.setAttribute('width', `${width}`);
      layer.setAttribute('height', `${height}`);
    });
    staticSvg.replaceChildren();

    if (nodes.length < 2) {
      return;
    }

    const points = nodes.map((node) => this.buildPoint(node, rootRect, timeline.scrollTop));

    for (let index = 0; index < points.length - 1; index += 1) {
      const fromPoint = points[index];
      const toPoint = points[index + 1];
      const path = document.createElementNS(SVG_NS, 'path');
      path.classList.add('conversations-history-edge');
      if (fromPoint.node.classList.contains('is-active') || toPoint.node.classList.contains('is-active')) {
        path.classList.add('touches-active');
      }

      const pathParts = [`M ${fromPoint.x} ${fromPoint.y}`];
      this.appendSegment(pathParts, fromPoint, toPoint);
      path.setAttribute('d', pathParts.join(' '));
      staticSvg.append(path);
    }

    const currentActiveNode = nodes.find((node) => node.classList.contains('is-active'));
    const currentActiveId = currentActiveNode?.dataset.nodeId ?? null;

    if (this.previousActiveNodeId && currentActiveId && this.previousActiveNodeId !== currentActiveId) {
      this.animateTraveler(points, this.previousActiveNodeId, currentActiveId);
    }

    this.previousActiveNodeId = currentActiveId;
  }

  private buildPoint(node: HTMLElement, rootRect: DOMRect, scrollTop: number): TimelinePoint {
    const rect = node.getBoundingClientRect();
    return {
      node,
      id: node.dataset.nodeId ?? '',
      x: rect.left - rootRect.left + rect.width / 2,
      y: rect.top - rootRect.top + rect.height / 2 + scrollTop,
    };
  }

  private appendSegment(pathParts: string[], fromPoint: TimelinePoint, toPoint: TimelinePoint): void {
    if (Math.abs(fromPoint.x - toPoint.x) < 1) {
      pathParts.push(`L ${toPoint.x} ${toPoint.y}`);
      return;
    }

    const midY = (fromPoint.y + toPoint.y) / 2;
    pathParts.push(`C ${fromPoint.x} ${midY}, ${toPoint.x} ${midY}, ${toPoint.x} ${toPoint.y}`);
  }

  private animateTraveler(points: TimelinePoint[], fromId: string, toId: string): void {
    const fxSvg = this.fxSvg;
    if (!fxSvg) {
      return;
    }

    const startIndex = points.findIndex((point) => point.id === fromId);
    const endIndex = points.findIndex((point) => point.id === toId);
    if (startIndex < 0 || endIndex < 0 || startIndex === endIndex) {
      return;
    }

    const routePoints = startIndex < endIndex
      ? points.slice(startIndex, endIndex + 1)
      : points.slice(endIndex, startIndex + 1).reverse();

    const routePath = document.createElementNS(SVG_NS, 'path');
    routePath.classList.add('conversations-history-travel-path');
    const pathParts = [`M ${routePoints[0].x} ${routePoints[0].y}`];
    for (let index = 0; index < routePoints.length - 1; index += 1) {
      this.appendSegment(pathParts, routePoints[index], routePoints[index + 1]);
    }
    routePath.setAttribute('d', pathParts.join(' '));
    fxSvg.replaceChildren();
    fxSvg.append(routePath);

    const glow = document.createElementNS(SVG_NS, 'circle');
    glow.classList.add('conversations-history-traveler-glow');
    glow.setAttribute('r', '12');
    glow.setAttribute('opacity', '0.7');

    const core = document.createElementNS(SVG_NS, 'circle');
    core.classList.add('conversations-history-traveler-core');
    core.setAttribute('r', '5.2');

    const totalLength = routePath.getTotalLength();
    const duration = 420;
    const startedAt = performance.now();
    const startPoint = routePath.getPointAtLength(0);

    glow.setAttribute('cx', `${startPoint.x}`);
    glow.setAttribute('cy', `${startPoint.y}`);
    core.setAttribute('cx', `${startPoint.x}`);
    core.setAttribute('cy', `${startPoint.y}`);

    fxSvg.append(glow, core);

    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
    }

    const move = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      const point = routePath.getPointAtLength(totalLength * eased);
      glow.setAttribute('cx', `${point.x}`);
      glow.setAttribute('cy', `${point.y}`);
      glow.setAttribute('opacity', `${0.7 * (1 - progress * 0.28)}`);
      core.setAttribute('cx', `${point.x}`);
      core.setAttribute('cy', `${point.y}`);

      if (progress < 1) {
        this.animationFrame = requestAnimationFrame(move);
      } else {
        fxSvg.replaceChildren();
        this.animationFrame = null;
      }
    };

    this.animationFrame = requestAnimationFrame(move);
  }

  private get timelineRoot(): HTMLElement | null {
    return this.querySelector<HTMLElement>('.conversations-history-timeline');
  }

  private get timelineRows(): HTMLElement | null {
    return this.querySelector<HTMLElement>('.conversations-history-timeline-rows');
  }

  private get staticSvg(): SVGSVGElement | null {
    return this.querySelector<SVGSVGElement>('.conversations-history-timeline-edges');
  }

  private get fxSvg(): SVGSVGElement | null {
    return this.querySelector<SVGSVGElement>('.conversations-history-timeline-effects');
  }
}

if (!customElements.get('conversations-history')) {
  customElements.define('conversations-history', ConversationsHistory);
}
