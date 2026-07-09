package com.wornux.ui.training_activity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.services.training_activity.instruction_review.InstructionReviewUnavailableException;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentTutorService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import com.wornux.ui.conversation.MessagesList;
import com.wornux.ui.conversation.MessageItem;
import com.wornux.ui.css.UiCss;

public class TrainingActivityDialog extends Div {

    private static final ObjectMapper REPORT_OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy · HH:mm", Locale.of("es", "DO"));

    private static final Pattern QUESTION_LABEL_PATTERN = Pattern.compile(
            "^pregunta(?:\\s+(\\d+))?\\s*:?(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STUDENT_ANSWER_LABEL_PATTERN = Pattern.compile(
            "^respuesta\\s+del\\s+estudiante\\s*:?(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSCRIPT_HEADING_PATTERN = Pattern.compile(
            "(?im)^\\s*#{0,6}\\s*(?:Transcripción|Transcripcion|Transcript|Evidencia disponible|Available evidence)\\s*$");
    private static final Pattern INTERNAL_REPORT_METADATA_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:\"?(?:type|reason|metadata|answerQuality|answer_quality|evidenceStatus|evidence_status|coverageStatus|coverage_status|pedagogicalMove|pedagogical_move|shouldContinue|should_continue|coveredInstructionAspects|covered_instruction_aspects|missingInstructionAspects|missing_instruction_aspects|unproductivePatternDetected|unproductive_pattern_detected)\"?\\s*[:=].*|(?:TutorDecisionType|COMPLETE_SUCCESS|COMPLETE_INSUFFICIENT_EVIDENCE|FOLLOW_UP|ASK_FOR_CLARITY)\\s*)$");

    private final transient TrainingActivity original;
    private final transient TrainingActivityService trainingActivityService;
    private final transient SafeBrowserModeService safeBrowserModeService;
    private final transient SafeBrowserAssignmentStateBus assignmentStateBus;
    private final transient Consumer<TrainingActivity> onSave;
    private final transient Runnable onClose;
    private transient TrainingActivity activitySnapshot;

    private final Div panel = new Div();
    private final TextField titleField;
    private final InstructionLinterEditor instructionField;
    private final Checkbox safeBrowserField;
    private final Button saveButton = new Button("Guardar cambios", _ -> onSaveClick());
    private AutoCloseable assignmentStateSubscription;
    private boolean activityMode = true;
    private transient InstructionReviewSnapshotDto displayedReviewSnapshot;
    private String displayedReviewTitle = "";
    private String displayedReviewInstructions = "";

    public TrainingActivityDialog(
            TrainingActivity activity,
            TrainingActivityService trainingActivityService,
            SafeBrowserModeService safeBrowserModeService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            Consumer<TrainingActivity> onSave,
            Runnable onClose) {
        this.original = activity;
        this.trainingActivityService = trainingActivityService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.assignmentStateBus = assignmentStateBus;
        this.onSave = onSave;
        this.onClose = onClose;
        this.activitySnapshot = activity;

        UiCss.TRAINING_ACTIVITY_OVERLAY.addTo(this);

        var backdrop = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_BACKDROP.addTo(backdrop);
        backdrop.addClickListener(_ -> close());

        UiCss.TRAINING_ACTIVITY_OVERLAY_PANEL.addTo(panel);

        titleField = new TextField("Título");
        titleField.setWidthFull();
        titleField.setValue(activity.getTitle());
        titleField.addValueChangeListener(_ -> invalidateDisplayedReviewConfirmation());

        instructionField = new InstructionLinterEditor();
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        instructionField.getElement().getStyle().set("margin-bottom", "var(--vaadin-padding-m, 1rem)");
        instructionField.setValue(activity.getInstructions());
        instructionField.addValueChangeListener(_ -> invalidateDisplayedReviewConfirmation());
        if (activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT) {
            showInstructionReview(trainingActivityService.getInstructionReviewSnapshot(activity.getId()));
        }

        safeBrowserField = new Checkbox("Safe Browser Mode");
        safeBrowserField.setHelperText("Disponible solo antes de lanzar la actividad.");
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT);

        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setDisableOnClick(true);

        add(backdrop, panel);
        renderActivityMode();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        subscribeToAssignmentStateChanges(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentStateChanges();
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentStateChanges(UI ui) {
        unsubscribeFromAssignmentStateChanges();
        assignmentStateSubscription = assignmentStateBus.subscribe(notification -> {
            if (!notification.affectsTrainingActivity(original.getId())) {
                return;
            }
            ui.access(() -> {
                if (!isAttached() || !activityMode) {
                    return;
                }
                refreshActivitySnapshot();
                renderActivityMode();
            });
        });
    }

    private void unsubscribeFromAssignmentStateChanges() {
        if (assignmentStateSubscription == null) {
            return;
        }
        try {
            assignmentStateSubscription.close();
        }
        catch (Exception exception) {
            // Subscription removal has no checked failure path.
        }
        assignmentStateSubscription = null;
    }

    private void renderActivityMode() {
        var activity = activitySnapshot;
        activityMode = true;
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--report");
        panel.addClassName("training-activity-overlay-panel--activity");
        var draft = activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT;

        var title = new H3("Actividad: %s".formatted(activity.getTitle()));
        title.getStyle().set("margin", "0");

        titleField.setValue(activity.getTitle());
        titleField.setReadOnly(!draft);
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(draft);

        Component instructionsComponent;
        if (draft) {
            instructionField.setValue(activity.getInstructions());
            showInstructionReview(trainingActivityService.getInstructionReviewSnapshot(activity.getId()));
            instructionsComponent = instructionField;
        }
        else {
            clearDisplayedReviewConfirmation();
            instructionField.resetReviewState();
            var readonlyInstructions = new Div();
            readonlyInstructions.setText(activity.getInstructions());
            readonlyInstructions.addClassName("training-activity-readonly-instructions");
            instructionsComponent = readonlyInstructions;
        }

        var body = new VerticalLayout(title, titleField, instructionsComponent, safeBrowserField, incidentSummary(), assignmentsGrid());
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        body.addClassName("training-activity-overlay-body");

        var closeActivityButton = new Button("Cerrar actividad", _ -> onCloseActivityClick());
        closeActivityButton.addThemeVariants(ButtonVariant.ERROR);
        closeActivityButton.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED);

        var closeButton = new Button("Cerrar", _ -> close());

        var footer = draft
                ? new HorizontalLayout(closeActivityButton, saveButton, closeButton)
                : new HorizontalLayout(closeActivityButton, closeButton);
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

        grid.setHeight("min(26rem, 38vh)");

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
        if (activitySnapshot.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            Notification.show("Solo se pueden editar actividades en borrador");
            return;
        }
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();

        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("El título y las instrucciones son obligatorios");
            return;
        }

        TrainingActivity updated;
        try {
            var currentSnapshot = trainingActivityService.getInstructionReviewSnapshot(original.getId());
            var confirmedReviewHash = confirmedReviewHashForSave(title, instruction, currentSnapshot);
            updated = trainingActivityService.update(original.getId(), new TrainingActivitySaveCommand(
                    title,
                    instruction,
                    safeBrowserField.getValue(),
                    confirmedReviewHash));
        }
        catch (InstructionQualityReviewException exception) {
            if (exception.getReviewSnapshot() != null) {
                showInstructionReview(exception.getReviewSnapshot());
            }
            Notification.show(exception.getMessage());
            return;
        }
        catch (InstructionReviewUnavailableException exception) {
            if (exception.getReviewSnapshot() != null) {
                showInstructionReview(exception.getReviewSnapshot());
            }
            Notification.show(exception.getMessage());
            return;
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
            return;
        }
        finally {
            saveButton.setEnabled(true);
        }
        var snapshot = trainingActivityService.getInstructionReviewSnapshot(updated.getId());
        activitySnapshot = updated;
        showInstructionReview(snapshot);
        Notification.show(saveMessage(snapshot));

        if (onSave != null) {
            onSave.accept(updated);
        }

        if (snapshot.qualityStatus() == InstructionQualityStatus.GOOD) {
            close();
        }
    }

    private boolean requiresVisibleReviewConfirmation(InstructionReviewSnapshotDto snapshot) {
        return snapshot != null && snapshot.requiresVisibleReviewConfirmation();
    }

    private String confirmedReviewHashForSave(
            String title,
            String instruction,
            InstructionReviewSnapshotDto currentSnapshot) {
        var confirmationSnapshot = confirmationSnapshotForSave(title, instruction, currentSnapshot);
        return requiresVisibleReviewConfirmation(confirmationSnapshot)
                ? confirmationSnapshot.reviewHash()
                : "";
    }

    private InstructionReviewSnapshotDto confirmationSnapshotForSave(
            String title,
            String instruction,
            InstructionReviewSnapshotDto currentSnapshot) {
        if (requiresVisibleReviewConfirmation(currentSnapshot) && matchesActivitySnapshot(title, instruction)) {
            return currentSnapshot;
        }
        if (matchesDisplayedReviewConfirmation(title, instruction)) {
            return displayedReviewSnapshot;
        }
        return null;
    }

    private boolean matchesActivitySnapshot(String title, String instruction) {
        return activitySnapshot != null
                && title.equals(activitySnapshot.getTitle().trim())
                && instruction.equals(activitySnapshot.getInstructions().trim());
    }

    private boolean matchesDisplayedReviewConfirmation(String title, String instruction) {
        return displayedReviewSnapshot != null
                && title.equals(displayedReviewTitle)
                && instruction.equals(displayedReviewInstructions);
    }

    private void invalidateDisplayedReviewConfirmation() {
        if (!matchesDisplayedReviewConfirmation(titleField.getValue().trim(), instructionField.getValue().trim())) {
            clearDisplayedReviewConfirmation();
        }
    }

    private void rememberDisplayedReviewConfirmation(InstructionReviewSnapshotDto reviewSnapshot) {
        if (!requiresVisibleReviewConfirmation(reviewSnapshot)) {
            clearDisplayedReviewConfirmation();
            return;
        }
        displayedReviewSnapshot = reviewSnapshot;
        displayedReviewTitle = titleField.getValue().trim();
        displayedReviewInstructions = instructionField.getValue().trim();
    }

    private void clearDisplayedReviewConfirmation() {
        displayedReviewSnapshot = null;
        displayedReviewTitle = "";
        displayedReviewInstructions = "";
    }

    private String saveMessage(InstructionReviewSnapshotDto snapshot) {
        if (snapshot.reviewStatus() == InstructionReviewStatus.COMPLETED_FROM_CACHE) {
            return "Actividad guardada con una revisión válida reutilizada para estas mismas instrucciones.";
        }
        return "Actividad guardada";
    }

    private void onCloseActivityClick() {
        try {
            var updated = trainingActivityService.close(original.getId());
            activitySnapshot = updated;
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

    public void showInstructionReview(InstructionReviewSnapshotDto reviewSnapshot) {
        rememberDisplayedReviewConfirmation(reviewSnapshot);
        instructionField.setReviewSnapshot(reviewSnapshot);
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
        activityMode = false;
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

        if (usesLegacyPromptVersion(assignment)) {
            var legacyNote = new Span("Generado con una versión anterior del tutor; interpretarlo con cautela.");
            legacyNote.addClassName("training-activity-report-legacy-note");
            meta.add(legacyNote);
        }

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
        var report = sanitizeTeacherReport(assignment.getFinalReport());
        var hasTranscriptHeading = hasTranscriptEvidenceHeading(report);
        var transcript = extractTranscriptEvidence(report);
        var questions = parseQuestions(hasTranscriptHeading ? transcript : report);
        if (questions.isEmpty()) {
            return fallbackReport(report, assignment);
        }

        var reportContent = new Div();
        reportContent.addClassName("training-activity-report-content");

        var narrative = hasTranscriptHeading ? extractReportNarrative(report) : "";
        if (narrative.isBlank()) {
            reportContent.add(reportTitle(extractReportTitle(report)));
        }
        else {
            reportContent.add(markdownReport(narrative, assignment));
        }
        reportContent.add(transcriptSection(questions));
        return reportContent;
    }

    private boolean hasTranscriptEvidenceHeading(String report) {
        return report != null && !report.isBlank() && TRANSCRIPT_HEADING_PATTERN.matcher(report).find();
    }

    private String extractTranscriptEvidence(String report) {
        if (report == null || report.isBlank()) {
            return "";
        }

        var matcher = TRANSCRIPT_HEADING_PATTERN.matcher(report);
        if (!matcher.find()) {
            return "";
        }
        return report.substring(matcher.end()).trim();
    }

    private Component fallbackReport(String report, TrainingActivityAssignment assignment) {
        var reportContent = new Div(markdownReport(report, assignment));
        reportContent.addClassName("training-activity-report-content");

        return reportContent;
    }

    private Component markdownReport(String report, TrainingActivityAssignment assignment) {
        var reportList = new MessagesList();
        reportList.setWidthFull();

        var createdAt = assignment.getSubmittedAt() != null
                ? assignment.getSubmittedAt()
                : Instant.now();

        var reportItem = new MessageItem(
                report,
                createdAt,
                "Tutor Socrático",
                MessageItem.Variant.ASSISTANT,
                false,
                false);

        reportList.setItems(List.of(reportItem));
        return reportList;
    }

    private boolean usesLegacyPromptVersion(TrainingActivityAssignment assignment) {
        return assignment == null
                || assignment.getTutorPromptVersion() == null
                || !TrainingAssignmentTutorService.currentPromptVersionValue().equals(assignment.getTutorPromptVersion());
    }

    private String sanitizeTeacherReport(String report) {
        if (report == null || report.isBlank()) {
            return "";
        }
        return INTERNAL_REPORT_METADATA_PATTERN.matcher(report).replaceAll("").trim();
    }

    private List<ReportQuestion> parseQuestions(String report) {
        if (report == null || report.isBlank()) {
            return List.of();
        }

        var questions = new ArrayList<ReportQuestion>();
        var prompt = new ArrayList<String>();
        var answer = new ArrayList<String>();
        Integer currentNumber = null;
        var nextImplicitNumber = 1;
        var readingAnswer = false;
        var inCodeFence = false;

        for (var rawLine : report.split("\\R", -1)) {
            if (!inCodeFence) {
                var normalizedLine = normalizeLabelLine(rawLine);
                var questionMatcher = QUESTION_LABEL_PATTERN.matcher(normalizedLine);
                if (questionMatcher.matches()) {
                    addReportQuestion(questions, currentNumber, prompt, answer);
                    currentNumber = questionMatcher.group(1) == null
                            ? nextImplicitNumber
                            : Integer.parseInt(questionMatcher.group(1));
                    nextImplicitNumber = Math.max(nextImplicitNumber, currentNumber + 1);
                    prompt.clear();
                    answer.clear();
                    readingAnswer = false;

                    var inlineQuestion = questionMatcher.group(2) == null ? "" : questionMatcher.group(2).trim();
                    if (!inlineQuestion.isBlank()) {
                        prompt.add(inlineQuestion);
                        inCodeFence = toggleCodeFence(inlineQuestion, inCodeFence);
                    }
                    continue;
                }

                if (currentNumber != null) {
                    var answerMatcher = STUDENT_ANSWER_LABEL_PATTERN.matcher(normalizedLine);
                    if (answerMatcher.matches()) {
                        readingAnswer = true;
                        var inlineAnswer = answerMatcher.group(1) == null ? "" : answerMatcher.group(1).trim();
                        if (!inlineAnswer.isBlank()) {
                            answer.add(inlineAnswer);
                            inCodeFence = toggleCodeFence(inlineAnswer, inCodeFence);
                        }
                        continue;
                    }
                }
            }

            if (currentNumber == null) {
                continue;
            }

            if (readingAnswer) {
                answer.add(rawLine);
            }
            else {
                prompt.add(rawLine);
            }
            inCodeFence = toggleCodeFence(rawLine, inCodeFence);
        }

        addReportQuestion(questions, currentNumber, prompt, answer);
        return questions;
    }

    private String normalizeLabelLine(String rawLine) {
        return rawLine.trim()
                .replace("**", "")
                .replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^[-*]\\s*", "")
                .trim();
    }

    private boolean toggleCodeFence(String line, boolean inCodeFence) {
        var markerCount = line.split("```", -1).length - 1;
        return markerCount % 2 == 0 ? inCodeFence : !inCodeFence;
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
            return "Reporte de evaluación";
        }

        var preTranscript = report.split("(?im)^\\s*#{0,6}\\s*(?:Transcripción|Transcripcion|Transcript)\\s*$", 2)[0];
        for (var rawLine : preTranscript.split("\\R")) {
            var line = stripMarkdownDecorators(rawLine.trim());
            if (line.isBlank()
                    || line.regionMatches(true, 0, "Activity:", 0, "Activity:".length())
                    || line.regionMatches(true, 0, "Actividad:", 0, "Actividad:".length())) {
                continue;
            }
            return line;
        }

        return "Reporte de evaluación";
    }

    private String extractReportNarrative(String report) {
        if (report == null || report.isBlank()) {
            return "";
        }

        var narrativeLines = new ArrayList<String>();
        var inCodeFence = false;
        for (var rawLine : report.split("\\R", -1)) {
            if (!inCodeFence) {
                if (TRANSCRIPT_HEADING_PATTERN.matcher(rawLine).matches()) {
                    break;
                }
            }
            narrativeLines.add(rawLine);
            inCodeFence = toggleCodeFence(rawLine, inCodeFence);
        }

        return removeTrailingTranscriptHeading(normalizeReportBlock(narrativeLines));
    }

    private String removeTrailingTranscriptHeading(String narrative) {
        if (narrative.isBlank()) {
            return "";
        }
        var lines = new ArrayList<>(List.of(narrative.split("\\R", -1)));
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        if (!lines.isEmpty() && isDuplicateTranscriptHeading(lines.getLast())) {
            lines.removeLast();
        }
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return String.join("\n", lines).trim();
    }

    private boolean isDuplicateTranscriptHeading(String line) {
        var normalized = stripMarkdownDecorators(line).toLowerCase(Locale.ROOT);
        return normalized.equals("transcripción")
                || normalized.equals("transcripcion")
                || normalized.equals("transcript")
                || normalized.equals("evidencia disponible")
                || normalized.equals("available evidence");
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

        var section = new Div(title, reportCards(questions));
        section.addClassName("training-activity-transcript-section");
        return section;
    }

    private Component reportCards(List<ReportQuestion> questions) {
        var cards = new TrainingActivityReportCards();
        cards.setItemsJson(writeReportQuestions(questions));
        return cards;
    }

    private void refreshActivitySnapshot() {
        activitySnapshot = trainingActivityService.get(original.getId());
    }

    private String writeReportQuestions(List<ReportQuestion> questions) {
        try {
            return REPORT_OBJECT_MAPPER.writeValueAsString(questions == null ? List.of() : questions);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize report questions", exception);
        }
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
        unsubscribeFromAssignmentStateChanges();
        if (onClose != null) {
            onClose.run();
        }
    }

    private record ReportQuestion(int number, String tutorPrompt, String studentAnswer) {
    }
}
