import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/text-area';
import { LitElement, html, nothing } from 'lit';
import { repeat } from 'lit/directives/repeat.js';

type EditableSegment = {
  id: string;
  ordinal: number;
  headingPath: string;
  content: string;
  charCount: number | null;
  tokenCount: number | null;
  pageNumber: number | null;
  pageNumbers: number[];
  captions: string[];
  docItems: string[];
};

function normalizeSegments(value: unknown): EditableSegment[] {
  const parsed = typeof value === 'string' ? JSON.parse(value) as unknown : value;
  if (!Array.isArray(parsed)) {
    return [];
  }
  return parsed.map(normalizeSegment);
}

function normalizeSegment(value: unknown): EditableSegment {
  const source = isRecord(value) ? value : {};
  return {
    id: stringValue(source.id),
    ordinal: numberValue(source.ordinal),
    headingPath: stringValue(source.headingPath),
    content: stringValue(source.content),
    charCount: nullableNumberValue(source.charCount),
    tokenCount: nullableNumberValue(source.tokenCount),
    pageNumber: nullableNumberValue(source.pageNumber),
    pageNumbers: numberArrayValue(source.pageNumbers),
    captions: stringArrayValue(source.captions),
    docItems: stringArrayValue(source.docItems),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function numberValue(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function nullableNumberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function stringArrayValue(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function numberArrayValue(value: unknown): number[] {
  return Array.isArray(value)
    ? value.filter((item): item is number => typeof item === 'number' && Number.isFinite(item))
    : [];
}

class DocumentSegmentEditorListElement extends LitElement {
  static readonly properties = {
    segments: { type: Array },
  };

  declare segments: EditableSegment[];

  constructor() {
    super();
    this.segments = [];
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setSegments(value: unknown): void {
    this.segments = normalizeSegments(value);
  }

  protected render() {
    return html`
      <div id="document-ingestion-segment-list" class="document-ingest-segment-list">
        ${repeat(this.segments, (segment) => segment.id, (segment) => this.renderSegment(segment))}
      </div>
    `;
  }

  private renderSegment(segment: EditableSegment) {
    return html`
      <div
        id=${`document-ingestion-segment-card-${segment.ordinal}`}
        class="document-ingest-segment-card"
        data-segment-id=${segment.id}
      >
        <div class="document-ingest-segment-card-header">
          <span class="document-ingest-segment-ordinal">segmento ${segment.ordinal}</span>
          <vaadin-button
            id=${`document-ingestion-segment-delete-${segment.ordinal}`}
            class="document-ingest-segment-delete-button"
            theme="error tertiary"
            aria-label=${`Eliminar segmento ${segment.ordinal}`}
            @click=${() => this.deleteSegment(segment.id)}
          >
            <vaadin-icon icon="vaadin:trash" slot="prefix"></vaadin-icon>
            Eliminar
          </vaadin-button>
        </div>
        <span class="document-ingest-segment-heading">
          ${segment.headingPath.trim().length === 0 ? 'Documento' : segment.headingPath}
        </span>
        <p class="document-ingest-segment-meta">
          ${segment.charCount ?? 0} caracteres · ${segment.tokenCount ?? 0} tokens${this.pageSummary(segment)}
        </p>
        ${this.renderProvenance(segment)}
        <vaadin-text-area
          id=${`document-ingestion-segment-text-${segment.ordinal}`}
          class="document-ingest-segment-text"
          data-segment-id=${segment.id}
          .value=${segment.content}
          maxlength="8000"
          style="width: 100%; min-height: 10rem;"
          @value-changed=${(event: CustomEvent<{ value?: string }>) => this.changeSegment(segment.id, event)}
        ></vaadin-text-area>
      </div>
    `;
  }

  private renderProvenance(segment: EditableSegment) {
    const entries = [
      segment.captions.length > 0 ? `captions: ${segment.captions.join(' · ')}` : null,
      segment.docItems.length > 0 ? `refs: ${segment.docItems.join(' · ')}` : null,
    ].filter((entry): entry is string => entry !== null);

    return entries.length > 0
      ? html`
          <div class="document-ingest-segment-provenance">
            ${entries.map((entry) => html`<span>${entry}</span>`)}
          </div>
        `
      : nothing;
  }

  private pageSummary(segment: EditableSegment): string {
    if (segment.pageNumbers.length > 0) {
      return ` · paginas ${segment.pageNumbers.join(', ')}`;
    }
    if (segment.pageNumber !== null) {
      return ` · pagina ${segment.pageNumber}`;
    }
    return '';
  }

  private changeSegment(id: string, event: CustomEvent<{ value?: string }>): void {
    this.dispatchEvent(new CustomEvent('segment-content-changed', {
      detail: { id, content: event.detail.value ?? '' },
      bubbles: true,
      composed: true,
    }));
  }

  private deleteSegment(id: string): void {
    this.dispatchEvent(new CustomEvent('segment-delete-requested', {
      detail: { id },
      bubbles: true,
      composed: true,
    }));
  }
}

if (!customElements.get('document-segment-editor-list')) {
  customElements.define('document-segment-editor-list', DocumentSegmentEditorListElement);
}
