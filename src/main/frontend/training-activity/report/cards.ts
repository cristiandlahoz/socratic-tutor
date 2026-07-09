import '../../conversation/markdown-renderer.js';
import { LitElement, html, nothing } from 'lit';
import { customElement, property } from 'lit/decorators.js';

type ReportQuestion = {
  number: number;
  tutorPrompt: string;
  studentAnswer: string;
};

@customElement('training-activity-report-cards')
export class TrainingActivityReportCardsElement extends LitElement {
  @property({ type: String }) itemsJson = '[]';

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    const questions = this.parseItems();
    if (questions.length === 0) {
      return nothing;
    }
    return html`${questions.map((question) => this.renderQuestion(question))}`;
  }

  private renderQuestion(question: ReportQuestion) {
    return html`
      <article class="training-activity-conversation-card">
        <div class="training-activity-conversation-card-header">
          <span class="training-activity-conversation-title">Pregunta ${question.number}</span>
          <span class="training-activity-conversation-badge">RESPONDIDA</span>
        </div>
        <div class="training-activity-conversation-body">
          ${this.renderMessage('Tutor Socrático', question.tutorPrompt, true)}
          ${this.renderMessage('Estudiante', question.studentAnswer, false)}
        </div>
      </article>
    `;
  }

  private renderMessage(author: string, text: string, tutor: boolean) {
    return html`
      <div class="training-activity-conversation-row ${tutor ? 'training-activity-conversation-row--tutor' : 'training-activity-conversation-row--student'}">
        <span class="training-activity-conversation-author">${author}</span>
        <markdown-renderer
          class="training-activity-conversation-message ${tutor ? 'training-activity-conversation-message--tutor' : 'training-activity-conversation-message--student'}"
          .content=${text}
          .debuggableCodeBlocks=${false}
        ></markdown-renderer>
      </div>
    `;
  }

  private parseItems(): ReportQuestion[] {
    if (!this.itemsJson || !this.itemsJson.trim()) {
      return [];
    }
    try {
      const parsed = JSON.parse(this.itemsJson) as ReportQuestion[];
      return Array.isArray(parsed) ? parsed : [];
    }
    catch {
      return [];
    }
  }
}
