import '@vaadin/button';
import 'Frontend/shell/width-aware-label.js';
import { normalizeArrayProperty } from 'Frontend/shared/dom-utils.js';
import { LitElement, html, nothing } from 'lit';
import { classMap } from 'lit/directives/class-map.js';
import { repeat } from 'lit/directives/repeat.js';

const SPANISH_LOCALE = 'es-DO';
const SVG_NS = 'http://www.w3.org/2000/svg';
const TIMELINE_CURVE_TRANSITION_ZONE = 16;

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

type TimelineGeometry = {
  points: TimelinePoint[];
  pathData: string;
};

function normalizeConversations(value: unknown): ConversationHistoryItem[] {
  return normalizeArrayProperty<ConversationHistoryItem>(value);
}

class ConversationsHistory extends LitElement {
  static properties = {
    conversations: { type: Array },
    activeConversationId: { type: String, attribute: 'active-conversation-id' },
  };

  declare conversations: ConversationHistoryItem[];
  declare activeConversationId: string | null;

  private resizeObserver: ResizeObserver | null = null;
  private animationFrame: number | null = null;
  private visualActiveConversationId: string | null = null;
  private pendingVisualActiveConversationId: string | null = null;
  private animatingToNodeId: string | null = null;
  private animationSequence = 0;
  private drawFrame: number | null = null;
  private readonly handleTimelineScroll = () => this.scheduleDrawGraph();

  constructor() {
    super();
    this.conversations = [];
    this.activeConversationId = null;
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

  protected willUpdate(changedProperties: Map<string, unknown>): void {
    if (changedProperties.has('activeConversationId')) {
      this.prepareVisualActiveTransition(changedProperties.get('activeConversationId') as string | null | undefined);
    }
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
          <h1 class="conversations-history-title">${this.formatConversationCount(this.conversations.length)}</h1>
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
        conversations-history {
          --trail-line-color: color-mix(in srgb, var(--vaadin-text-color-secondary) 42%, transparent);
          --trail-line-active-color: var(--vaadin-text-color);
          --trail-line-width: 3px;
          --trail-node-active-color: var(--vaadin-text-color);
          --history-optical-inline-start: var(--sidebar-optical-inline-start, 0.55rem);
        }

        .conversation-history__header {
          min-width: 0;
          padding: 0.32rem var(--sidebar-section-inline-end) 0.38rem var(--history-optical-inline-start);
        }

        .conversations-history-title-row {
          min-width: 0;
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          gap: var(--vaadin-gap-s);
        }

        .conversations-history-title {
          margin: 0;
          flex: 1 1 auto;
          min-width: 0;
          color: var(--vaadin-text-color-secondary);
          font-family: var(--aura-font-family);
          font-size: var(--aura-font-size-m);
          line-height: 1.1;
          font-weight: var(--aura-font-weight-semibold);
          letter-spacing: 0;
        }

        .conversations-history-section {
          min-height: 0;
          flex: 1 1 auto;
          display: flex;
          flex-direction: column;
          overflow: hidden;
        }

        .conversations-history-body {
          min-height: 0;
          flex: 1 1 auto;
          display: flex;
          flex-direction: column;
          overflow: hidden;
        }

        .conversations-history-empty {
          margin: 0.32rem var(--sidebar-section-inline-end) 0.58rem var(--history-optical-inline-start);
          display: flex;
          flex-direction: column;
          gap: var(--vaadin-gap-xs);
        }

        .conversations-history-empty-title {
          color: var(--vaadin-text-color);
          font-family: var(--aura-font-family);
          font-size: var(--aura-font-size-m);
          font-weight: var(--aura-font-weight-semibold);
          letter-spacing: 0;
        }

        .conversations-history-empty-description {
          margin: 0;
          color: var(--vaadin-text-color-secondary);
          font-family: var(--aura-font-family);
          font-size: var(--aura-font-size-m);
          line-height: 1.34;
        }

        .conversations-history-timeline {
          position: relative;
          min-height: 0;
          flex: 1 1 auto;
          overflow: auto;
          scrollbar-width: none;
          -ms-overflow-style: none;
          padding: 0.2rem 0.45rem 0.7rem var(--history-optical-inline-start);
        }

        .conversations-history-timeline-edges,
        .conversations-history-timeline-effects {
          position: absolute;
          inset: 0;
          pointer-events: none;
          overflow: visible;
        }

        .conversations-history-timeline-edges {
          z-index: 0;
        }

        .conversations-history-timeline-effects {
          z-index: 2;
        }

        .conversations-history-timeline-rows {
          position: relative;
          z-index: 1;
          display: flex;
          flex-direction: column;
          gap: var(--vaadin-gap-xs);
        }

        .conversations-history-entry-row {
          width: 100%;
          min-width: 0;
          display: grid;
          grid-template-columns: 1.35rem minmax(0, 1fr);
          align-items: center;
        }

        .conversations-history-divider-row {
          min-height: 2.05rem;
          padding: 0.05rem 0;
        }

        .conversations-history-thread-row {
          min-height: 2.45rem;
          padding: 0.08rem 0.55rem 0.08rem 0;
          grid-template-columns: 2.25rem minmax(0, 1fr);
          border-radius: var(--vaadin-radius-m);
        }

        vaadin-button.conversations-history-item-button {
          display: block;
          --vaadin-button-padding: 0;
        }

        vaadin-button.conversations-history-item-button::before,
        vaadin-button.conversations-history-item-button::after {
          display: none;
        }

        vaadin-button.conversations-history-item-button::part(button) {
          width: 100%;
          padding: 0;
          border: 0;
          background: transparent !important;
          box-shadow: none;
        }

        vaadin-button.conversations-history-item-button::part(label) {
          width: 100%;
          display: block;
        }

        vaadin-button.conversations-history-item-button[disabled]::part(button) {
          opacity: 1;
        }

        .conversations-history-edge,
        .conversations-history-active-marker,
        .conversations-history-travel-path,
        .conversations-history-travel-tail {
          fill: none;
          stroke-linecap: round;
          stroke-linejoin: round;
          vector-effect: non-scaling-stroke;
          shape-rendering: geometricPrecision;
        }

        .conversations-history-edge {
          stroke: var(--trail-line-color);
          stroke-width: var(--trail-line-width);
          opacity: 0.62;
          transition: opacity var(--motion-fast);
        }

        .conversations-history-active-marker {
          stroke: var(--trail-line-active-color);
          stroke-width: calc(var(--trail-line-width) + 1px);
          opacity: 0.96;
        }

        .conversations-history-travel-path {
          stroke: var(--trail-line-active-color);
          stroke-width: calc(var(--trail-line-width) + 1px);
          opacity: 0.96;
        }

        .conversations-history-travel-tail {
          stroke: var(--trail-line-active-color);
          stroke-width: var(--trail-line-width);
          opacity: 0.22;
        }

        .conversations-history-node {
          justify-self: center;
          width: 0;
          height: 0;
          border-radius: var(--vaadin-radius-l);
          background: transparent;
          box-shadow: none;
        }

        .conversations-history-divider-row .conversations-history-node {
          margin-left: var(--vaadin-gap-xs);
        }

        .conversations-history-thread-row .conversations-history-node {
          margin-left: 0;
        }

        .conversations-history-divider-label {
          min-width: 0;
          color: var(--vaadin-text-color-secondary);
          font-family: var(--aura-font-family);
          font-size: var(--aura-font-size-m);
          letter-spacing: 0.02em;
        }

        .conversations-history-item-title,
        width-aware-label.conversations-history-item-title {
          min-width: 0;
          display: block;
          color: var(--vaadin-text-color-secondary);
          font-family: var(--aura-font-family);
          font-size: var(--aura-font-size-m);
          font-weight: var(--aura-font-weight-medium);
          line-height: 1.3;
          text-align: left;
          transition: color var(--motion-fast);
        }

        vaadin-button.conversations-history-item-button:hover .conversations-history-item-title,
        vaadin-button.conversations-history-item-button:focus-within .conversations-history-item-title,
        .conversations-history-thread-row.is-active .conversations-history-item-title {
          color: var(--vaadin-text-color);
        }

        .conversations-history-thread-row.is-active .conversations-history-item-title {
          text-shadow: 0 0 6px color-mix(in srgb, var(--trail-node-active-color) 6%, transparent);
        }

        @media (max-width: 640px) {
          .conversations-history-empty {
            margin: 0.4rem var(--sidebar-section-inline-end) 0.7rem var(--history-optical-inline-start);
          }

          .conversations-history-timeline {
            padding: 0.18rem 0.4rem 0.72rem var(--history-optical-inline-start);
          }

          .conversations-history-entry-row {
            grid-template-columns: 2rem minmax(0, 1fr);
          }

          .conversations-history-thread-row {
            grid-template-columns: 2.55rem minmax(0, 1fr);
          }
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
    const active = entry.conversation.id === this.currentVisualActiveConversationId;
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
        @click=${() => this.openConversation(entry.conversation.id)}
      >
        <div class=${classMap(rowClasses)}>
          ${this.renderNode(entry, active)}
          <width-aware-label
            class="conversations-history-item-title"
            .fullText=${entry.conversation.title}
          ></width-aware-label>
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
    return `Hilos (${count})`;
  }

  private get currentVisualActiveConversationId(): string | null {
    if (this.pendingVisualActiveConversationId) {
      return null;
    }
    return this.visualActiveConversationId ?? this.activeConversationId;
  }

  private prepareVisualActiveTransition(previousActiveConversationId: string | null | undefined): void {
    const nextActiveConversationId = this.activeConversationId;
    const currentVisualConversationId = this.visualActiveConversationId ?? previousActiveConversationId ?? null;

    if (!currentVisualConversationId || !nextActiveConversationId || currentVisualConversationId === nextActiveConversationId) {
      this.visualActiveConversationId = nextActiveConversationId;
      this.pendingVisualActiveConversationId = null;
      this.animatingToNodeId = null;
      this.animationSequence += 1;
      return;
    }

    this.visualActiveConversationId = currentVisualConversationId;
    this.pendingVisualActiveConversationId = nextActiveConversationId;
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
      this.animationSequence += 1;
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

    const strokeWidth = this.resolveTrailStrokeWidth(timeline);
    const geometry = this.buildGeometry(nodes, rootRect, timeline.scrollTop, strokeWidth);

    const path = document.createElementNS(SVG_NS, 'path');
    path.classList.add('conversations-history-edge');
    path.setAttribute('d', geometry.pathData);
    staticSvg.append(path);

    const activeMarker = this.buildActiveMarker(geometry.points, this.currentVisualActiveConversationId, strokeWidth);
    if (activeMarker) {
      staticSvg.append(activeMarker);
    }

    const visualActiveConversationId = this.visualActiveConversationId ?? this.activeConversationId;
    const pendingActiveConversationId = this.pendingVisualActiveConversationId;
    if (visualActiveConversationId && pendingActiveConversationId) {
      const fromNodeId = `thread-${visualActiveConversationId}`;
      const toNodeId = `thread-${pendingActiveConversationId}`;
      if (this.animatingToNodeId !== toNodeId) {
        this.animatingToNodeId = toNodeId;
        this.animateTraveler(
          geometry.points,
          fromNodeId,
          toNodeId,
          strokeWidth,
          () => this.finishActiveTransition(pendingActiveConversationId, toNodeId),
        );
      }
    }
  }

  private buildGeometry(
    nodes: HTMLElement[],
    rootRect: DOMRect,
    scrollTop: number,
    strokeWidth: number,
  ): TimelineGeometry {
    const points = nodes.map((node) => this.buildPoint(node, rootRect, scrollTop, strokeWidth));
    return {
      points,
      pathData: this.buildPathData(points, strokeWidth),
    };
  }

  private buildPoint(node: HTMLElement, rootRect: DOMRect, scrollTop: number, strokeWidth: number): TimelinePoint {
    const rect = node.getBoundingClientRect();
    return {
      node,
      id: node.dataset.nodeId ?? '',
      x: this.alignToStrokePixel(rect.left - rootRect.left + rect.width / 2, strokeWidth),
      y: this.alignToStrokePixel(rect.top - rootRect.top + rect.height / 2 + scrollTop, strokeWidth),
    };
  }

  private buildPathData(points: TimelinePoint[], strokeWidth: number): string {
    const pathParts = [`M ${points[0].x} ${points[0].y}`];
    for (let index = 0; index < points.length - 1; index += 1) {
      this.appendSegment(pathParts, points[index], points[index + 1], strokeWidth);
    }
    return pathParts.join(' ');
  }

  private resolveTrailStrokeWidth(timeline: HTMLElement): number {
    const value = getComputedStyle(timeline).getPropertyValue('--trail-line-width').trim();
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 3;
  }

  private alignToStrokePixel(value: number, strokeWidth: number): number {
    const rounded = Math.round(value);
    return Math.round(strokeWidth) % 2 === 0 ? rounded : rounded + 0.5;
  }

  private appendSegment(
    pathParts: string[],
    fromPoint: TimelinePoint,
    toPoint: TimelinePoint,
    strokeWidth: number,
  ): void {
    if (Math.abs(fromPoint.x - toPoint.x) < 1) {
      pathParts.push(`L ${toPoint.x} ${toPoint.y}`);
      return;
    }

    const direction = Math.sign(toPoint.y - fromPoint.y) || 1;
    const cornerY = this.alignToStrokePixel((fromPoint.y + toPoint.y) / 2, strokeWidth);
    const transitionZone = Math.min(
      TIMELINE_CURVE_TRANSITION_ZONE,
      Math.abs(toPoint.y - fromPoint.y) / 2,
    );
    const entryY = this.alignToStrokePixel(cornerY - transitionZone * direction, strokeWidth);
    const exitY = this.alignToStrokePixel(cornerY + transitionZone * direction, strokeWidth);

    pathParts.push(`L ${fromPoint.x} ${entryY}`);
    pathParts.push(`C ${fromPoint.x} ${cornerY}, ${toPoint.x} ${cornerY}, ${toPoint.x} ${exitY}`);
    pathParts.push(`L ${toPoint.x} ${toPoint.y}`);
  }

  private buildActiveMarker(
    points: TimelinePoint[],
    activeConversationId: string | null,
    strokeWidth: number,
  ): SVGPathElement | null {
    if (!activeConversationId) {
      return null;
    }

    const activePoint = points.find((point) => point.id === `thread-${activeConversationId}`);
    if (!activePoint) {
      return null;
    }

    const markerHalfHeight = 9.75;
    const marker = document.createElementNS(SVG_NS, 'path');
    marker.classList.add('conversations-history-active-marker');
    marker.setAttribute(
      'd',
      `M ${activePoint.x} ${this.alignToStrokePixel(activePoint.y - markerHalfHeight, strokeWidth)} `
        + `L ${activePoint.x} ${this.alignToStrokePixel(activePoint.y + markerHalfHeight, strokeWidth)}`,
    );
    return marker;
  }

  private animateTraveler(
    points: TimelinePoint[],
    fromId: string,
    toId: string,
    strokeWidth: number,
    onComplete: () => void,
  ): void {
    const fxSvg = this.fxSvg;
    if (!fxSvg) {
      return;
    }

    const startIndex = points.findIndex((point) => point.id === fromId);
    const endIndex = points.findIndex((point) => point.id === toId);
    if (startIndex < 0 || endIndex < 0 || startIndex === endIndex) {
      this.animatingToNodeId = null;
      onComplete();
      return;
    }

    const routePoints = startIndex < endIndex
      ? points.slice(startIndex, endIndex + 1)
      : points.slice(endIndex, startIndex + 1).reverse();

    const routePathData = this.buildPathData(routePoints, strokeWidth);
    const routeTail = document.createElementNS(SVG_NS, 'path');
    routeTail.classList.add('conversations-history-travel-tail');
    routeTail.setAttribute('d', routePathData);

    const routePath = document.createElementNS(SVG_NS, 'path');
    routePath.classList.add('conversations-history-travel-path');
    routePath.setAttribute('d', routePathData);
    fxSvg.replaceChildren(routeTail, routePath);

    const totalLength = routePath.getTotalLength();
    const dropLength = Math.min(56, Math.max(28, totalLength * 0.34));
    const tailLength = Math.min(96, Math.max(dropLength * 1.35, totalLength * 0.42));
    const duration = Math.min(860, Math.max(520, totalLength * 8));
    const startedAt = performance.now();
    const sequence = ++this.animationSequence;

    routePath.style.strokeDasharray = `${dropLength} ${totalLength}`;
    routePath.style.strokeDashoffset = `${dropLength}`;
    routeTail.style.strokeDasharray = `${tailLength} ${totalLength}`;
    routeTail.style.strokeDashoffset = `${tailLength}`;

    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
    }

    const draw = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = progress < 0.5
        ? 4 * progress * progress * progress
        : 1 - Math.pow(-2 * progress + 2, 3) / 2;
      routePath.style.strokeDashoffset = `${dropLength - eased * (totalLength + dropLength)}`;
      routeTail.style.strokeDashoffset = `${tailLength - eased * (totalLength + tailLength)}`;

      if (progress < 1) {
        this.animationFrame = requestAnimationFrame(draw);
      } else if (sequence === this.animationSequence) {
        fxSvg.replaceChildren();
        this.animationFrame = null;
        onComplete();
      }
    };

    this.animationFrame = requestAnimationFrame(draw);
  }

  private finishActiveTransition(conversationId: string, nodeId: string): void {
    if (this.activeConversationId === conversationId && this.animatingToNodeId === nodeId) {
      this.visualActiveConversationId = conversationId;
      this.pendingVisualActiveConversationId = null;
    }
    this.animatingToNodeId = null;
    this.requestUpdate();
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
