package com.wornux.chat.tools;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AskStudentQuestionToolTest {

    private final QuestionInteractionService questionInteractionService = new QuestionInteractionService();
    private final ToolUsageAuditService toolUsageAuditService = new ToolUsageAuditService(new SimpleMeterRegistry(), ObservationRegistry.NOOP);
    private final AskStudentQuestionTool tool = new AskStudentQuestionTool(questionInteractionService, toolUsageAuditService);

    @Test
    void tool_returns_structured_answer_summary_after_student_response() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        var questionSet = new StudentQuestionSet("Diagnostico", "preference", StudentQuestionSet.ProfileImpact.NONE, List.of(
                new StudentQuestion("q1", "Nivel", "Como te sientes con arrays?", List.of(
                        new StudentQuestionOption("Muy perdido", "Necesito empezar de cero"),
                        new StudentQuestionOption("Voy bien", "Solo quiero validar detalles")),
                        false,
                        true)));

        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = CompletableFuture.supplyAsync(() -> tool.askStudentQuestion(questionSet, context(clientId, conversationId, turnId)), executor);
            awaitPending(clientId, conversationId);

            questionInteractionService.submitResponse(
                    clientId,
                    conversationId,
                    new StudentQuestionResponse(List.of(new StudentQuestionAnswer("q1", List.of("Muy perdido"), "Necesito un ejemplo"))));

            var result = future.get(2, TimeUnit.SECONDS);
            assertThat(result.answers()).singleElement().satisfies(answer -> {
                assertThat(answer.questionId()).isEqualTo("q1");
                assertThat(answer.selectedOptionLabels()).containsExactly("Muy perdido");
                assertThat(answer.customText()).isEqualTo("Necesito un ejemplo");
            });
            assertThat(result.summary()).contains("q1 -> Muy perdido");
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolContext context(UUID clientId, UUID conversationId, UUID turnId) {
        return new ToolContext(Map.of(
                ToolUsageAuditService.CLIENT_ID, clientId,
                ToolUsageAuditService.CONVERSATION_ID, conversationId,
                ToolUsageAuditService.TURN_ID, turnId,
                ToolUsageAuditService.PROFILE_VERSION, 3L
        ));
    }

    private void awaitPending(UUID clientId, UUID conversationId) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (questionInteractionService.findPending(clientId, conversationId).isPresent()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        throw new AssertionError("Pending question interaction was not published in time");
    }
}
