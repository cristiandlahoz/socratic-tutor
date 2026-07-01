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
                          Ask the student 1 to 3 short questions when their answer determines the next tutoring move.
                          Collect observable progress: statement, attempt, code, error, wrong output, stuck point, or current idea.
                          Options are optional; use them only for useful categories such as progress state or available material.
                          Never offer options that mean solve it, write full code, provide complete logic, or do the assignment.
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
