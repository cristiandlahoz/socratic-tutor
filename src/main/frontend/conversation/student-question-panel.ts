import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/popover';
import '@vaadin/text-area';
import { LitElement, html, nothing } from 'lit';
import { repeat } from 'lit/directives/repeat.js';
import { renderConversationDisclaimer } from './conversation-disclaimer.js';

type StudentQuestionOption = {
  label: string;
  description: string;
};

type StudentQuestion = {
  question: string;
  options: StudentQuestionOption[];
};

type StudentQuestionSet = {
  questions: StudentQuestion[];
};

type StudentQuestionAnswer = {
  questionId: string;
  selectedOptionLabels: string[];
  customText: string;
};

type StudentQuestionResponse = {
  answers: StudentQuestionAnswer[];
};

type AnswerDraft = {
  selectedOptionKey: string | null;
  customText: string;
};

function normalizeQuestionSet(value: unknown): StudentQuestionSet | null {
  if (value == null || value === '') {
    return null;
  }

  const parsed = typeof value === 'string' ? JSON.parse(value) as unknown : value;
  if (!isRecord(parsed) || !Array.isArray(parsed.questions)) {
    return null;
  }

  return {
    questions: parsed.questions.map(normalizeQuestion),
  };
}

function normalizeQuestion(value: unknown): StudentQuestion {
  if (!isRecord(value)) {
    return { question: '', options: [] };
  }

  return {
    question: stringValue(value.question),
    options: Array.isArray(value.options) ? value.options.map(normalizeOption) : [],
  };
}

function normalizeOption(value: unknown): StudentQuestionOption {
  if (!isRecord(value)) {
    return { label: '', description: '' };
  }

  return {
    label: stringValue(value.label),
    description: stringValue(value.description),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function questionKey(index: number): string {
  return `q${index}`;
}

function optionKey(index: number): string {
  return `o${index}`;
}

class StudentQuestionPanelElement extends LitElement {
  static readonly properties = {
    questionSet: { attribute: false },
    submitting: { type: Boolean, reflect: true },
  };

  declare questionSet: StudentQuestionSet | null;
  declare submitting: boolean;

  private activeQuestionIndex = 0;
  private answerDrafts = new Map<string, AnswerDraft>();

  constructor() {
    super();
    this.questionSet = null;
    this.submitting = false;
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  setQuestionSet(value: unknown): void {
    this.questionSet = normalizeQuestionSet(value);
    this.resetAnswers();
  }

  protected willUpdate(changedProperties: Map<PropertyKey, unknown>): void {
    if (changedProperties.has('questionSet')) {
      this.resetAnswers();
    }
  }

  private resetAnswers(): void {
    this.activeQuestionIndex = 0;
    this.answerDrafts = new Map();
    this.ensureDrafts();
  }

  setSubmitting(value: boolean): void {
    this.submitting = Boolean(value);
  }

  protected render() {
    const questions = this.questionSet?.questions ?? [];
    const open = questions.length > 0;
    const activeQuestion = questions[this.activeQuestionIndex];

    this.classList.toggle('conversation-question--open', open);
    this.classList.toggle('conversation-question--submitting', this.submitting);

    if (!open || !activeQuestion) {
      return nothing;
    }

    return html`
      <div class="conversation-question__header-row">
        <span class="conversation-question__title">${activeQuestion.question}</span>
        <span class="conversation-question__progress">${this.activeQuestionIndex + 1} / ${questions.length}</span>
      </div>
      <div class="conversation-question__viewport">
        ${activeQuestion.options.length > 0 ? this.renderQuestionCard(activeQuestion) : nothing}
      </div>
      <div class="conversation-question__composer">
        ${this.renderResponseComposer(activeQuestion)}
      </div>
      ${renderConversationDisclaimer()}
    `;
  }

  private renderQuestionCard(question: StudentQuestion) {
    return html`
      <div class="conversation-question__card">
        <div class="conversation-question__options">
          ${repeat(question.options, (_, index) => optionKey(index), (option, index) => this.renderOption(option, index))}
        </div>
      </div>
    `;
  }

  private renderOption(option: StudentQuestionOption, index: number) {
    const currentQuestionKey = questionKey(this.activeQuestionIndex);
    const currentOptionKey = optionKey(index);
    const selected = this.draftFor(currentQuestionKey).selectedOptionKey === currentOptionKey;

    return html`
      <div class="conversation-question__option-row ${selected ? 'is-selected' : ''}">
        <div class="conversation-question__option-mobile-header">
          <vaadin-button
            class="conversation-question__option ${selected ? 'is-selected' : ''}"
            theme="tertiary"
            data-question-id=${currentQuestionKey}
            data-option-index=${index}
            aria-pressed=${selected ? 'true' : 'false'}
            ?disabled=${this.submitting}
            @click=${() => this.toggleOption(currentQuestionKey, currentOptionKey)}
          >
            <div class="conversation-question__option-copy">
              <span class="conversation-question__option-label">${option.label}</span>
              <p class="conversation-question__option-description conversation-question__option-description--inline">
                ${option.description}
              </p>
            </div>
          </vaadin-button>
          <vaadin-button
            id=${this.infoButtonId(currentQuestionKey, index)}
            class="conversation-question__option-info"
            theme="tertiary"
            aria-label=${`Ver detalle de ${option.label}`}
            ?disabled=${this.submitting}
          >
            <vaadin-icon src="/icons/IconInfo.svg" aria-hidden="true"></vaadin-icon>
          </vaadin-button>
          <vaadin-popover class="conversation-question__option-popover" for=${this.infoButtonId(currentQuestionKey, index)}>
            <p class="conversation-question__option-description conversation-question__option-description--popover">
              ${option.description}
            </p>
          </vaadin-popover>
        </div>
      </div>
    `;
  }

  private renderResponseComposer(question: StudentQuestion) {
    const currentQuestionKey = questionKey(this.activeQuestionIndex);
    const openQuestion = question.options.length === 0;

    return html`
      <div class="conversation-question__composer-wrap">
        <vaadin-text-area
          class="conversation-question__custom-text"
          data-question-id=${currentQuestionKey}
          .value=${this.draftFor(currentQuestionKey).customText}
          ?disabled=${this.submitting}
          placeholder=${openQuestion ? 'Escribe tu respuesta...' : 'Agrega contexto extra si quieres...'}
          aria-label=${openQuestion ? 'Respuesta a la pregunta' : 'Respuesta complementaria'}
          @value-changed=${(event: CustomEvent<{ value?: string }>) => this.updateCustomText(currentQuestionKey, event)}
        ></vaadin-text-area>
        <div class="conversation-question__composer-actions">
          ${this.renderPreviousButton()}
          ${this.renderNextButton()}
          ${this.renderSubmitButton()}
        </div>
      </div>
    `;
  }

  private renderPreviousButton() {
    const visible = this.activeQuestionIndex > 0;
    return visible
      ? html`
          <vaadin-button
            class="conversation-question__nav-button"
            theme="tertiary"
            aria-label="Pregunta anterior"
            ?disabled=${this.submitting}
            @click=${this.showPreviousQuestion}
          >
            <vaadin-icon src="/icons/IconArrowLeftBar.svg" aria-hidden="true"></vaadin-icon>
          </vaadin-button>
        `
      : nothing;
  }

  private renderNextButton() {
    const total = this.questionSet?.questions.length ?? 0;
    const visible = this.activeQuestionIndex < total - 1;
    return visible
      ? html`
          <vaadin-button
            class="conversation-question__nav-button"
            theme="tertiary"
            aria-label="Pregunta siguiente"
            ?disabled=${this.submitting}
            @click=${this.showNextQuestion}
          >
            <vaadin-icon src="/icons/IconArrowRight.svg" aria-hidden="true"></vaadin-icon>
          </vaadin-button>
        `
      : nothing;
  }

  private renderSubmitButton() {
    const total = this.questionSet?.questions.length ?? 0;
    const visible = total > 0 && this.activeQuestionIndex === total - 1;
    return visible
      ? html`
          <vaadin-button
            class="conversation-question__submit-button"
            theme="tertiary"
            ?disabled=${this.submitting || !this.canSubmit()}
            @click=${this.submitAnswers}
          >
            Enviar
          </vaadin-button>
        `
      : nothing;
  }

  private ensureDrafts(): void {
    for (let index = 0; index < (this.questionSet?.questions.length ?? 0); index += 1) {
      const key = questionKey(index);
      if (!this.answerDrafts.has(key)) {
        this.answerDrafts.set(key, { selectedOptionKey: null, customText: '' });
      }
    }
  }

  private draftFor(key: string): AnswerDraft {
    const draft = this.answerDrafts.get(key);
    if (draft) {
      return draft;
    }

    const nextDraft = { selectedOptionKey: null, customText: '' };
    this.answerDrafts.set(key, nextDraft);
    return nextDraft;
  }

  private toggleOption(questionId: string, selectedOptionKey: string): void {
    if (this.submitting) {
      return;
    }

    const draft = this.draftFor(questionId);
    draft.selectedOptionKey = draft.selectedOptionKey === selectedOptionKey ? null : selectedOptionKey;
    this.requestUpdate();
  }

  private updateCustomText(questionId: string, event: CustomEvent<{ value?: string }>): void {
    this.draftFor(questionId).customText = event.detail.value ?? '';
    this.requestUpdate();
  }

  private showPreviousQuestion = (): void => {
    this.activeQuestionIndex = Math.max(0, this.activeQuestionIndex - 1);
    this.requestUpdate();
  };

  private showNextQuestion = (): void => {
    const total = this.questionSet?.questions.length ?? 0;
    this.activeQuestionIndex = Math.min(total - 1, this.activeQuestionIndex + 1);
    this.requestUpdate();
  };

  private canSubmit(): boolean {
    const questions = this.questionSet?.questions ?? [];
    return questions.length > 0 && questions.every((_, index) => {
      const draft = this.draftFor(questionKey(index));
      return draft.selectedOptionKey !== null || draft.customText.trim().length > 0;
    });
  }

  private submitAnswers = (): void => {
    if (!this.canSubmit() || !this.questionSet) {
      return;
    }

    const response: StudentQuestionResponse = {
      answers: this.questionSet.questions.map((question, index) => {
        const key = questionKey(index);
        const draft = this.draftFor(key);
        const selectedIndex = draft.selectedOptionKey === null ? -1 : Number(draft.selectedOptionKey.substring(1));
        const selectedOption = selectedIndex >= 0 ? question.options[selectedIndex] : undefined;
        return {
          questionId: key,
          selectedOptionLabels: selectedOption ? [selectedOption.label] : [],
          customText: draft.customText.trim(),
        };
      }),
    };

    this.dispatchEvent(new CustomEvent('submit-question-response', {
      detail: {
        response,
        responseJson: JSON.stringify(response),
      },
      bubbles: true,
      composed: true,
    }));
  };

  private infoButtonId(questionId: string, index: number): string {
    return `question-${questionId}-option-${index}-info`;
  }
}

if (!customElements.get('student-question-panel')) {
  customElements.define('student-question-panel', StudentQuestionPanelElement);
}
