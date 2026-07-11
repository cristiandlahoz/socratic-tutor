package com.wornux.ui.training_activity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.vaadin.flow.component.UI;
import com.wornux.config.ApplicationProperties;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.ui.conversation.ConversationState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingAssignmentViewTest {

    @Test
    void af4_blankComposerInputIsRejectedInTheUiBeforeTheDurableCommand() {
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var properties = new ApplicationProperties.Ai.Conversation();
            properties.setContextWindowTokens(2000);
            var view = new TrainingAssignmentView(evaluationService, mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(), properties);
            ReflectionTestUtils.setField(view, "assignmentId", UUID.randomUUID());
            var state = (ConversationState) ReflectionTestUtils.getField(view, "composerState");
            state.composerText().set(" \t ");
            UI.setCurrent(ui);

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            verify(evaluationService, never()).submitAnswer(any(), any(), any());
        }
        finally {
            UI.setCurrent(null);
        }
    }
}
