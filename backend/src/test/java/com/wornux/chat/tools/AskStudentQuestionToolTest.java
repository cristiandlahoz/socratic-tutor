package com.wornux.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.domain.chat.questions.StudentQuestion;
import com.wornux.domain.chat.questions.StudentQuestionAnswer;
import com.wornux.domain.chat.questions.StudentQuestionOption;
import com.wornux.domain.chat.questions.StudentQuestionResponse;
import com.wornux.domain.chat.questions.StudentQuestionSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AskStudentQuestionToolTest {

    @Test
    void delegatesQuestionSetToHandlerAndReturnsRawAnswerPayload() {
        var questionSet = sampleQuestionSet();
        var response = new StudentQuestionResponse(
                List.of(new StudentQuestionAnswer("q1", List.of("Muy perdido"), "Dame ejemplo")));
        var tool = new AskStudentQuestionTool(receivedQuestionSet -> {
            assertThat(receivedQuestionSet).isSameAs(questionSet);
            return response;
        });

        var result = tool.askStudentQuestion(questionSet);

        assertThat(result.response()).isEqualTo(response);
    }

    @Test
    void rejectsMissingQuestionSetBeforeCallingHandler() {
        var tool = new AskStudentQuestionTool(_ -> {
            throw new AssertionError("handler should not be called");
        });

        assertThatNullPointerException().isThrownBy(() -> tool.askStudentQuestion(null))
                .withMessage("questionSet must not be null");
    }

    @Test
    void rejectsMissingHandlerResponse() {
        var tool = new AskStudentQuestionTool(_ -> null);

        assertThatNullPointerException().isThrownBy(() -> tool.askStudentQuestion(sampleQuestionSet()))
                .withMessage("question response must not be null");
    }

    private StudentQuestionSet sampleQuestionSet() {
        return new StudentQuestionSet("Antes de seguir",
                "diagnosis",
                StudentQuestionSet.ProfileImpact.NONE,
                List.of(
                    new StudentQuestion("q1",
                            "Confianza",
                            "Como te sientes con este tema?",
                            List.of(
                                new StudentQuestionOption("Muy perdido", "Necesito empezar de cero"),
                                new StudentQuestionOption("Voy bien", "Solo quiero validar detalles")),
                            false)));
    }
}
