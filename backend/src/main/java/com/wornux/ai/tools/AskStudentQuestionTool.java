package com.wornux.ai.tools;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
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
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AskStudentQuestionTool {

  private final QuestionHandler questionHandler;

  public AskStudentQuestionTool(QuestionHandler questionHandler) {
    this.questionHandler =
        Objects.requireNonNull(questionHandler, "questionHandler must not be null");
  }

  @FunctionalInterface
  public interface QuestionHandler {
    StudentQuestionResponse ask(StudentQuestionSet questionSet);
  }

  @Tool(
      name = "askStudentQuestion",
      description =
          """
          Use this tool when you need to ask the student short structured questions during
          execution. This allows you to:
          1. Gather the student's preferences or requirements
          2. Clarify ambiguous instructions or missing context
          3. Ask for confidence signals, feedback, or next-step decisions
          4. Diagnose the student's understanding with concise pedagogical questions

          Usage notes:
          - Ask 1 to 3 questions at a time
          - Prefer this tool over plain-text questions when structured input would be faster
            or clearer
          - Users can always provide complementary custom text
          - Use multiSelect: true to allow multiple answers for a question
          - If you recommend a specific option, make it the first option and add
            "(Recommended)" at the end of the label
          - When this tool is appropriate, emit an actual tool call; never describe, print,
            or simulate this tool in assistant text
          - Avoid using this tool for the final lightweight question right before you
            conclude a response
          """)
  public AskStudentQuestionResult askStudentQuestion(
      @ToolParam(description = "The interactive question panel to show to the student.")
          StudentQuestionSet questionSet) {

    Objects.requireNonNull(questionSet, "questionSet must not be null");
    return AskStudentQuestionResult.from(questionHandler.ask(questionSet));
  }

  public record AskStudentQuestionResult(StudentQuestionResponse response) {

    static AskStudentQuestionResult from(StudentQuestionResponse response) {
      Objects.requireNonNull(response, "question response must not be null");
      return new AskStudentQuestionResult(response);
    }
  }
}
