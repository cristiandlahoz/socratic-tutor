package com.wornux.ai.tools;

import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;

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
            execution. This tool is for collecting student input, context, observable progress,
            or concise pedagogical signals.

            Default behavior:
             - Ask direct questions.
             - Leave options empty unless they add real value.
             - Most academic tutoring questions should be open questions, especially when asking
               for the exercise statement, the student's attempt, code, an error message, wrong
               output, a stuck point, or an explanation of what the student thinks should happen.

            Options are optional. Use at most 3 options, and only when the question is naturally
            categorical or multi-choice, for example:
             - the student's current progress state
             - the type of exercise
             - the kind of material the student can share
             - the kind of error or difficulty they are seeing
             - a small set of concrete next-step choices that do not solve the exercise

            Option quality rules:
             - Options must represent the student's state, context, available material, or
               observable progress.
             - Options must not represent what the assistant will solve, write, implement, guess,
               or explain.
             - Options must not offer a complete solution, full code, full logic, full
               implementation, or step-by-step resolution of the exercise.
             - Options must not be marked as preferred or recommended.
             - Options must not be yes/no commands such as "Yes, write it in C" or
               "No, do not guess it".
             - Options must not be written as questions.
             - Option descriptions must clarify the option; they must not ask another question.

            Good option labels:
             - "I have not started"
             - "I do not know where to start"
             - "I have a partial idea"
             - "I have pseudocode"
             - "I have code, but it fails"
             - "I am almost done"
             - "I only have the statement"
             - "I know where I am stuck"

            Bad option labels:
             - "Yes, guide me step by step"
             - "Yes, write it in C"
             - "Tell me what it should do"
             - "Solve it for me"
             - "Give me the full code"
             - "Show me the complete logic"

            Usage notes:
             - Ask 1 to 3 questions at a time.
             - Prefer this tool over plain-text questions when structured input would be faster
               or clearer.
             - Users can always provide complementary custom text.
             - Do not add options just to fill the tool call.
             - When this tool is appropriate, emit an actual tool call; never describe, print,
               or simulate this tool in assistant text.
             - Do not wrap tool calls in Markdown code fences or add extra text after a tool
               call.
             - If the runtime uses XML tool tags internally, close the call with
               </tool_call> exactly; never close it with </tool>.
             - Avoid using this tool for the final lightweight question right before you
               conclude a response.
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
