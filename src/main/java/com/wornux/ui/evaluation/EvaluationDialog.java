package com.wornux.ui.evaluation;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import com.wornux.data.entities.Evaluation;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService;
import com.wornux.services.evaluation.EvaluationService;
import java.util.function.Consumer;

public class EvaluationDialog extends Dialog {

  private final Evaluation original;
  private final EvaluationService evaluationService;
  private final Consumer<Evaluation> onSave;
  private final EvaluationQuestionGenerationService questionGenerationService;

  private final TextField titleField;
  private final TextArea instructionField;
  private final Div questionsSection;
  private final Div answersSection;
  private final Div reportSection;
  private final Button saveButton;

  public EvaluationDialog(
      Evaluation evaluation,
      EvaluationService evaluationService,
      EvaluationQuestionGenerationService questionGenerationService,
      Consumer<Evaluation> onSave) {
    this.original = evaluation;
    this.evaluationService = evaluationService;
    this.questionGenerationService = questionGenerationService;
    this.onSave = onSave;

    setHeaderTitle("Evaluación: " + evaluation.getTitle());
    setWidth("48rem");
    setMinHeight("30rem");
    setCloseOnOutsideClick(true);
    setCloseOnEsc(true);

    titleField = new TextField("Título");
    titleField.setWidthFull();
    titleField.setValue(evaluation.getTitle());

    instructionField = new TextArea("Instrucción");
    instructionField.setWidthFull();
    instructionField.setMinHeight("6rem");
    instructionField.setValue(evaluation.getInstruction());

    questionsSection = buildQuestionsSection(evaluation);
    answersSection = buildAnswersSection(evaluation);
    reportSection = buildReportSection(evaluation);

    var body = new VerticalLayout(
        titleField,
        instructionField,
        new Hr(),
        questionsSection,
        new Hr(),
        answersSection,
        new Hr(),
        reportSection);
    body.setPadding(false);
    body.setSpacing(true);
    add(body);

    saveButton = new Button("Guardar cambios", _ -> onSaveClick());
    saveButton.addThemeVariants(ButtonVariant.PRIMARY);

    var closeButton = new Button("Cerrar", _ -> close());

    var footer = new HorizontalLayout(saveButton, closeButton);
    footer.setPadding(false);
    getFooter().add(footer);
  }

  private Div buildQuestionsSection(Evaluation evaluation) {
    var title = new H3("Preguntas generadas");
    title.getStyle().set("margin", "0");

    var container = new Div();
    container.addClassName("evaluation-dialog-questions");

    var json = evaluation.getQuestionsJson();
    if (json == null || json.isBlank()) {
      container.add(new Span("Aún no se generaron preguntas."));
    } else {
      try {
        var questions = questionGenerationService.fromJson(json);
        for (int i = 0; i < questions.size(); i++) {
          var q = questions.get(i);
          var item = new Div(
              new Span((i + 1) + ". "),
              new Span(q.questionText()));
          item.addClassName("evaluation-dialog-question-item");
          container.add(item);
        }
      } catch (Exception e) {
        container.add(new Span("Error al leer las preguntas."));
      }
    }

    return new Div(title, container);
  }

  private Div buildAnswersSection(Evaluation evaluation) {
    var title = new H3("Respuestas del estudiante");
    title.getStyle().set("margin", "0");

    var container = new Div();
    container.addClassName("evaluation-dialog-answers");

    var json = evaluation.getAnswersJson();
    if (json == null || json.isBlank()) {
      container.add(new Span("Aún no se registraron respuestas."));
    } else {
      container.add(new Span("Respuestas guardadas (formato JSON)."));
    }

    var section = new Div(title, container);
    section.setVisible(json != null && !json.isBlank());
    return section;
  }

  private Div buildReportSection(Evaluation evaluation) {
    var title = new H3("Reporte evaluativo");
    title.getStyle().set("margin", "0");

    var container = new Div();
    container.addClassName("evaluation-dialog-report");

    var markdown = evaluation.getReportMarkdown();
    if (markdown == null || markdown.isBlank()) {
      container.add(new Span("Aún no se generó el reporte."));
    } else {
      var markdownEl = new Element("vaadin-markdown");
      markdownEl.setProperty("content", markdown);
      var wrapper = new Div();
      wrapper.addClassName("evaluation-report-markdown");
      wrapper.getElement().appendChild(markdownEl);
      container.add(wrapper);
    }

    var section = new Div(title, container);
    section.setVisible(markdown != null && !markdown.isBlank());
    return section;
  }

  private void onSaveClick() {
    var title = titleField.getValue().trim();
    var instruction = instructionField.getValue().trim();

    if (title.isBlank() || instruction.isBlank()) {
      Notification.show("El título y la instrucción no pueden estar vacíos");
      return;
    }

    try {
      var updated = evaluationService.update(original.getId(), title, instruction);
      Notification.show("Cambios guardados");
      close();
      if (onSave != null) {
        onSave.accept(updated);
      }
    } catch (Exception e) {
      Notification.show("Error al guardar: " + e.getMessage());
    }
  }
}
