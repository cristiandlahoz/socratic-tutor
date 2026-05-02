package com.wornux.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.chat.questions.StudentQuestion;
import com.wornux.domain.chat.questions.StudentQuestionAnswer;
import com.wornux.domain.chat.questions.StudentQuestionOption;
import com.wornux.domain.chat.questions.StudentQuestionResponse;
import com.wornux.domain.chat.questions.StudentQuestionSet;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AskStudentQuestionToolTest {

  @Test
  void delegatesQuestionSetToHandlerAndReturnsRawAnswerPayload() {
    var questionSet = sampleQuestionSet();
    var response =
        new StudentQuestionResponse(
            List.of(new StudentQuestionAnswer("q1", List.of("Muy perdido"), "Dame ejemplo")));
    var tool =
        new AskStudentQuestionTool(
            receivedQuestionSet -> {
              assertThat(receivedQuestionSet).isSameAs(questionSet);
              return response;
            });

    var result = tool.askStudentQuestion(questionSet);

    assertThat(result.response()).isEqualTo(response);
  }

  @Test
  void rejectsMissingQuestionSetBeforeCallingHandler() {
    var tool =
        new AskStudentQuestionTool(
            _ -> {
              throw new AssertionError("handler should not be called");
            });

    assertThatNullPointerException()
        .isThrownBy(() -> tool.askStudentQuestion(null))
        .withMessage("questionSet must not be null");
  }

  @Test
  void rejectsMissingHandlerResponse() {
    var tool = new AskStudentQuestionTool(_ -> null);

    assertThatNullPointerException()
        .isThrownBy(() -> tool.askStudentQuestion(sampleQuestionSet()))
        .withMessage("question response must not be null");
  }

  private StudentQuestionSet sampleQuestionSet() {
    return new StudentQuestionSet(
        "Antes de seguir",
        "diagnosis",
        StudentQuestionSet.ProfileImpact.NONE,
        List.of(
            new StudentQuestion(
                "q1",
                "Confianza",
                "Como te sientes con este tema?",
                List.of(
                    new StudentQuestionOption("Muy perdido", "Necesito empezar de cero"),
                    new StudentQuestionOption("Voy bien", "Solo quiero validar detalles")),
                false)));
  }
}
