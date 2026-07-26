package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingActivityViewReviewResetTest extends BrowserlessTest {

    @Test
    void saveClickPersistsImmediatelyWhenTheAppliedSuggestionHasACachedGoodReview() {
        var service = mock(TrainingActivityService.class);
        var savedActivity = draftActivity();
        var derivedReview = reviewSnapshot();
        when(service.listAll()).thenReturn(List.of());
        when(service.reviewDraft(any())).thenReturn(derivedReview);
        when(service.createPending(any())).thenReturn(savedActivity);
        when(service.getInstructionReviewSnapshot(savedActivity.getId())).thenReturn(derivedReview);
        var view = view(service);

        titleField(view).setValue("Actividad con sugerencia aplicada");
        instructionField(view).setValue("Diseña cinco preguntas de dificultad media.");

        ReflectionTestUtils.invokeMethod(view, "onSave");

        verify(service).reviewDraft(any());
        verify(service).createPending(any());
        assertThat(titleField(view).getValue()).isEmpty();
        assertThat(instructionField(view).getValue()).isEmpty();
    }

    @Test
    void successfulDraftSaveClearsTheInstructionReviewUiState() {
        var service = mock(TrainingActivityService.class);
        var savedActivity = draftActivity();
        var review = reviewSnapshot();
        when(service.listAll()).thenReturn(List.of());
        when(service.createPending(any())).thenReturn(savedActivity);
        when(service.getInstructionReviewSnapshot(savedActivity.getId())).thenReturn(review);
        var view = view(service);
        var instructionField = instructionField(view);

        titleField(view).setValue("Actividad con revisión");
        instructionField.setValue("Explica cada decisión y aporta evidencia concreta.");
        instructionField.getElement().setProperty("reviewSnapshot", "stale-review");
        instructionField.getElement().setProperty("stale", true);
        instructionField.setReviewing(true);
        ReflectionTestUtils.setField(view, "lastReviewSnapshot", review);
        ReflectionTestUtils.setField(view, "lastReviewedTitle", titleField(view).getValue());
        ReflectionTestUtils.setField(view, "lastReviewedInstructions", instructionField.getValue());

        ReflectionTestUtils.invokeMethod(view, "persistDraft", titleField(view).getValue(), instructionField.getValue(), "");

        assertThat(titleField(view).getValue()).isEmpty();
        assertThat(instructionField.getValue()).isEmpty();
        assertThat(instructionField.getElement().getProperty("reviewSnapshot")).isEmpty();
        assertThat(instructionField.getElement().getProperty("stale", true)).isFalse();
        assertThat(instructionField.getElement().getProperty("reviewing", true)).isFalse();
        assertThat(instructionField.getElement().getProperty("reviewResetToken")).isNotBlank();
        assertThat(ReflectionTestUtils.getField(view, "lastReviewSnapshot")).isNull();
    }

    @Test
    void failedDraftSavePreservesActionableInstructionReviewUiState() {
        var service = mock(TrainingActivityService.class);
        var review = reviewSnapshot();
        when(service.listAll()).thenReturn(List.of());
        when(service.createPending(any())).thenThrow(new IllegalStateException("Persistence failed"));
        var view = view(service);
        var instructionField = instructionField(view);

        titleField(view).setValue("Actividad con revisión");
        instructionField.setValue("Explica cada decisión y aporta evidencia concreta.");
        instructionField.getElement().setProperty("reviewSnapshot", "actionable-review");
        instructionField.getElement().setProperty("stale", false);
        instructionField.setReviewing(false);
        instructionField.getElement().setProperty("reviewResetToken", "before-save");
        ReflectionTestUtils.setField(view, "lastReviewSnapshot", review);

        ReflectionTestUtils.invokeMethod(view, "persistDraft", titleField(view).getValue(), instructionField.getValue(), "");

        assertThat(titleField(view).getValue()).isEqualTo("Actividad con revisión");
        assertThat(instructionField.getValue()).isEqualTo("Explica cada decisión y aporta evidencia concreta.");
        assertThat(instructionField.getElement().getProperty("reviewSnapshot")).isEqualTo("actionable-review");
        assertThat(instructionField.getElement().getProperty("reviewResetToken")).isEqualTo("before-save");
        assertThat(ReflectionTestUtils.getField(view, "lastReviewSnapshot")).isEqualTo(review);
    }

    private static TrainingActivityView view(TrainingActivityService service) {
        var ui = UI.getCurrent();
        var view = new TrainingActivityView(
                service,
                mock(SafeBrowserModeService.class),
                new SafeBrowserAssignmentStateBus(),
                mock(TrainingActivityReportProjectionService.class),
                mock(WorkspaceRoutingService.class),
                mock(AuthenticatedUserContextUtils.class));
        ui.add(view);
        return view;
    }

    private static TrainingActivity draftActivity() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.DRAFT);
        return activity;
    }

    private static InstructionReviewSnapshotDto reviewSnapshot() {
        return new InstructionReviewSnapshotDto(
                UUID.randomUUID(),
                "review-hash",
                InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.GOOD,
                true,
                "Revisión favorable.",
                false,
                List.of(),
                "",
                Instant.now());
    }

    private static TextField titleField(TrainingActivityView view) {
        return (TextField) ReflectionTestUtils.getField(view, "titleField");
    }

    private static InstructionLinterEditor instructionField(TrainingActivityView view) {
        return (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
    }
}
