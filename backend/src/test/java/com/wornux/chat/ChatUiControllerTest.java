package com.wornux.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionSet;
import com.wornux.chat.tools.QuestionInteractionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatUiControllerTest {

  @Mock private ChatService chatService;
  @Mock private ConversationService conversationService;
  @Mock private ChatUsageService chatUsageService;
  @Mock private ConversationTitleService conversationTitleService;
  @Mock private BrowserClientService browserClientService;
  @Mock private QuestionInteractionService questionInteractionService;

  private ChatUiState state;
  private ChatUiController controller;

  @BeforeEach
  void setUp() {
    state = new ChatUiState();
    controller =
        new ChatUiController(
            chatService,
            conversationService,
            chatUsageService,
            conversationTitleService,
            browserClientService,
            questionInteractionService,
            state);
  }

  @Test
  void initializeFromRoute_draft_mode_clears_pending_question_state() {
    state.pendingQuestionSet().set(sampleQuestionSet());
    state.questionSubmissionInProgress().set(true);
    when(browserClientService.resolveClientId()).thenReturn(UUID.randomUUID());
    when(conversationService.listConversations(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    var initialization = controller.initializeFromRoute(null, true);

    assertThat(initialization.rerouteRequired()).isFalse();
    assertThat(state.pendingQuestionSet().peek()).isNull();
    assertThat(state.questionSubmissionInProgress().peek()).isFalse();
  }

  @Test
  void syncPendingQuestionState_without_active_conversation_clears_panel_state() {
    state.clientId().set(UUID.randomUUID());
    state.pendingQuestionSet().set(sampleQuestionSet());
    state.questionSubmissionInProgress().set(true);

    controller.syncPendingQuestionState();

    assertThat(state.pendingQuestionSet().peek()).isNull();
    assertThat(state.questionSubmissionInProgress().peek()).isFalse();
    verifyNoInteractions(questionInteractionService);
  }

  @Test
  void syncPendingQuestionState_uses_only_real_pending_interactions() {
    var clientId = UUID.randomUUID();
    var conversationId = UUID.randomUUID();
    var questionSet = sampleQuestionSet();
    state.clientId().set(clientId);
    state.activeConversationId().set(conversationId);

    when(questionInteractionService.findPending(clientId, conversationId))
        .thenReturn(
            Optional.of(
                new QuestionInteractionService.PendingQuestionView(
                    new QuestionInteractionService.QuestionRouting(
                        clientId, conversationId, UUID.randomUUID()),
                    questionSet,
                    Instant.now())));

    controller.syncPendingQuestionState();

    assertThat(state.pendingQuestionSet().peek()).isEqualTo(questionSet);
  }

  private StudentQuestionSet sampleQuestionSet() {
    return new StudentQuestionSet(
        "Antes de seguir",
        "clarification",
        StudentQuestionSet.ProfileImpact.NONE,
        List.of(
            new StudentQuestion(
                "q1",
                "Ayuda",
                "Como prefieres que te ayude?",
                List.of(
                    new StudentQuestionOption("Paso a paso", "Te guio paso a paso."),
                    new StudentQuestionOption("Pista breve", "Te doy una pista corta.")),
                false)));
  }
}
