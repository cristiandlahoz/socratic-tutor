package com.wornux.ai.tools;

import java.util.Objects;

import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AskStudentQuestionTool {

    public static final String ASK_STUDENT_QUESTION = "askStudentQuestion";

    private final QuestionHandler questionHandler;

    public AskStudentQuestionTool(QuestionHandler questionHandler) {
        this.questionHandler = Objects.requireNonNull(questionHandler, "questionHandler must not be null");
    }

    @FunctionalInterface
    public interface QuestionHandler {
        StudentQuestionResponse ask(StudentQuestionSet questionSet);
    }

    @Tool(name = ASK_STUDENT_QUESTION,
            description = """
                          Ask the student 1 to 3 short diagnostic questions when the next tutoring move depends on missing observable progress or clarification.

                          Use this when the student asks for help with an exercise but has not provided the statement, attempt, code, error, wrong output, stuck point, or current idea.

                          Also use this for doubts or conceptual questions when a brief clarification would make the response more precise, such as the topic, course context, specific confusion, example they are working on, or level of detail needed.

                          Prefer open-ended questions. Use selectable options only for progress-state or context categories. Never offer options that mean: solve it, write full code, provide complete logic, or do the assignment.
                          """)
    public AskStudentQuestionResult askStudentQuestion(
            @ToolParam(
                    description = "The interactive question panel to show to the student.") StudentQuestionSet questionSet) {

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
