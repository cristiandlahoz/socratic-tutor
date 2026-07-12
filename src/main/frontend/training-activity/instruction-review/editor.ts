import { EditorSelection, EditorState } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { LitElement, html, nothing } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';

import { editorTheme, issueDecorationExtension, type IssueDecorationHost } from './codemirror.js';
import { instructionLinterStyles } from './styles.js';
import type {
  InstructionLintIssue,
  InstructionReviewSnapshot,
  IssuePresentationState,
  QualityStatus,
  ReviewStatus,
} from './types.js';
import { hasInlineRange, hasSuggestion, issueReplacementRange, normalizeSnapshot } from './utils.js';

const HOVER_CARD_CLOSE_DELAY_MS = 140;

@customElement('instruction-linter-editor')
export class InstructionLinterEditorElement extends LitElement implements IssueDecorationHost {
  static styles = instructionLinterStyles;

  @property({ type: String }) value = '';
  @property({ type: String }) label = 'Instrucciones';
  @property({ type: String }) reviewSnapshot = '';
  @property({ type: Boolean }) stale = false;
  @property({ type: Boolean }) reviewing = false;
  @query('.instruction-linter-editor__editor')
  private editorHost?: HTMLDivElement;

  private editorView: EditorView | null = null;
  private parsedSnapshot: InstructionReviewSnapshot | null = null;
  private syncingEditorValue = false;
  private applyingServerSnapshot = false;
  private issueStates = new Map<string, IssuePresentationState>();
  private reviewStatus: ReviewStatus = 'IDLE';
  private qualityStatus?: QualityStatus;
  private message = '';
  private issues: InstructionLintIssue[] = [];
  private floatingCardEl?: HTMLDivElement;
  private hoverCardCloseTimeoutId: number | null = null;
  private pointerInsideIssue = false;
  private pointerInsideCard = false;

  @state()
  private activeIssue: InstructionLintIssue | null = null;

  private readonly outsideClickListener = (event: MouseEvent) => {
    const path = event.composedPath();
    if (!path.includes(this) && (!this.floatingCardEl || !path.includes(this.floatingCardEl))) {
      this.closeHoverCard();
    }
  };

  private readonly escapeKeyListener = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      this.closeHoverCard();
    }
  };

  private readonly floatingCardPointerEnterListener = () => {
    this.pointerInsideCard = true;
    this.clearHoverCardCloseTimer();
  };

  private readonly floatingCardPointerLeaveListener = () => {
    this.pointerInsideCard = false;
    this.scheduleHoverCardClose();
  };

  private readonly repositionActiveCard = () => {
    if (!this.activeIssue || !this.editorView || !this.floatingCardEl?.isConnected) {
      this.closeHoverCard();
      return;
    }
    this.positionFloatingCard(this.activeIssue, this.editorView);
  };

  get snapshot(): InstructionReviewSnapshot | null {
    return this.parsedSnapshot;
  }

  get visibleIssues(): InstructionLintIssue[] {
    return this.issues.filter((issue) => {
      const state = this.issueStates.get(issue.issueKey) ?? 'OPEN';
      return state !== 'APPLIED';
    });
  }

  firstUpdated(): void {
    this.createEditor();
    document.addEventListener('click', this.outsideClickListener);
  }

  disconnectedCallback(): void {
    document.removeEventListener('click', this.outsideClickListener);
    document.removeEventListener('keydown', this.escapeKeyListener);
    this.removeFloatingCardListeners();
    this.floatingCardEl?.remove();
    this.floatingCardEl = undefined;
    this.editorView?.destroy();
    this.editorView = null;
    super.disconnectedCallback();
  }

  protected willUpdate(changedProperties: Map<string, unknown>): void {
    if (changedProperties.has('reviewSnapshot') && !this.applyingServerSnapshot) {
      const snapshot = normalizeSnapshot(this.reviewSnapshot);
      if (snapshot) {
        this.applyReviewSnapshot(snapshot);
      } else if (!this.reviewSnapshot) {
        this.parsedSnapshot = null;
      }
    }
  }

  protected updated(changedProperties: Map<string, unknown>): void {
    if (!this.editorView && this.editorHost) {
      this.createEditor();
    }
    if (changedProperties.has('value')) {
      this.syncEditorValue();
    }
    if (this.editorView && (changedProperties.has('reviewSnapshot')
      || changedProperties.has('reviewing')
      || changedProperties.has('stale')
      || changedProperties.has('value'))) {
      this.refreshEditorAnnotations();
    }
  }

  public applyReviewSnapshot(snapshot: string | InstructionReviewSnapshot | null): void {
    const normalizedSnapshot = normalizeSnapshot(snapshot);
    if (!normalizedSnapshot) {
      this.resetReviewState('server-reset');
      return;
    }

    this.applyingServerSnapshot = true;
    this.reviewSnapshot = typeof snapshot === 'string' ? snapshot : JSON.stringify(normalizedSnapshot);
    this.parsedSnapshot = normalizedSnapshot;
    this.reviewStatus = normalizedSnapshot.reviewStatus ?? 'IDLE';
    this.qualityStatus = normalizedSnapshot.qualityStatus ?? undefined;
    this.message = normalizedSnapshot.message ?? '';
    this.issues = normalizedSnapshot.issues ?? [];
    this.issueStates = new Map();
    this.stale = false;
    this.reviewing = this.isReviewingStatus(this.reviewStatus);
    this.closeHoverCard();
    this.requestUpdate();
    if (this.editorView) {
      this.refreshEditorAnnotations();
    }
    this.applyingServerSnapshot = false;
  }

  public resetReviewState(_reason: 'clear' | 'activity-change' | 'empty-input' | 'saved' | 'server-reset' = 'saved'): void {
    this.reviewStatus = 'IDLE';
    this.qualityStatus = undefined;
    this.reviewing = false;
    this.message = '';
    this.issues = [];
    this.issueStates = new Map();
    this.reviewSnapshot = '';
    this.parsedSnapshot = null;
    this.stale = false;
    this.closeHoverCard();
    this.requestUpdate();
    if (this.editorView) {
      this.refreshEditorAnnotations();
    }
  }

  public closeIssueCard(): void {
    this.closeHoverCard();
    this.editorView?.focus();
  }

  public inlineIssueMarkClass(): string | null {
    if (this.visibleIssues.some((issue) => issue.severity === 'ERROR')) {
      return 'instruction-linter-mark instruction-linter-mark--error';
    }
    if (this.visibleIssues.length > 0) {
      return 'instruction-linter-mark instruction-linter-mark--warning';
    }
    return null;
  }

  public showIssueAtPointer(event: MouseEvent, view: EditorView): void {
    if (!this.supportsInlineIssueCards()) {
      this.pointerInsideIssue = false;
      this.closeHoverCard();
      return;
    }
    const position = view.posAtCoords({ x: event.clientX, y: event.clientY });
    if (position == null) {
      this.pointerInsideIssue = false;
      this.scheduleHoverCardClose();
      return;
    }
    const issue = this.visibleIssues.find((candidate) => hasInlineRange(candidate)
      && position >= candidate.startOffset!
      && position <= candidate.endOffset!);
    if (!issue) {
      this.pointerInsideIssue = false;
      this.scheduleHoverCardClose();
      return;
    }
    this.pointerInsideIssue = true;
    this.clearHoverCardCloseTimer();
    if (this.activeIssue?.issueKey === issue.issueKey && this.floatingCardEl?.isConnected) {
      this.positionFloatingCard(issue, view);
      return;
    }
    this.showIssueCard(issue, view);
  }

  public handleIssuePointerLeave(): void {
    this.pointerInsideIssue = false;
    this.scheduleHoverCardClose();
  }

  public supportsInlineIssueCards(): boolean {
    return !this.stale
      && !this.reviewing
      && this.visibleIssues.some((issue) => hasInlineRange(issue));
  }

  applyRangeSuggestion(issue: InstructionLintIssue): void {
    if (this.reviewing || !hasInlineRange(issue) || !hasSuggestion(issue)) {
      return;
    }
    const { from, to } = issueReplacementRange(issue, this.value);
    const nextValue = `${this.value.slice(0, from)}${issue.suggestedReplacement ?? ''}${this.value.slice(to)}`;
    this.issueStates = new Map(this.issueStates);
    this.issueStates.set(issue.issueKey, 'APPLIED');
    this.closeHoverCard();
    this.applyValue(nextValue, { stale: false });
  }

  protected render() {
    return html`
      <label class="instruction-linter-editor__label">${this.label}</label>
      <div class="instruction-linter-editor__editor"></div>
      ${this.renderStaleMessage()}
      ${this.renderReviewingCard()}
      ${this.renderAnalysisLine()}
    `;
  }

  private renderStaleMessage() {
    if (!this.showStaleMessage()) {
      return nothing;
    }
    return html`<div class="instruction-linter-editor__stale">La revisión anterior quedó desactualizada por cambios en las instrucciones.</div>`;
  }

  private renderReviewingCard() {
    if (!this.shouldShowReviewCard()) {
      return nothing;
    }
    return html`
      <section class="instruction-linter-editor__review" aria-live="polite">
        <div class="instruction-linter-editor__review-header">
          <span class="instruction-linter-editor__review-title">Revisión de instrucciones</span>
          <span class="instruction-linter-editor__badge instruction-linter-editor__badge--reviewing">En curso</span>
          ${this.renderQualityBadge()}
        </div>
        <div class="instruction-linter-editor__loading">
          <span class="instruction-linter-editor__spinner" aria-hidden="true"></span>
          <div>
            <div>Revisando instrucciones...</div>
          </div>
        </div>
      </section>
    `;
  }

  private renderQualityBadge() {
    if (!this.qualityStatus) {
      return nothing;
    }
    return html`<span class="instruction-linter-editor__badge instruction-linter-editor__badge--${this.qualityStatus.toLowerCase()}">${this.qualityStatus}</span>`;
  }

  private renderAnalysisLine() {
    const bottomAnalysisLine = this.bottomAnalysisLine();
    if (!bottomAnalysisLine) {
      return nothing;
    }
    return html`<div class="instruction-linter-editor__analysis-line">${bottomAnalysisLine}</div>`;
  }

  private createEditor(): void {
    if (!this.editorHost || this.editorView) {
      return;
    }
    const state = EditorState.create({
      doc: this.value,
      extensions: [
        EditorView.lineWrapping,
        editorTheme(),
        issueDecorationExtension(this),
        EditorView.updateListener.of((update) => {
          if (!update.docChanged || this.syncingEditorValue) {
            return;
          }
          this.handleManualEditorChange(update.state.doc.toString());
        }),
      ],
    });
    this.editorView = new EditorView({ state, parent: this.editorHost });
  }

  private syncEditorValue(): void {
    if (!this.editorView) {
      return;
    }
    const currentValue = this.editorView.state.doc.toString();
    if (currentValue === this.value) {
      return;
    }
    this.syncingEditorValue = true;
    this.editorView.dispatch({
      changes: { from: 0, to: currentValue.length, insert: this.value },
      selection: EditorSelection.cursor(this.value.length),
    });
    this.syncingEditorValue = false;
  }

  private handleManualEditorChange(newValue: string): void {
    if (this.applyingServerSnapshot) {
      return;
    }
    this.value = newValue;
    if (!newValue.trim()) {
      this.resetReviewState('empty-input');
    } else {
      this.stale = this.reviewStatus !== 'IDLE' && !this.reviewing;
      this.reviewing = false;
      this.closeHoverCard();
    }
    this.dispatchValueChanged();
  }

  private applyValue(nextValue: string, options: { stale: boolean }): void {
    this.value = nextValue;
    this.stale = options.stale;
    this.syncEditorValue();
    this.dispatchValueChanged();
    this.requestUpdate();
    if (this.editorView) {
      this.refreshEditorAnnotations();
    }
  }

  private showIssueCard(issue: InstructionLintIssue, view: EditorView): void {
    this.activeIssue = issue;
    this.clearHoverCardCloseTimer();
    this.renderFloatingCard(issue);
    this.positionFloatingCard(issue, view);
    this.addFloatingCardListeners();
  }

  private closeHoverCard(): void {
    this.activeIssue = null;
    this.pointerInsideIssue = false;
    this.pointerInsideCard = false;
    this.clearHoverCardCloseTimer();
    this.removeFloatingCardListeners();
    this.floatingCardEl?.remove();
    this.floatingCardEl = undefined;
  }

  private ensureFloatingCard(): HTMLDivElement {
    if (!this.floatingCardEl) {
      this.floatingCardEl = document.createElement('div');
      this.floatingCardEl.className = 'instruction-linter-floating-card';
      this.applyFloatingCardBaseStyles(this.floatingCardEl);
      this.floatingCardEl.addEventListener('click', (event) => event.stopPropagation());
      this.floatingCardEl.addEventListener('mouseenter', this.floatingCardPointerEnterListener);
      this.floatingCardEl.addEventListener('mouseleave', this.floatingCardPointerLeaveListener);
      document.body.appendChild(this.floatingCardEl);
    }
    return this.floatingCardEl;
  }

  private renderFloatingCard(issue: InstructionLintIssue): void {
    const card = this.ensureFloatingCard();
    card.replaceChildren();

    const message = document.createElement('strong');
    message.textContent = issue.message;
    message.style.color = '#f9fafb';
    message.style.lineHeight = '1.45';
    card.appendChild(message);

    if (!hasSuggestion(issue)) {
      return;
    }

    const label = document.createElement('div');
    label.textContent = 'Sugerencia';
    label.style.fontSize = '0.72rem';
    label.style.fontWeight = '800';
    label.style.color = '#cbd5e1';
    label.style.textTransform = 'uppercase';
    label.style.letterSpacing = '0.06em';
    card.appendChild(label);

    const suggestion = document.createElement('div');
    suggestion.textContent = issue.suggestedReplacement ?? '';
    suggestion.style.padding = '0.7rem';
    suggestion.style.borderRadius = '0.625rem';
    suggestion.style.background = 'rgba(255, 255, 255, 0.08)';
    suggestion.style.color = '#f8fafc';
    suggestion.style.whiteSpace = 'pre-wrap';
    suggestion.style.wordBreak = 'break-word';
    suggestion.style.fontFamily = "var(--vaadin-font-family-monospace, 'SFMono-Regular', Consolas, monospace)";
    suggestion.style.fontSize = '0.86rem';
    card.appendChild(suggestion);

    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Aplicar';
    button.disabled = this.reviewing;
    button.style.justifySelf = 'start';
    button.style.border = '1px solid rgba(59, 130, 246, 0.55)';
    button.style.borderRadius = '0.625rem';
    button.style.padding = '0.48rem 0.75rem';
    button.style.background = 'rgba(37, 99, 235, 0.2)';
    button.style.color = '#93c5fd';
    button.style.cursor = 'pointer';
    button.style.font = 'inherit';
    button.style.fontWeight = '750';
    button.addEventListener('mouseenter', () => {
      button.style.background = 'rgba(37, 99, 235, 0.32)';
      button.style.color = '#bfdbfe';
    });
    button.addEventListener('mouseleave', () => {
      button.style.background = 'rgba(37, 99, 235, 0.2)';
      button.style.color = '#93c5fd';
    });
    button.addEventListener('click', (event) => {
      event.stopPropagation();
      this.applyRangeSuggestion(issue);
    });
    card.appendChild(button);
  }

  private applyFloatingCardBaseStyles(card: HTMLDivElement): void {
    card.style.position = 'fixed';
    card.style.zIndex = '100000';
    card.style.display = 'grid';
    card.style.gap = '0.65rem';
    card.style.maxWidth = '460px';
    card.style.padding = '0.95rem';
    card.style.border = '1px solid rgba(148, 163, 184, 0.28)';
    card.style.borderRadius = '14px';
    card.style.background = '#0f172a';
    card.style.color = 'white';
    card.style.boxShadow = '0 18px 45px rgba(0, 0, 0, 0.35)';
    card.style.pointerEvents = 'auto';
    card.style.boxSizing = 'border-box';
    card.style.overflow = 'visible';
  }

  private positionFloatingCard(issue: InstructionLintIssue, view: EditorView): void {
    const card = this.ensureFloatingCard();
    const offset = Math.max(0, Math.min(issue.startOffset ?? 0, view.state.doc.length));
    const anchorRect = view.coordsAtPos(offset);
    if (!anchorRect || !this.isRectVisible(anchorRect)) {
      this.closeHoverCard();
      return;
    }

    const margin = 12;
    const gap = 10;
    const maxWidth = Math.min(460, window.innerWidth - margin * 2);
    card.style.maxWidth = `${maxWidth}px`;
    card.style.left = '0px';
    card.style.top = '0px';

    const cardRect = card.getBoundingClientRect();
    let left = anchorRect.left;
    let top = anchorRect.top - cardRect.height - gap;

    if (top < margin) {
      top = anchorRect.bottom + gap;
    }
    if (top + cardRect.height > window.innerHeight - margin) {
      top = Math.max(margin, window.innerHeight - cardRect.height - margin);
    }
    if (left + cardRect.width > window.innerWidth - margin) {
      left = window.innerWidth - cardRect.width - margin;
    }
    if (left < margin) {
      left = margin;
    }

    card.style.left = `${left}px`;
    card.style.top = `${top}px`;
  }

  private isRectVisible(rect: { top: number; right: number; bottom: number; left: number }): boolean {
    return rect.bottom >= 0
      && rect.right >= 0
      && rect.top <= window.innerHeight
      && rect.left <= window.innerWidth;
  }

  private addFloatingCardListeners(): void {
    document.addEventListener('keydown', this.escapeKeyListener);
    window.addEventListener('resize', this.repositionActiveCard);
    window.addEventListener('scroll', this.repositionActiveCard, true);
  }

  private removeFloatingCardListeners(): void {
    document.removeEventListener('keydown', this.escapeKeyListener);
    window.removeEventListener('resize', this.repositionActiveCard);
    window.removeEventListener('scroll', this.repositionActiveCard, true);
  }

  private scheduleHoverCardClose(): void {
    if (!this.activeIssue || this.pointerInsideIssue || this.pointerInsideCard) {
      return;
    }
    this.clearHoverCardCloseTimer();
    this.hoverCardCloseTimeoutId = window.setTimeout(() => {
      if (!this.pointerInsideIssue && !this.pointerInsideCard) {
        this.closeHoverCard();
      }
    }, HOVER_CARD_CLOSE_DELAY_MS);
  }

  private clearHoverCardCloseTimer(): void {
    if (this.hoverCardCloseTimeoutId == null) {
      return;
    }
    window.clearTimeout(this.hoverCardCloseTimeoutId);
    this.hoverCardCloseTimeoutId = null;
  }

  private refreshEditorAnnotations(): void {
    this.editorView?.dispatch({});
  }

  private isReviewingStatus(status: ReviewStatus): boolean {
    return status === 'PENDING' || status === 'REVIEWING';
  }

  private showStaleMessage(): boolean {
    return this.stale && this.reviewStatus !== 'NEEDS_USER_FIX' && !this.reviewing;
  }

  private shouldShowReviewCard(): boolean {
    return this.reviewing;
  }

  private bottomAnalysisLine(): string {
    if (this.reviewing || this.showStaleMessage()) {
      return '';
    }
    if ((this.reviewStatus === 'FAILED' || this.reviewStatus === 'UNAVAILABLE') && !this.message.trim()) {
      return 'No pudimos completar la revisión automática. Intentá guardar de nuevo.';
    }
    const issue = this.visibleIssues[0];
    if (this.qualityStatus === 'GOOD') {
      return issue?.message || this.message || 'Las instrucciones están listas para guardarse y lanzarse.';
    }
    if (this.qualityStatus === 'NEEDS_IMPROVEMENT') {
      return issue?.message || this.message || 'La instrucción es usable, pero todavía debe mejorar antes de guardarse.';
    }
    return this.message || issue?.message || 'No pudimos usar estas instrucciones.';
  }

  private dispatchValueChanged(): void {
    this.dispatchEvent(new CustomEvent('value-changed', {
      detail: { value: this.value },
      bubbles: true,
      composed: true,
    }));
  }
}
