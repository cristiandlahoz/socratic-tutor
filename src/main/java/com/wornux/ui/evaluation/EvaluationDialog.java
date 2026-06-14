package com.wornux.ui.evaluation;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.wornux.data.entities.Evaluation;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService;
import com.wornux.services.evaluation.EvaluationService;
import java.util.function.Consumer;

public class EvaluationDialog extends Div {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Evaluation original;
  private final EvaluationService evaluationService;
  private final Consumer<Evaluation> onSave;
  private final Runnable onClose;
  private final EvaluationQuestionGenerationService questionGenerationService;

  private final TextField titleField;
  private final TextArea instructionField;

  public EvaluationDialog(
      Evaluation evaluation,
      EvaluationService evaluationService,
      EvaluationQuestionGenerationService questionGenerationService,
      Consumer<Evaluation> onSave,
      Runnable onClose) {
    this.original = evaluation;
    this.evaluationService = evaluationService;
    this.questionGenerationService = questionGenerationService;
    this.onSave = onSave;
    this.onClose = onClose;

    addClassName("evaluation-overlay");

    var backdrop = new Div();
    backdrop.addClassName("evaluation-overlay-backdrop");
    backdrop.addClickListener(_ -> close());

    var panel = new Div();
    panel.addClassName("evaluation-overlay-panel");

    var title = new H3("Evaluación: " + evaluation.getTitle());
    title.getStyle().set("margin", "0");

    titleField = new TextField("Título");
    titleField.setWidthFull();
    titleField.setValue(evaluation.getTitle());

    instructionField = new TextArea("Instrucción");
    instructionField.setWidthFull();
    instructionField.setMinHeight("9rem");
    instructionField.setMaxHeight("14rem");
    instructionField.setValue(evaluation.getInstruction());

    var body = new VerticalLayout(
        title,
        titleField,
        instructionField,
        new Hr(),
        buildQuestionsSection(evaluation),
        new Hr(),
        buildAnswersSection(evaluation),
        new Hr(),
        buildReportSection(evaluation));
    body.setPadding(false);
    body.setSpacing(true);
    body.getStyle()
        .set("flex", "1 1 auto")
        .set("min-height", "0")
        .set("overflow-y", "auto")
        .set("padding-right", "0.35rem");

    var saveButton = new Button("Guardar cambios", _ -> onSaveClick());
    saveButton.addThemeVariants(ButtonVariant.PRIMARY);

    var closeButton = new Button("Cerrar", _ -> close());

    var footer = new HorizontalLayout(saveButton, closeButton);
    footer.addClassName("evaluation-overlay-footer");
    footer.setPadding(false);
    footer.setSpacing(true);
    footer.getStyle()
        .set("margin-top", "1rem")
        .set("padding-top", "1rem")
        .set("border-top", "1px solid var(--chat-border-visible)")
        .set("flex-shrink", "0");

    panel.getStyle()
        .set("width", "min(72rem, 98vw)")
        .set("max-height", "min(85vh, 56rem)")
        .set("overflow", "hidden")
        .set("display", "flex")
        .set("flex-direction", "column");

    panel.add(body, footer);
    add(backdrop, panel);
  }

  public void close() {
    if (onClose != null) {
      onClose.run();
    }
  }

  private Div buildQuestionsSection(Evaluation evaluation) {
    var title = new H4("Preguntas generadas");
    title.getStyle().set("margin", "0");

    var container = new Div();
    container.addClassName("evaluation-dialog-questions");
    container.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.75rem");

    var json = evaluation.getQuestionsJson();
    if (json == null || json.isBlank()) {
      container.add(new Span("Aún no se generaron preguntas."));
    } else {
      try {
        var questions = questionGenerationService.fromJson(json);
        for (int i = 0; i < questions.size(); i++) {
          var q = questions.get(i);
          var item = new Div(new Span((i + 1) + ". "), new Span(q.questionText()));
          item.addClassName("evaluation-dialog-question-item");
          item.getStyle()
              .set("padding", "0.85rem 1rem")
              .set("border", "1px solid var(--chat-border-subtle, rgba(255,255,255,0.08))")
              .set("border-radius", "0.75rem")
              .set("background", "var(--chat-surface-base, rgba(255,255,255,0.02))")
              .set("line-height", "1.5");
          container.add(item);
        }
      } catch (Exception e) {
        container.add(new Span("Error al leer las preguntas."));
      }
    }

    return buildSection(title, container);
  }

  private Div buildAnswersSection(Evaluation evaluation) {
    var title = new H4("Respuestas del estudiante");
    title.getStyle().set("margin", "0");

    var container = new Div();
    container.addClassName("evaluation-dialog-answers");
    container.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.85rem");

    var json = evaluation.getAnswersJson();
    if (json == null || json.isBlank()) {
      container.add(new Span("Aún no se registraron respuestas."));
    } else {
      if (!renderAnswersAsCards(json, container)) {
        var jsonBlock = new Pre(prettyPrintJson(json));
        jsonBlock.getStyle()
            .set("margin", "0")
            .set("padding", "1rem")
            .set("overflow", "auto")
            .set("max-height", "18rem")
            .set("border-radius", "0.75rem")
            .set("border", "1px solid var(--chat-border-subtle, rgba(255,255,255,0.08))")
            .set("background", "rgba(0, 0, 0, 0.18)")
            .set("font-size", "0.92rem")
            .set("line-height", "1.5")
            .set("white-space", "pre-wrap")
            .set("word-break", "break-word");
        container.add(jsonBlock);
      }
    }

    var section = buildSection(title, container);
    section.setVisible(json != null && !json.isBlank());
    return section;
  }

  private Div buildReportSection(Evaluation evaluation) {
    var title = new H4("Reporte evaluativo");
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
      wrapper.getStyle().set("max-height", "24rem").set("overflow", "auto").set("padding-right", "0.25rem");
      wrapper.getElement().appendChild(markdownEl);
      container.add(wrapper);
    }

    var section = buildSection(title, container);
    section.setVisible(markdown != null && !markdown.isBlank());
    return section;
  }

  private Div buildSection(H4 title, Component content) {
    var section = new Div(title, content);
    section.getStyle()
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("gap", "0.85rem")
        .set("padding", "1rem")
        .set("border", "1px solid var(--chat-border-visible)")
        .set("border-radius", "0.9rem")
        .set("background", "rgba(255, 255, 255, 0.02)");
    return section;
  }

  private String prettyPrintJson(String json) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(OBJECT_MAPPER.readTree(json));
    } catch (Exception e) {
      return json;
    }
  }

  private boolean renderAnswersAsCards(String json, Div container) {
    try {
      var root = OBJECT_MAPPER.readTree(json);
      var answers = extractAnswerEntries(root);
      if (answers.isEmpty()) {
        return false;
      }

      for (int i = 0; i < answers.size(); i++) {
        var answerNode = answers.get(i);
        var card = new Div();
        card.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "0.65rem")
            .set("padding", "1rem")
            .set("border", "1px solid var(--chat-border-subtle, rgba(255,255,255,0.08))")
            .set("border-radius", "0.8rem")
            .set("background", "var(--chat-surface-base, rgba(255,255,255,0.02))");

        var badge = new Span("Respuesta " + (i + 1));
        badge.getElement().getThemeList().add("badge contrast");

        var questionText = answerNode.path("questionText").asText("").trim();
        var answerText = answerNode.path("answer").asText("").trim();
        var questionKey = answerNode.path("questionKey").asText("").trim();

        card.add(badge);
        card.add(buildAnswerField("Pregunta", questionText.isBlank() ? "Sin pregunta disponible." : questionText));
        card.add(buildAnswerField("Respuesta del estudiante", answerText.isBlank() ? "Sin respuesta registrada." : answerText));

        if (!questionKey.isBlank()) {
          var key = new Span("ID: " + questionKey);
          key.getStyle()
              .set("font-size", "0.82rem")
              .set("color", "var(--chat-text-secondary)");
          card.add(key);
        }

        container.add(card);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private java.util.List<JsonNode> extractAnswerEntries(JsonNode node) {
    var entries = new java.util.ArrayList<JsonNode>();
    collectAnswerEntries(node, entries);
    return entries;
  }

  private void collectAnswerEntries(JsonNode node, java.util.List<JsonNode> entries) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      for (var child : node) {
        collectAnswerEntries(child, entries);
      }
      return;
    }
    if (node.isObject() && (node.has("answer") || node.has("questionText") || node.has("questionKey"))) {
      entries.add(node);
    }
  }

  private Div buildAnswerField(String label, String value) {
    var wrapper = new Div();
    wrapper.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.35rem");

    var labelSpan = new Span(label);
    labelSpan.getStyle()
        .set("font-size", "0.82rem")
        .set("font-weight", "600")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.04em")
        .set("color", "var(--chat-text-secondary)");

    var valueBlock = new Div(new Span(value));
    valueBlock.getStyle()
        .set("padding", "0.85rem 0.95rem")
        .set("border-radius", "0.7rem")
        .set("background", "rgba(0, 0, 0, 0.16)")
        .set("line-height", "1.6")
        .set("white-space", "pre-wrap")
        .set("word-break", "break-word");

    wrapper.add(labelSpan, valueBlock);
    return wrapper;
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
      if (onSave != null) {
        onSave.accept(updated);
      }
      close();
    } catch (Exception e) {
      Notification.show("Error al guardar: " + e.getMessage());
    }
  }
}
