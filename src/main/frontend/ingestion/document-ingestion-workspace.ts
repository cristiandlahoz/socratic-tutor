import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/progress-bar';
import '@vaadin/text-area';
import './dropzone';
import { css, html, LitElement, nothing } from 'lit';
import { repeat } from 'lit/directives/repeat.js';

type CourseMaterialCatalog = {
  label: string;
  useWhen: string;
  aliases: string[];
};

type EditableSegment = {
  id: string;
  ordinal: number;
  headingPath: string;
  content: string;
  editable: boolean;
  deleted: boolean;
  charCount: number | null;
  tokenCount: number | null;
  pageNumber: number | null;
  pageNumbers: number[];
  captions: string[];
  docItems: string[];
  rawText: string;
  source: string;
};

type DocumentCard = {
  ingestionId: string;
  title: string;
  status: string;
  segmentCount: number;
  catalogLabel: string;
  catalogUseWhen: string;
};

type DocumentDetail = {
  ingestionId: string;
  title: string;
  status: string;
  catalog: CourseMaterialCatalog | null;
  markdown: string;
  segments: EditableSegment[];
  vectorIds: string[];
};

type ServerBridge = {
  loadDocuments(): Promise<string>;
  loadDocument(ingestionId: string): Promise<string>;
  generateCatalog(title: string, useWhen: string, segmentsJson: string): Promise<string>;
  indexDraft(
    ingestionId: string,
    title: string,
    catalogJson: string,
    markdown: string,
    segmentsJson: string
  ): Promise<string>;
  reindexDocument(detailJson: string): Promise<string>;
  deleteDocument(ingestionId: string): Promise<string>;
};

class DocumentIngestionWorkspaceElement extends LitElement {
  static readonly styles = css`
    :host {
      display: block;
      min-height: 100%;
      height: 100%;
      box-sizing: border-box;
      padding: var(--vaadin-padding-l);
      color: var(--vaadin-text-color);
      font-family: var(--aura-font-family);
      background:
        var(--theme-glow),
        linear-gradient(
          180deg,
          color-mix(in srgb, var(--aura-app-background) 94%, transparent),
          color-mix(in srgb, var(--vaadin-background-color) 96%, transparent)
        );
    }

    * {
      box-sizing: border-box;
    }

    .workspace {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-l);
      min-height: calc(100vh - (var(--vaadin-padding-l) * 2));
      max-width: none;
      margin: 0;
    }

    .header {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-m);
      align-items: center;
      padding: var(--vaadin-padding-s) 0 var(--vaadin-padding-xs);
    }

    .header-copy {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--vaadin-gap-s);
      max-width: 100%;
      text-align: center;
    }

    .title-row {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--vaadin-gap-xs);
    }

    .info-button {
      position: relative;
      flex: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: calc(var(--aura-font-size-l) * 1.4);
      height: calc(var(--aura-font-size-l) * 1.4);
      border: 0;
      border-radius: var(--vaadin-radius-s);
      background: var(--vaadin-background-container);
      color: var(--vaadin-text-color-secondary);
      cursor: help;
      padding: 0;
    }

    .info-button vaadin-icon {
      width: var(--aura-font-size-s);
      height: var(--aura-font-size-s);
    }

    .tooltip {
      position: absolute;
      top: calc(100% + var(--vaadin-gap-s));
      left: 50%;
      z-index: 2;
      width: min(28rem, calc(100vw - (var(--vaadin-padding-l) * 2)));
      padding: var(--vaadin-padding-s);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
      background: var(--aura-surface-color-solid);
      color: var(--vaadin-text-color);
      font-size: var(--aura-font-size-s);
      line-height: 1.45;
      opacity: 0;
      pointer-events: none;
      text-align: start;
      transform: translate(-50%, calc(var(--vaadin-gap-xs) * -1));
      transition:
        opacity var(--motion-fast),
        transform var(--motion-fast);
    }

    .info-button:hover .tooltip,
    .info-button:focus-visible .tooltip {
      opacity: 1;
      transform: translate(-50%, 0);
    }

    h1,
    h2,
    h3,
    p {
      margin: 0;
    }

    h1 {
      font-size: var(--aura-font-size-xl);
      line-height: var(--aura-line-height-xl);
      font-weight: var(--aura-font-weight-semibold);
      letter-spacing: 0;
      text-wrap: balance;
    }

    h2 {
      font-size: var(--aura-font-size-l);
      line-height: var(--aura-line-height-l);
      font-weight: var(--aura-font-weight-semibold);
    }

    h3 {
      font-size: var(--aura-font-size-m);
      line-height: var(--aura-line-height-m);
      font-weight: var(--aura-font-weight-semibold);
    }

    .description,
    .muted,
    .meta,
    .empty p {
      color: var(--vaadin-text-color-secondary);
      font-size: var(--aura-font-size-s);
      line-height: 1.55;
    }

    .upload-panel {
      width: min(100%, 38rem);
      margin-inline: auto;
    }

    ::slotted(document-upload-dropzone) {
      width: 100%;
    }

    .status-note {
      display: flex;
      align-items: center;
      gap: var(--vaadin-gap-s);
      padding: var(--vaadin-padding-s) var(--vaadin-padding-m);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
      background: var(--vaadin-background-container);
      color: var(--vaadin-text-color);
      font-size: var(--aura-font-size-s);
    }

    .status-note[hidden] {
      display: none;
    }

    .content-grid {
      flex: 1;
      display: grid;
      grid-template-columns: 1fr;
      gap: var(--vaadin-gap-l);
      align-items: start;
      justify-items: start;
      width: 100%;
    }

    .content-grid[detail] {
      grid-template-columns: minmax(18rem, 22rem) minmax(0, 1fr);
      align-items: start;
      justify-items: stretch;
    }

    .document-list,
    .detail {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-m);
      width: 100%;
    }

    .list-heading,
    .detail-heading {
      display: flex;
      justify-content: space-between;
      gap: var(--vaadin-gap-m);
      align-items: flex-start;
    }

    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 28rem), 1fr));
      grid-auto-rows: 16rem;
      gap: var(--vaadin-gap-m);
      width: 100%;
    }

    .content-grid[detail] .list-heading {
      display: none;
    }

    .content-grid[detail] .cards {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-s);
    }

    .card {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-m);
      width: 100%;
      min-width: 0;
      height: 100%;
      min-height: 0;
      overflow: hidden;
      padding: var(--vaadin-padding-m);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-l);
      background: var(--aura-surface-color);
      cursor: pointer;
      transition:
        background var(--motion-fast),
        border-color var(--motion-fast),
        transform var(--motion-fast);
    }

    .content-grid[detail] .card {
      width: 100%;
      min-width: 0;
      max-width: none;
      height: 12rem;
      gap: var(--vaadin-gap-s);
    }

    .card .muted,
    .card-title {
      display: -webkit-box;
      overflow: hidden;
      -webkit-box-orient: vertical;
    }

    .card-title {
      -webkit-line-clamp: 2;
    }

    .card .muted {
      -webkit-line-clamp: 4;
    }

    .content-grid[detail] .card .muted {
      -webkit-line-clamp: 3;
    }

    .card:hover {
      background: var(--vaadin-background-container);
      transform: translateY(-1px);
    }

    .card:focus-visible {
      outline: var(--vaadin-focus-ring-width, 2px) solid var(--aura-accent-border-color);
      outline-offset: var(--vaadin-gap-xs);
    }

    .card[selected] {
      border-color: var(--aura-accent-border-color);
      background: var(--aura-accent-surface);
    }

    .card-top,
    .segment-top,
    .actions {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: var(--vaadin-gap-s);
    }

    .card-title {
      overflow-wrap: anywhere;
    }

    .chip {
      flex: none;
      display: inline-flex;
      align-items: center;
      width: fit-content;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      padding: var(--vaadin-padding-xs) var(--vaadin-padding-s);
      border-radius: var(--vaadin-radius-s);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      background: var(--vaadin-background-container);
      color: var(--vaadin-text-color);
      font-size: var(--aura-font-size-xs);
      font-weight: var(--aura-font-weight-medium);
    }

    .chip[indexed] {
      border-color: color-mix(in srgb, var(--aura-green) 45%, var(--vaadin-border-color-secondary));
      color: var(--aura-green-text);
    }

    .chip[dirty] {
      border-color: color-mix(in srgb, var(--aura-orange) 45%, var(--vaadin-border-color-secondary));
      color: var(--aura-orange-text);
    }

    .chip[error] {
      border-color: color-mix(in srgb, var(--aura-red) 45%, var(--vaadin-border-color-secondary));
      color: var(--aura-red-text);
    }

    .card-footer {
      display: flex;
      flex-wrap: wrap;
      gap: var(--vaadin-gap-xs);
      margin-top: auto;
    }

    .detail-panel,
    .empty,
    .section {
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-l);
      background: var(--aura-surface-color);
      padding: var(--vaadin-padding-m);
    }

    .detail-panel {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-m);
      min-height: 0;
      animation: detail-enter var(--motion-fast) ease-out;
    }

    .detail-title {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-xs);
      min-width: 0;
    }

    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: var(--vaadin-gap-xs);
      border-bottom: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      padding-bottom: var(--vaadin-padding-xs);
    }

    .tab {
      border: 0;
      border-radius: var(--vaadin-radius-s);
      background: transparent;
      color: var(--vaadin-text-color-secondary);
      cursor: pointer;
      font: inherit;
      font-size: var(--aura-font-size-s);
      padding: var(--vaadin-padding-xs) var(--vaadin-padding-s);
    }

    .tab[aria-selected='true'] {
      background: var(--vaadin-background-container);
      color: var(--vaadin-text-color);
    }

    .section {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-m);
    }

    .segment-list {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-s);
      padding-bottom: calc(var(--vaadin-padding-xl) + var(--vaadin-padding-l));
    }

    .segment {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-s);
      padding: var(--vaadin-padding-s);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
      background: var(--vaadin-background-container);
    }

    .segment vaadin-text-area::part(input-field),
    .catalog-form vaadin-text-area::part(input-field),
    .markdown-editor::part(input-field) {
      background: var(--vaadin-background-container-strong);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
    }

    .segment vaadin-text-area,
    .catalog-form vaadin-text-area,
    .markdown-editor {
      width: 100%;
    }

    .catalog-form {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-s);
    }

    .required-label {
      display: inline-flex;
      align-items: center;
      gap: var(--vaadin-gap-xs);
    }

    .generate-catalog-button[disabled] {
      opacity: 1;
      color: color-mix(in srgb, var(--vaadin-text-color) 62%, var(--vaadin-background-color));
    }

    .generate-catalog-button[disabled]::part(button) {
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      background: var(--vaadin-background-container);
    }

    .generate-catalog-button[disabled]::part(label),
    .generate-catalog-button[disabled]::part(prefix) {
      color: color-mix(in srgb, var(--vaadin-text-color) 62%, var(--vaadin-background-color)) !important;
    }

    .generate-catalog-button[disabled] vaadin-icon {
      color: inherit !important;
    }

    .catalog-preview {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-xs);
      padding: var(--vaadin-padding-s);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
      background: var(--vaadin-background-container);
    }

    .action-bar {
      position: sticky;
      bottom: var(--vaadin-gap-m);
      z-index: 1;
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--vaadin-gap-s);
      padding: var(--vaadin-padding-s);
      border: var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color-secondary);
      border-radius: var(--vaadin-radius-m);
      background: var(--aura-surface-color-solid);
    }

    .empty {
      display: flex;
      flex-direction: column;
      gap: var(--vaadin-gap-xs);
    }

    vaadin-button::part(button) {
      border-radius: var(--vaadin-radius-m);
    }

    .trash-icon {
      display: block;
      width: 18px;
      height: 18px;
      background: var(--vaadin-text-color);
      mask: url('/icons/trash.svg') center / contain no-repeat;
    }

    @media (max-width: 62rem) {
      :host {
        padding: var(--vaadin-padding-m);
      }

      .content-grid,
      .content-grid[detail] {
        grid-template-columns: 1fr;
      }

      .header {
        align-items: stretch;
      }

      .upload-panel {
        flex-basis: auto;
        width: 100%;
      }

      .header-copy {
        justify-content: flex-start;
        padding-inline-start: var(--vaadin-padding-l);
        text-align: start;
      }

      .title-row {
        justify-content: flex-start;
      }

      .workspace {
        min-height: calc(100vh - (var(--vaadin-padding-m) * 2));
      }
    }

    @keyframes detail-enter {
      from {
        opacity: 0;
        transform: translateY(var(--vaadin-gap-xs));
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .detail-panel {
        animation: none;
      }

      .card {
        transition: none;
      }
    }
  `;

  static readonly properties = {
    documents: { state: true },
    drafts: { state: true },
    selectedDocument: { state: true },
    selectedId: { state: true },
    dirty: { state: true },
    busy: { state: true },
    statusMessage: { state: true },
    errorMessage: { state: true },
    activeTab: { state: true },
    catalogUseWhen: { state: true },
  };

  declare documents: DocumentCard[];
  declare drafts: DocumentDetail[];
  declare selectedDocument: DocumentDetail | null;
  declare selectedId: string;
  declare dirty: boolean;
  declare busy: boolean;
  declare statusMessage: string;
  declare errorMessage: string;
  declare activeTab: 'segments' | 'catalog' | 'markdown';
  declare catalogUseWhen: string;

  private readonly detailCache = new Map<string, DocumentDetail>();

  constructor() {
    super();
    this.documents = [];
    this.drafts = [];
    this.selectedDocument = null;
    this.selectedId = '';
    this.dirty = false;
    this.busy = false;
    this.statusMessage = '';
    this.errorMessage = '';
    this.activeTab = 'segments';
    this.catalogUseWhen = '';
  }

  connectedCallback(): void {
    super.connectedCallback();
    void this.loadDocuments();
  }

  receiveDraft(detailJson: string): void {
    const detail = parseJson<DocumentDetail>(detailJson);
    this.errorMessage = '';
    this.drafts = [detail, ...this.drafts.filter((draft) => draft.ingestionId !== detail.ingestionId)];
    this.selectDetail(detail, false);
  }

  protected render() {
    return html`
      <div class="workspace">
        ${this.renderHeader()}
        <div class="status-note" ?hidden=${!this.busy && !this.statusMessage && !this.errorMessage}>
          ${this.busy ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : nothing}
          <span>${this.errorMessage || this.statusMessage}</span>
        </div>
        <div class="content-grid" ?detail=${Boolean(this.selectedDocument)}>
          ${this.renderDocumentList()}
          ${this.selectedDocument ? this.renderDetail(this.selectedDocument) : nothing}
        </div>
      </div>
    `;
  }

  private renderTrashIcon() {
    return html`<span class="trash-icon" aria-hidden="true" slot="prefix"></span>`;
  }

  private renderHeader() {
    return html`
      <header class="header">
        <div class="header-copy">
          <div class="title-row">
            <h1>Materiales de clase</h1>
            <button class="info-button" type="button" aria-label="Ver descripción de materiales de clase">
              <vaadin-icon icon="vaadin:info-circle-o" aria-hidden="true"></vaadin-icon>
              <span class="tooltip" role="tooltip">
                Gestiona los PDFs que el tutor puede consultar. Revisa segmentos, corrige contenido y reindexa cuando
                cambie la evidencia.
              </span>
            </button>
          </div>
        </div>
        <div class="upload-panel">
          <slot name="upload"></slot>
        </div>
      </header>
    `;
  }

  private renderDocumentList() {
    const cards = this.cards();
    return html`
      <section class="document-list" aria-label="Documentos indexados">
        <div class="list-heading">
          <div>
            <h2>Documentos (${cards.length})</h2>
          </div>
        </div>
        ${cards.length === 0
        ? html`
              <div class="empty">
                <h3>Sin documentos indexados</h3>
                <p>Sube un PDF para crear el primer material consultable por el tutor.</p>
              </div>
            `
        : html`<div class="cards">${repeat(cards, (card) => card.ingestionId, (card) => this.renderCard(card))}</div>`}
      </section>
    `;
  }

  private renderCard(card: DocumentCard) {
    const selected = this.selectedId === card.ingestionId;
    return html`
      <article
        class="card"
        role="button"
        tabindex="0"
        ?selected=${selected}
        @click=${() => this.selectCard(card)}
        @keydown=${(event: KeyboardEvent) => this.activateCard(event, card)}
      >
        <div class="card-top">
          <span class="chip" ?indexed=${card.status === 'INDEXED'} ?dirty=${card.status === 'DIRTY'}>
            ${this.statusLabel(card.status)}
          </span>
          <vaadin-button
            theme="tertiary error"
            aria-label=${`Eliminar ${card.title}`}
            @click=${(event: Event) => this.deleteFromCard(event, card)}
          >
            ${this.renderTrashIcon()}
          </vaadin-button>
        </div>
        <h3 class="card-title">${card.title}</h3>
        <p class="muted">${card.catalogUseWhen || 'Sin criterio de uso registrado.'}</p>
        <div class="card-footer">
          <span class="chip">${card.segmentCount} segmentos</span>
          ${card.catalogLabel ? html`<span class="chip">${card.catalogLabel}</span>` : nothing}
        </div>
      </article>
    `;
  }

  private renderDetail(detail: DocumentDetail) {
    return html`
      <section class="detail-panel" aria-label="Detalle del documento">
        <div class="detail-heading">
          <div class="detail-title">
            <span class="chip" ?indexed=${detail.status === 'INDEXED' && !this.dirty} ?dirty=${this.dirty}>
              ${this.dirty ? 'Cambios sin indexar' : this.statusLabel(detail.status)}
            </span>
            <h2>${detail.title}</h2>
            <p class="meta">${detail.segments.length} segmentos · ${detail.catalog?.label || 'catálogo pendiente'}</p>
          </div>
        </div>
        <nav class="tabs" aria-label="Secciones del documento">
          ${this.renderTab('segments', 'Segmentos')}
          ${this.renderTab('catalog', 'Catálogo')}
          ${this.renderTab('markdown', 'Markdown')}
        </nav>
        ${this.activeTab === 'segments' ? this.renderSegments(detail) : nothing}
        ${this.activeTab === 'catalog' ? this.renderCatalog(detail) : nothing}
        ${this.activeTab === 'markdown' ? this.renderMarkdown(detail) : nothing}
        ${this.renderActions(detail)}
      </section>
    `;
  }

  private renderTab(tab: 'segments' | 'catalog' | 'markdown', label: string) {
    return html`
      <button class="tab" type="button" aria-selected=${String(this.activeTab === tab)} @click=${() => {
        this.activeTab = tab;
      }}>
        ${label}
      </button>
    `;
  }

  private renderSegments(detail: DocumentDetail) {
    return html`
      <div class="section">
        <div>
          <h3>Segmentos</h3>
          <p class="muted">El tutor recupera estos fragmentos como evidencia. Edita o elimina antes de reindexar.</p>
        </div>
        <div class="segment-list">
          ${repeat(detail.segments, (segment) => segment.id, (segment) => this.renderSegment(segment))}
        </div>
      </div>
    `;
  }

  private renderSegment(segment: EditableSegment) {
    return html`
      <article class="segment">
        <div class="segment-top">
          <div>
            <span class="chip">segmento ${segment.ordinal}</span>
            <p class="meta">${segment.content.length} caracteres${this.pageSummary(segment)}</p>
          </div>
          <vaadin-button theme="tertiary error" @click=${() => this.deleteSegment(segment.id)}>
            ${this.renderTrashIcon()}
            Eliminar
          </vaadin-button>
        </div>
        <vaadin-text-area
          .value=${segment.content}
          maxlength="8000"
          @value-changed=${(event: CustomEvent<{ value?: string }>) => this.updateSegment(segment.id, event)}
        ></vaadin-text-area>
      </article>
    `;
  }

  private renderCatalog(detail: DocumentDetail) {
    return html`
      <div class="section">
        <div>
          <h3>Catálogo de búsqueda</h3>
          <p class="muted">El tutor usa esta descripción para decidir cuándo consultar el material.</p>
        </div>
        ${detail.status === 'REVIEW_READY'
        ? html`
              <div class="catalog-form">
                <vaadin-text-area
                  helper-text="Máximo 200 caracteres."
                  maxlength="200"
                  required
                  required-indicator-visible
                  .value=${this.catalogUseWhen}
                  @value-changed=${(event: CustomEvent<{ value?: string }>) => {
            this.catalogUseWhen = event.detail.value ?? '';
          }}
                >
                  <span slot="label" class="required-label">
                    Cuándo debe usarlo el tutor
                  </span>
                </vaadin-text-area>
                <vaadin-button
                  class="generate-catalog-button"
                  theme="primary"
                  ?disabled=${this.busy || this.catalogUseWhen.trim().length === 0}
                  @click=${() => this.generateCatalog()}
                >
                  <vaadin-icon icon="vaadin:magic" slot="prefix"></vaadin-icon>
                  Generar catálogo
                </vaadin-button>
              </div>
            `
        : nothing}
        ${detail.catalog
        ? html`
              <div class="catalog-preview">
                <h3>${detail.catalog.label}</h3>
                <p class="muted">${detail.catalog.useWhen}</p>
                ${detail.catalog.aliases.length > 0
            ? html`<p class="meta">Alias: ${detail.catalog.aliases.join(', ')}</p>`
            : nothing}
              </div>
            `
        : html`<p class="muted">Genera un catálogo antes de indexar este documento.</p>`}
      </div>
    `;
  }

  private renderMarkdown(detail: DocumentDetail) {
    return html`
      <div class="section">
        <div>
          <h3>Markdown fuente</h3>
          <p class="muted">Artefacto revisado del PDF. En documentos indexados se reconstruye desde segmentos.</p>
        </div>
        <vaadin-text-area
          class="markdown-editor"
          .value=${detail.markdown}
          maxlength="200000"
          @value-changed=${(event: CustomEvent<{ value?: string }>) => this.updateMarkdown(event)}
        ></vaadin-text-area>
      </div>
    `;
  }

  private renderActions(detail: DocumentDetail) {
    const isDraft = detail.status === 'REVIEW_READY';
    const canIndex = detail.segments.length > 0 && Boolean(detail.catalog) && !this.busy;
    return html`
      <div class="action-bar">
        <vaadin-button theme="tertiary error" ?disabled=${this.busy} @click=${() => this.deleteSelected()}>
          ${this.renderTrashIcon()}
          Eliminar documento
        </vaadin-button>
        <vaadin-button theme="primary" ?disabled=${!canIndex || (!isDraft && !this.dirty)} @click=${() => this.commit()}>
          <vaadin-icon icon="vaadin:database" slot="prefix"></vaadin-icon>
          ${isDraft ? 'Indexar' : 'Reindexar'}
        </vaadin-button>
      </div>
    `;
  }

  private async loadDocuments(): Promise<void> {
    this.busy = true;
    this.errorMessage = '';
    try {
      this.documents = parseJson<DocumentCard[]>(await this.server().loadDocuments());
      const selectedFromUrl = new URLSearchParams(window.location.search).get('d');
      if (selectedFromUrl) {
        const card = this.cards().find((item) => item.ingestionId === selectedFromUrl);
        if (card) {
          await this.selectCard(card);
        }
      }
    } catch (error) {
      this.errorMessage = message(error);
    } finally {
      this.busy = false;
    }
  }

  private async selectCard(card: DocumentCard): Promise<void> {
    if (this.selectedId === card.ingestionId) {
      if (!this.dirty) {
        this.clearSelection();
      }
      return;
    }

    const draft = this.drafts.find((item) => item.ingestionId === card.ingestionId);
    if (draft) {
      this.selectDetail(draft, false);
      return;
    }

    const cached = this.detailCache.get(card.ingestionId);
    if (cached) {
      this.selectDetail(cached, false);
      return;
    }

    this.errorMessage = '';
    this.selectedId = card.ingestionId;
    try {
      this.selectDetail(parseJson<DocumentDetail>(await this.server().loadDocument(card.ingestionId)), false);
    } catch (error) {
      this.errorMessage = message(error);
      this.selectedId = '';
    }
  }

  private selectDetail(detail: DocumentDetail, dirty: boolean): void {
    this.detailCache.set(detail.ingestionId, cloneDetail(detail));
    this.selectedDocument = cloneDetail(detail);
    this.selectedId = detail.ingestionId;
    this.dirty = dirty;
    this.catalogUseWhen = detail.catalog?.useWhen ?? '';
    this.activeTab = detail.catalog ? 'segments' : 'catalog';
    this.setSelectedUrl(detail.ingestionId);
  }

  private async generateCatalog(): Promise<void> {
    const detail = this.selectedDocument;
    if (!detail) {
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    try {
      const catalog = parseJson<CourseMaterialCatalog>(
        await this.server().generateCatalog(detail.title, this.catalogUseWhen, JSON.stringify(detail.segments))
      );
      this.selectedDocument = { ...detail, catalog };
      this.replaceDraft(this.selectedDocument);
      this.statusMessage = 'Catálogo generado. Ya puedes indexar el documento.';
    } catch (error) {
      this.errorMessage = message(error);
    } finally {
      this.busy = false;
    }
  }

  private async commit(): Promise<void> {
    const detail = this.selectedDocument;
    if (!detail || !detail.catalog) {
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    try {
      const next =
        detail.status === 'REVIEW_READY'
          ? parseJson<DocumentDetail>(
            await this.server().indexDraft(
              detail.ingestionId,
              detail.title,
              JSON.stringify(detail.catalog),
              detail.markdown,
              JSON.stringify(detail.segments)
            )
          )
          : parseJson<DocumentDetail>(await this.server().reindexDocument(JSON.stringify(detail)));
      this.drafts = this.drafts.filter((draft) => draft.ingestionId !== next.ingestionId);
      this.detailCache.set(next.ingestionId, cloneDetail(next));
      this.selectDetail(next, false);
      this.documents = parseJson<DocumentCard[]>(await this.server().loadDocuments());
      this.statusMessage = 'Documento indexado para el tutor.';
    } catch (error) {
      this.errorMessage = message(error);
    } finally {
      this.busy = false;
    }
  }

  private async deleteSelected(): Promise<void> {
    const detail = this.selectedDocument;
    if (!detail || !window.confirm(`Eliminar "${detail.title}"?`)) {
      return;
    }
    if (detail.status === 'REVIEW_READY') {
      this.drafts = this.drafts.filter((draft) => draft.ingestionId !== detail.ingestionId);
      this.clearSelection();
      return;
    }
    this.documents = parseJson<DocumentCard[]>(await this.server().deleteDocument(detail.ingestionId));
    this.detailCache.delete(detail.ingestionId);
    this.clearSelection();
  }

  private async deleteFromCard(event: Event, card: DocumentCard): Promise<void> {
    event.stopPropagation();
    if (this.selectedId !== card.ingestionId) {
      await this.selectCard(card);
    }
    await this.deleteSelected();
  }

  private deleteSegment(segmentId: string): void {
    const detail = this.selectedDocument;
    if (!detail || !window.confirm('Eliminar este segmento del documento?')) {
      return;
    }
    const nextSegments = detail.segments
      .filter((segment) => segment.id !== segmentId)
      .map((segment, index) => ({ ...segment, ordinal: index + 1 }));
    this.updateDetail({ ...detail, segments: nextSegments }, detail.status === 'INDEXED');
  }

  private updateSegment(segmentId: string, event: CustomEvent<{ value?: string }>): void {
    const detail = this.selectedDocument;
    if (!detail) {
      return;
    }
    const nextContent = event.detail.value ?? '';
    const currentSegment = detail.segments.find((segment) => segment.id === segmentId);
    if (!currentSegment || currentSegment.content === nextContent) {
      return;
    }
    const nextSegments = detail.segments.map((segment) =>
      segment.id === segmentId ? { ...segment, content: nextContent } : segment
    );
    this.updateDetail({ ...detail, segments: nextSegments }, detail.status === 'INDEXED');
  }

  private updateMarkdown(event: CustomEvent<{ value?: string }>): void {
    const detail = this.selectedDocument;
    if (!detail) {
      return;
    }
    const nextMarkdown = event.detail.value ?? '';
    if (detail.markdown === nextMarkdown) {
      return;
    }
    this.updateDetail({ ...detail, markdown: nextMarkdown }, detail.status === 'INDEXED');
  }

  private updateDetail(detail: DocumentDetail, indexedMutation: boolean): void {
    this.selectedDocument = detail;
    this.detailCache.set(detail.ingestionId, cloneDetail(detail));
    this.dirty = this.dirty || indexedMutation;
    if (detail.status === 'REVIEW_READY') {
      this.replaceDraft(detail);
    }
  }

  private replaceDraft(detail: DocumentDetail): void {
    this.drafts = [detail, ...this.drafts.filter((draft) => draft.ingestionId !== detail.ingestionId)];
  }

  private clearSelection(): void {
    this.selectedDocument = null;
    this.selectedId = '';
    this.dirty = false;
    this.setSelectedUrl(null);
  }

  private activateCard(event: KeyboardEvent, card: DocumentCard): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      void this.selectCard(card);
    }
  }

  private cards(): DocumentCard[] {
    const draftCards = this.drafts.map((draft) => ({
      ingestionId: draft.ingestionId,
      title: draft.title,
      status: 'REVIEW_READY',
      segmentCount: draft.segments.length,
      catalogLabel: draft.catalog?.label ?? '',
      catalogUseWhen: draft.catalog?.useWhen ?? 'Pendiente de catálogo e indexación.',
    }));
    return [...draftCards, ...this.documents];
  }

  private pageSummary(segment: EditableSegment): string {
    if (segment.pageNumbers.length > 0) {
      return ` · páginas ${segment.pageNumbers.join(', ')}`;
    }
    if (segment.pageNumber !== null) {
      return ` · página ${segment.pageNumber}`;
    }
    return '';
  }

  private statusLabel(status: string): string {
    if (status === 'INDEXED') {
      return 'Indexado';
    }
    if (status === 'REVIEW_READY') {
      return 'Revisión';
    }
    if (status === 'DIRTY') {
      return 'Cambios sin indexar';
    }
    return status || 'Pendiente';
  }

  private setSelectedUrl(ingestionId: string | null): void {
    const url = new URL(window.location.href);
    if (ingestionId) {
      url.searchParams.set('d', ingestionId);
    } else {
      url.searchParams.delete('d');
    }
    window.history.replaceState({}, '', url);
  }

  private server(): ServerBridge {
    return (this as unknown as { $server: ServerBridge }).$server;
  }

}

function parseJson<T>(value: string): T {
  return JSON.parse(value) as T;
}

function cloneDetail(detail: DocumentDetail): DocumentDetail {
  return {
    ...detail,
    catalog: detail.catalog ? { ...detail.catalog, aliases: [...detail.catalog.aliases] } : null,
    segments: detail.segments.map((segment) => ({ ...segment })),
    vectorIds: [...detail.vectorIds],
  };
}

function message(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return 'Ocurrió un error inesperado.';
}

if (!customElements.get('document-ingestion-workspace')) {
  customElements.define('document-ingestion-workspace', DocumentIngestionWorkspaceElement);
}
