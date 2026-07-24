package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.instruction_review.InstructionLintIssueDto;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.ui.conversation.MessageItem;
import com.wornux.ui.conversation.MessagesList;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingActivityDialogTest {

    @Test
    void reportDialogRequiresTheCanonicalProjectionService() {
        assertThat(TrainingActivityDialog.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(TrainingActivityReportProjectionService.class));
    }

    @Test
    void draftDialogUsesTheNamedInstructionReviewSpacingClass() {
        var dialog = dialog(draftActivity(), reviewSnapshot("persisted", InstructionReviewStatus.IDLE, null, false, List.of()));

        assertThat(instructionField(dialog).getClassNames()).contains("training-activity-dialog-instructions");
    }

    @Test
    void confirmedReviewHashForSaveUsesDisplayedSnapshotAfterRejectedSave() {
        var activity = draftActivity();
        var initialSnapshot = reviewSnapshot("persisted", InstructionReviewStatus.IDLE, null, false, List.of());
        var dialog = dialog(activity, initialSnapshot);
        var confirmableSnapshot = reviewSnapshot(
                "displayed-review-hash",
                InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.GOOD,
                true,
                List.of(issue()));

        titleField(dialog).setValue("Nuevo título");
        instructionField(dialog).setValue("Nuevas instrucciones con criterios observables y evidencia concreta.");
        dialog.showInstructionReview(confirmableSnapshot);

        var confirmedReviewHash = (String) ReflectionTestUtils.invokeMethod(
                dialog,
                "confirmedReviewHashForSave",
                titleField(dialog).getValue().trim(),
                instructionField(dialog).getValue().trim(),
                initialSnapshot);

        assertThat(confirmedReviewHash).isEqualTo("displayed-review-hash");
    }

    @Test
    void confirmedReviewHashForSaveClearsDisplayedSnapshotWhenInstructionsChange() {
        var activity = draftActivity();
        var initialSnapshot = reviewSnapshot("persisted", InstructionReviewStatus.IDLE, null, false, List.of());
        var dialog = dialog(activity, initialSnapshot);
        var confirmableSnapshot = reviewSnapshot(
                "displayed-review-hash",
                InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.GOOD,
                true,
                List.of(issue()));

        titleField(dialog).setValue("Nuevo título");
        instructionField(dialog).setValue("Nuevas instrucciones con criterios observables y evidencia concreta.");
        dialog.showInstructionReview(confirmableSnapshot);
        instructionField(dialog).setValue("Nuevas instrucciones modificadas después de la advertencia.");

        var confirmedReviewHash = (String) ReflectionTestUtils.invokeMethod(
                dialog,
                "confirmedReviewHashForSave",
                titleField(dialog).getValue().trim(),
                instructionField(dialog).getValue().trim(),
                initialSnapshot);

        assertThat(confirmedReviewHash).isEmpty();
    }

    @Test
    void editedDraftRequestsDurableReviewBeforeAttemptingUpdate() {
        var activity = draftActivity();
        var initialSnapshot = reviewSnapshot("persisted", InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.NEEDS_IMPROVEMENT, false, List.of());
        var reviewingSnapshot = reviewSnapshot("edited", InstructionReviewStatus.REVIEWING, null, false, List.of());
        var dialog = dialog(activity, initialSnapshot);
        TrainingActivityService trainingActivityService = field(dialog, "trainingActivityService");
        when(trainingActivityService.reviewDraft(any())).thenReturn(reviewingSnapshot);

        titleField(dialog).setValue("Edited title");
        instructionField(dialog).setValue("Edited multiline instructions\nwith observable evaluation criteria.");
        ReflectionTestUtils.invokeMethod(dialog, "onSaveClick");

        verify(trainingActivityService).reviewDraft(any());
        verify(trainingActivityService, never()).update(
                any(), any(com.wornux.services.training_activity.TrainingActivitySaveCommand.class));
        InstructionReviewSnapshotDto displayedReviewSnapshot = field(dialog, "displayedReviewSnapshot");
        assertThat(displayedReviewSnapshot).isEqualTo(reviewingSnapshot);
    }

    @Test
    void reportBodyGroupsCanonicalTurnsWithTutorFirstMarkdownMessages() {
        var activity = activity();
        var assignment = assignment(activity);
        var dialog = dialog(activity);

        var content = (Div) ReflectionTestUtils.invokeMethod(dialog, "reportBody", new TrainingActivityReportProjectionService.ReportProjection(
                assignment,
                com.wornux.data.entities.training_activity.TrainingActivityReportStatus.READY,
                com.wornux.data.entities.training_activity.EvidenceStatus.STRONG_EVIDENCE,
                "La estudiante identifica la idea general de los punteros.",
                List.of(), List.of(), List.of(), List.of(), null,
                List.of(new TrainingActivityReportProjectionService.TurnProjection(
                        1, "¿Qué es un puntero?", "Una variable que guarda una dirección.\n\n```c\nint *pointer;\n```"))));

        var cards = descendants(content)
                .filter(component -> component.getClassNames().contains("training-activity-conversation-card"))
                .toList();
        var reportLists = descendants(content)
                .filter(MessagesList.class::isInstance)
                .map(MessagesList.class::cast)
                .toList();

        assertThat(cards).hasSize(1);
        assertThat(descendants(content).map(component -> component.getElement().getTag()))
                .doesNotContain("training-activity-report-cards");
        assertThat(descendants(cards.getFirst()).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText))
                .contains("Pregunta 1", "RESPONDIDA", "Tutor Socrático", "Estudiante");
        assertThat(reportLists).hasSize(2);
        assertThat(reportLists.getFirst().getItems()).singleElement().satisfies(message -> {
            assertThat(messageText(message)).isEqualTo("¿Qué es un puntero?");
            assertThat(message.getVariant()).isEqualTo("assistant");
        });
        assertThat(reportLists.getLast().getItems()).singleElement().satisfies(message -> {
            assertThat(messageText(message)).isEqualTo("Una variable que guarda una dirección.\n\n```c\nint *pointer;\n```");
            assertThat(message.getVariant()).isEqualTo("user");
        });
    }

    @Test
    void temporaryTutorUnavailabilityIsLabeledAsRetryable() {
        var activity = activity();
        var assignment = assignment(activity);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);

        var statusLabel = (String) ReflectionTestUtils.invokeMethod(
                dialog(activity), "assignmentStatusLabel", assignment);

        assertThat(statusLabel).isEqualTo("Tutor no disponible temporalmente");
    }

    @Test
    void uc009_pendingReportShowsNonblockingStateAndCanonicalPersistedTurns() {
        var activity = activity();
        var assignment = assignment(activity);
        var projection = new TrainingActivityReportProjectionService.ReportProjection(
                assignment,
                com.wornux.data.entities.training_activity.TrainingActivityReportStatus.PENDING,
                null,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(new TrainingActivityReportProjectionService.TurnProjection(
                        2, "¿Qué sucede en este caso?", "La respuesta completa sin truncar.")));

        var content = (Div) ReflectionTestUtils.invokeMethod(dialog(activity), "reportBody", projection);

        assertThat(findDescendant(content, com.vaadin.flow.component.html.Paragraph.class).getText())
                .contains("pendiente de generación");
        var cards = descendants(content)
                .filter(component -> component.getClassNames().contains("training-activity-conversation-card"))
                .toList();
        var transcript = descendants(content)
                .filter(MessagesList.class::isInstance)
                .map(MessagesList.class::cast)
                .toList();
        assertThat(cards).hasSize(1);
        assertThat(transcript).hasSize(2);
        assertThat(transcript.getFirst().getItems()).extracting(TrainingActivityDialogTest::messageText)
                .containsExactly("¿Qué sucede en este caso?");
        assertThat(transcript.getLast().getItems()).extracting(TrainingActivityDialogTest::messageText)
                .containsExactly("La respuesta completa sin truncar.");
    }

    @Test
    void uc009_reportRefreshesWhenBackgroundGenerationBecomesReady() {
        var activity = activity();
        var assignment = assignment(activity);
        var reportProjectionService = mock(TrainingActivityReportProjectionService.class);
        var pending = projection(assignment, com.wornux.data.entities.training_activity.TrainingActivityReportStatus.PENDING);
        var ready = projection(assignment, com.wornux.data.entities.training_activity.TrainingActivityReportStatus.READY);
        when(reportProjectionService.getForCurrentReviewer(assignment.getId())).thenReturn(ready);
        var dialog = dialog(activity, reportProjectionService);

        ReflectionTestUtils.invokeMethod(dialog, "renderReportMode", pending);
        ReflectionTestUtils.invokeMethod(dialog, "refreshDisplayedReport");

        var panel = (Div) ReflectionTestUtils.getField(dialog, "panel");
        assertThat(descendants(panel)
                .filter(com.vaadin.flow.component.html.H4.class::isInstance)
                .map(component -> ((com.vaadin.flow.component.html.H4) component).getText()))
                .contains("Síntesis diagnóstica");
    }

    @Test
    void uc009_findingReferencesResolveOnlyAgainstAuthoritativeProjectionTurns() {
        var activity = activity();
        var assignment = assignment(activity);
        var projection = new TrainingActivityReportProjectionService.ReportProjection(
                assignment,
                com.wornux.data.entities.training_activity.TrainingActivityReportStatus.READY,
                com.wornux.data.entities.training_activity.EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(new TrainingActivityReportProjectionService.TurnProjection(
                        3, "Pregunta canónica", "Respuesta canónica")));
        var finding = new com.wornux.data.entities.training_activity.TrainingActivityReportFinding(
                "Limitación observada",
                List.of(new com.wornux.data.entities.training_activity.TrainingActivityReportEvidenceReference(3)));

        var text = (String) ReflectionTestUtils.invokeMethod(dialog(activity), "findingText", finding, projection);

        assertThat(text).contains("Limitación observada").contains("Turno 3").doesNotContain("Respuesta canónica");
    }

    private static TrainingActivityDialog dialog(TrainingActivity activity) {
        return dialog(activity, (InstructionReviewSnapshotDto) null);
    }

    private static TrainingActivityDialog dialog(TrainingActivity activity, InstructionReviewSnapshotDto reviewSnapshot) {
        var trainingActivityService = mock(TrainingActivityService.class);
        var safeBrowserModeService = mock(SafeBrowserModeService.class);
        when(trainingActivityService.listAssignments(activity.getId())).thenReturn(List.of());
        when(safeBrowserModeService.listOpenAlerts(activity.getId())).thenReturn(List.of());
        if (reviewSnapshot != null) {
            when(trainingActivityService.getInstructionReviewSnapshot(activity.getId())).thenReturn(
                    reviewSnapshot,
                    reviewSnapshot,
                    reviewSnapshot,
                    reviewSnapshot,
                    reviewSnapshot);
        }
        return new TrainingActivityDialog(
                activity,
                trainingActivityService,
                safeBrowserModeService,
                new SafeBrowserAssignmentStateBus(),
                mock(TrainingActivityReportProjectionService.class),
                _ -> {},
                () -> {});
    }

    private static TrainingActivityDialog dialog(
            TrainingActivity activity,
            TrainingActivityReportProjectionService reportProjectionService) {
        var trainingActivityService = mock(TrainingActivityService.class);
        var safeBrowserModeService = mock(SafeBrowserModeService.class);
        when(trainingActivityService.listAssignments(activity.getId())).thenReturn(List.of());
        when(safeBrowserModeService.listOpenAlerts(activity.getId())).thenReturn(List.of());
        return new TrainingActivityDialog(
                activity,
                trainingActivityService,
                safeBrowserModeService,
                new SafeBrowserAssignmentStateBus(),
                reportProjectionService,
                _ -> {},
                () -> {});
    }

    private static TrainingActivityReportProjectionService.ReportProjection projection(
            TrainingActivityAssignment assignment,
            com.wornux.data.entities.training_activity.TrainingActivityReportStatus status) {
        return new TrainingActivityReportProjectionService.ReportProjection(
                assignment,
                status,
                status == com.wornux.data.entities.training_activity.TrainingActivityReportStatus.READY
                        ? com.wornux.data.entities.training_activity.EvidenceStatus.STRONG_EVIDENCE
                        : null,
                status == com.wornux.data.entities.training_activity.TrainingActivityReportStatus.READY
                        ? "Síntesis respaldada por evidencia."
                        : "",
                List.of(),
                List.of(),
                List.of(),
                List.of("Continuar con práctica guiada."),
                null,
                List.of(new TrainingActivityReportProjectionService.TurnProjection(
                        1, "¿Qué observaste?", "Una respuesta completa.")));
    }

    private static TrainingActivity activity() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Actividad final");
        ReflectionTestUtils.setField(activity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        return activity;
    }

    private static TrainingActivity draftActivity() {
        var activity = activity();
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.DRAFT);
        return activity;
    }

    private static TrainingActivityAssignment assignment(TrainingActivity activity) {
        var account = new Account();
        ReflectionTestUtils.setField(account, "firstName", "Student");
        ReflectionTestUtils.setField(account, "lastName", "Example");
        var tenantAccount = new TenantAccount();
        ReflectionTestUtils.setField(tenantAccount, "account", account);
        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(member, "tenantAccount", tenantAccount);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.SUBMITTED);
        ReflectionTestUtils.setField(assignment, "submittedAt", Instant.now());
        return assignment;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        return (T) ReflectionTestUtils.getField(target, name);
    }

    private static String messageText(MessageItem item) {
        return field(item, "text");
    }

    private static com.vaadin.flow.component.textfield.TextField titleField(TrainingActivityDialog dialog) {
        return field(dialog, "titleField");
    }

    private static InstructionLinterEditor instructionField(TrainingActivityDialog dialog) {
        return field(dialog, "instructionField");
    }

    private static InstructionReviewSnapshotDto reviewSnapshot(
            String reviewHash,
            InstructionReviewStatus reviewStatus,
            InstructionQualityStatus qualityStatus,
            boolean canSave,
            List<InstructionLintIssueDto> issues) {
        return new InstructionReviewSnapshotDto(
                UUID.randomUUID(),
                reviewHash,
                reviewStatus,
                qualityStatus,
                canSave,
                "message",
                false,
                issues,
                "",
                Instant.now());
    }

    private static InstructionLintIssueDto issue() {
        return new InstructionLintIssueDto(
                "issue-1",
                "OPTIONAL_REFINEMENT",
                "WARNING",
                0,
                10,
                "message",
                "",
                "suggestion",
                "");
    }

    private static <T extends Component> T findDescendant(Component root, Class<T> type) {
        return descendants(root)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(TrainingActivityDialogTest::descendants));
    }
}
