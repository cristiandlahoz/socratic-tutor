package com.wornux.ui.training_activity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.ui.conversation.CodeMessageList;
import com.wornux.ui.conversation.CodeMessageListItem;
import com.wornux.ui.css.UiCss;

public class TrainingActivityDialog extends Div {

    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy · HH:mm", Locale.of("es", "DO"));

    private static final Pattern QUESTION_HEADING_PATTERN = Pattern.compile(
            "^\\s*(?:#{1,6}\\s*)?\\**\\s*Pregunta\\s+(\\d+)\\s*\\**\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STUDENT_ANSWER_LABEL_PATTERN = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?\\**\\s*Respuesta del estudiante\\s*:\\s*\\**\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final transient TrainingActivity original;
    private final transient TrainingActivityService trainingActivityService;
    private final transient SafeBrowserModeService safeBrowserModeService;
    private final transient Consumer<TrainingActivity> onSave;
    private final transient Runnable onClose;

    private final Div panel = new Div();
    private final TextField titleField;
    private final TextArea instructionField;
    private final Checkbox safeBrowserField;

    public TrainingActivityDialog(
            TrainingActivity activity,
            TrainingActivityService trainingActivityService,
            SafeBrowserModeService safeBrowserModeService,
            Consumer<TrainingActivity> onSave,
            Runnable onClose) {
        this.original = activity;
        this.trainingActivityService = trainingActivityService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.onSave = onSave;
        this.onClose = onClose;

        UiCss.TRAINING_ACTIVITY_OVERLAY.addTo(this);

        var backdrop = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_BACKDROP.addTo(backdrop);
        backdrop.addClickListener(_ -> close());

        UiCss.TRAINING_ACTIVITY_OVERLAY_PANEL.addTo(panel);

        titleField = new TextField("Título");
        titleField.setWidthFull();
        titleField.setValue(activity.getTitle());

        instructionField = new TextArea("Instrucciones");
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        instructionField.setMaxHeight("14rem");
        instructionField.setValue(activity.getInstructions());

        safeBrowserField = new Checkbox("Safe Browser Mode");
        safeBrowserField.setHelperText("Disponible solo antes de lanzar la actividad.");
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT);

        add(backdrop, panel);
        renderActivityMode();
    }

    private void renderActivityMode() {
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--report");
        panel.addClassName("training-activity-overlay-panel--activity");

        var title = new H3("Actividad: %s".formatted(original.getTitle()));
        title.getStyle().set("margin", "0");

        titleField.setValue(original.getTitle());
        instructionField.setValue(original.getInstructions());
        safeBrowserField.setValue(original.isSafeBrowserEnabled());

        var body = new VerticalLayout(title, titleField, instructionField, safeBrowserField, incidentSummary(), assignmentsGrid());
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        body.addClassName("training-activity-overlay-body");

        var saveButton = new Button("Guardar cambios", _ -> onSaveClick());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);

        var closeActivityButton = new Button("Cerrar actividad", _ -> onCloseActivityClick());
        closeActivityButton.addThemeVariants(ButtonVariant.ERROR);
        closeActivityButton.setEnabled(original.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED);

        var closeButton = new Button("Cerrar", _ -> close());

        var footer = new HorizontalLayout(closeActivityButton, saveButton, closeButton);
        UiCss.TRAINING_ACTIVITY_OVERLAY_FOOTER.addTo(footer);
        footer.setPadding(false);
        footer.setSpacing(false);

        panel.add(body, footer);
    }

    private Grid<TrainingActivityAssignment> assignmentsGrid() {
        var grid = new Grid<>(TrainingActivityAssignment.class, false);

        grid.addColumn(this::studentName)
                .setHeader("Estudiante")
                .setWidth("0")
                .setFlexGrow(1)
                .setAutoWidth(false);

        grid.addColumn(this::assignmentStatusLabel)
                .setHeader("Estado")
                .setWidth("7rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(this::safeBrowserStatusLabel)
                .setHeader("Safe Browser")
                .setWidth("10rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(new ComponentRenderer<>(this::reportButton))
                .setHeader("Reporte")
                .setWidth("6.5rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(new ComponentRenderer<>(this::unlockButton))
                .setHeader("Incidente")
                .setWidth("8rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.setEmptyStateText("No hay asignaciones todavía.");
        grid.setItems(trainingActivityService.listAssignments(original.getId()));
        grid.setWidthFull();

        // Más alto para clases reales con más estudiantes.
        grid.setHeight("min(34rem, 50vh)");

        return grid;
    }

    private String assignmentStatusLabel(TrainingActivityAssignment assignment) {
        return switch (assignment.getStatus()) {
            case ASSIGNED -> "Asignada";
            case STARTED -> "Iniciada";
            case SUBMITTED -> "Enviada";
            case SKIPPED -> "Omitida";
            case EXPIRED -> "Vencida";
            case EXCUSED -> "Eximida";
        };
    }

    private String safeBrowserStatusLabel(TrainingActivityAssignment assignment) {
        if (!assignment.getTrainingActivity().isSafeBrowserEnabled()) {
            return "No requerido";
        }
        if (assignment.isSafeBrowserLocked()) {
            return "Bloqueada";
        }
        if (assignment.isSafeBrowserSessionActive()) {
            return "Activa";
        }
        return "Pendiente";
    }

    private void onSaveClick() {
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();

        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("El título y las instrucciones son obligatorios");
            return;
        }

        var updated = trainingActivityService.update(original.getId(), title, instruction, safeBrowserField.getValue());
        Notification.show("Actividad formativa actualizada");

        if (onSave != null) {
            onSave.accept(updated);
        }

        close();
    }

    private void onCloseActivityClick() {
        try {
            var updated = trainingActivityService.close(original.getId());
            Notification.show("Actividad formativa cerrada");
            if (onSave != null) {
                onSave.accept(updated);
            }
            close();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private String studentName(TrainingActivityAssignment assignment) {
        var account = assignment.getGroupClassMember().getTenantAccount().getAccount();
        return "%s %s".formatted(account.getFirstName(), account.getLastName()).trim();
    }

    private Button reportButton(TrainingActivityAssignment assignment) {
        var button = new Button("Ver", _ -> renderReportMode(assignment));
        button.setEnabled(assignment.getFinalReport() != null && !assignment.getFinalReport().isBlank());
        button.setWidthFull();
        return button;
    }

    private Button unlockButton(TrainingActivityAssignment assignment) {
        var button = new Button("Desbloquear", _ -> unlockAssignment(assignment));
        button.setEnabled(assignment.isSafeBrowserLocked());
        button.setWidthFull();
        return button;
    }

    private void unlockAssignment(TrainingActivityAssignment assignment) {
        try {
            safeBrowserModeService.unlockAssignment(assignment.getId());
            Notification.show("Asignación desbloqueada");
            renderActivityMode();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private Component incidentSummary() {
        var openAlerts = safeBrowserModeService.listOpenAlerts(original.getId());
        if (openAlerts.isEmpty()) {
            var empty = new Span("Sin alertas Safe Browser abiertas.");
            empty.addClassName("training-activity-safe-browser-empty");
            return empty;
        }
        var alert = openAlerts.getFirst();
        var text = new Span("Alerta Safe Browser: %d incidente(s), último evento %s"
                .formatted(alert.getIncidentCount(), alert.getLastEventAt()));
        text.addClassName("training-activity-safe-browser-alert");
        return text;
    }

    private void renderReportMode(TrainingActivityAssignment assignment) {
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--activity");
        panel.addClassName("training-activity-overlay-panel--report");

        panel.add(reportHeader(assignment), reportBody(assignment), reportFooter());
    }

    private Component reportHeader(TrainingActivityAssignment assignment) {
        var title = new H3("Reporte de evaluación");
        title.getStyle().set("margin", "0");

        var student = new Paragraph(studentName(assignment));
        student.getStyle().set("margin", "0");

        var activity = new Span("Actividad: %s".formatted(assignment.getTrainingActivity().getTitle()));
        var status = createReportBadge(assignmentStatusLabel(assignment).toUpperCase(Locale.ROOT));

        var meta = new Div(activity, status);
        meta.addClassName("training-activity-report-meta");

        var submittedAt = assignment.getSubmittedAt();
        if (submittedAt != null) {
            var submitted = new Span(submittedAt.atZone(ZoneId.systemDefault()).format(REPORT_DATE_FORMATTER));
            meta.add(submitted);
        }

        var header = new Div(title, student, meta);
        header.addClassName("training-activity-report-header");

        return header;
    }

    private Component reportBody(TrainingActivityAssignment assignment) {
        var report = assignment.getFinalReport();
        var questions = parseQuestions(report);
        if (questions.isEmpty()) {
            return fallbackReport(report, assignment);
        }

        var reportContent = new Div();
        reportContent.addClassName("training-activity-report-content");

        reportContent.add(
                reportTitle(extractReportTitle(report)),
                transcriptSection(questions));
        return reportContent;
    }

    private Component fallbackReport(String report, TrainingActivityAssignment assignment) {
        var reportList = new CodeMessageList();
        reportList.setMarkdown(true);
        reportList.setWidthFull();

        var createdAt = assignment.getSubmittedAt() != null
                ? assignment.getSubmittedAt()
                : Instant.now();

        var reportItem = new CodeMessageListItem(
                report,
                createdAt,
                "Tutor Socrático");

        reportItem.addClass(UiCss.CONVERSATION_MESSAGE_ASSISTANT);
        reportList.setItems(List.of(reportItem));

        var reportContent = new Div(reportList);
        reportContent.addClassName("training-activity-report-content");

        return reportContent;
    }

    private List<ReportQuestion> parseQuestions(String report) {
        if (report == null || report.isBlank()) {
            return List.of();
        }

        var questions = new ArrayList<ReportQuestion>();
        var prompt = new ArrayList<String>();
        var answer = new ArrayList<String>();
        Integer currentNumber = null;
        var readingAnswer = false;

        for (var rawLine : report.split("\\R")) {
            var questionMatcher = QUESTION_HEADING_PATTERN.matcher(rawLine);
            if (questionMatcher.matches()) {
                addReportQuestion(questions, currentNumber, prompt, answer);
                currentNumber = Integer.parseInt(questionMatcher.group(1));
                prompt.clear();
                answer.clear();
                readingAnswer = false;
                continue;
            }

            if (currentNumber == null) {
                continue;
            }

            if (STUDENT_ANSWER_LABEL_PATTERN.matcher(rawLine).matches()) {
                readingAnswer = true;
                continue;
            }

            if (readingAnswer) {
                answer.add(rawLine);
            }
            else {
                prompt.add(rawLine);
            }
        }

        addReportQuestion(questions, currentNumber, prompt, answer);
        return questions;
    }

    private void addReportQuestion(
            List<ReportQuestion> questions,
            Integer number,
            List<String> promptLines,
            List<String> answerLines) {
        if (number == null) {
            return;
        }

        var prompt = normalizeReportBlock(promptLines);
        var answer = normalizeReportBlock(answerLines);
        if (!prompt.isBlank() && !answer.isBlank()) {
            questions.add(new ReportQuestion(number, prompt, answer));
        }
    }

    private String normalizeReportBlock(List<String> lines) {
        var start = 0;
        var end = lines.size();

        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }

        return String.join("\n", lines.subList(start, end)).trim();
    }

    private String extractReportTitle(String report) {
        if (report == null || report.isBlank()) {
            return "Evaluation report";
        }

        var preTranscript = report.split("(?im)^\\s*Transcript\\s*$", 2)[0];
        for (var rawLine : preTranscript.split("\\R")) {
            var line = stripMarkdownDecorators(rawLine.trim());
            if (line.isBlank()
                    || line.regionMatches(true, 0, "Activity:", 0, "Activity:".length())) {
                continue;
            }
            return line;
        }

        return "Evaluation report";
    }

    private String stripMarkdownDecorators(String text) {
        return text.replaceFirst("^#{1,6}\\s*", "")
                .replaceAll("^\\*+|\\*+$", "")
                .trim();
    }

    private Component reportTitle(String text) {
        var title = new H4(text);
        title.addClassName("training-activity-report-title");
        return title;
    }

    private Component transcriptSection(List<ReportQuestion> questions) {
        var title = new H4("Transcripción");
        title.addClassName("training-activity-transcript-title");

        var section = new Div(title);
        section.addClassName("training-activity-transcript-section");
        questions.stream()
                .map(this::questionConversationCard)
                .forEach(section::add);
        return section;
    }

    private Component questionConversationCard(ReportQuestion question) {
        var title = new Span("Pregunta %d".formatted(question.number()));
        title.addClassName("training-activity-conversation-title");

        var badge = new Span("RESPONDIDA");
        badge.addClassName("training-activity-conversation-badge");

        var header = new Div(title, badge);
        header.addClassName("training-activity-conversation-card-header");

        var body = new Div(
                conversationMessage("Tutor Socrático", question.tutorPrompt(), true),
                conversationMessage("Estudiante", question.studentAnswer(), false));
        body.addClassName("training-activity-conversation-body");

        var card = new Div(header, body);
        card.addClassName("training-activity-conversation-card");
        return card;
    }

    private Component conversationMessage(String author, String text, boolean tutor) {
        var authorLabel = new Span(author);
        authorLabel.addClassName("training-activity-conversation-author");

        var message = new Paragraph(text);
        message.addClassName("training-activity-conversation-message");
        message.addClassName(tutor
                ? "training-activity-conversation-message--tutor"
                : "training-activity-conversation-message--student");

        var row = new Div(authorLabel, message);
        row.addClassName("training-activity-conversation-row");
        row.addClassName(tutor
                ? "training-activity-conversation-row--tutor"
                : "training-activity-conversation-row--student");
        return row;
    }

    private Span createReportBadge(String text) {
        var badge = new Span(text);
        badge.addClassName("training-activity-report-badge");
        return badge;
    }

    private Component reportFooter() {
        var backButton = new Button("Volver a la actividad", _ -> renderActivityMode());

        var closeButton = new Button("Cerrar", _ -> close());
        closeButton.addThemeVariants(ButtonVariant.PRIMARY);

        var footer = new HorizontalLayout(backButton, closeButton);
        footer.addClassName("training-activity-report-footer");
        footer.setPadding(false);
        footer.setSpacing(false);
        
        return footer;
    }

    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private record ReportQuestion(int number, String tutorPrompt, String studentAnswer) {
    }
}
