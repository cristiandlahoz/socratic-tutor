package com.wornux.ai.tools;

import java.util.Objects;

import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class InterrogateUserTool {

    public static final String INTERROGATE_USER = "interrogateUser";

    private final QuestionHandler questionHandler;

    public InterrogateUserTool(QuestionHandler questionHandler) {
        this.questionHandler = Objects.requireNonNull(questionHandler, "questionHandler must not be null");
    }

    @FunctionalInterface
    public interface QuestionHandler {
        StudentQuestionResponse ask(StudentQuestionSet questionSet);
    }

    @Tool(name = INTERROGATE_USER,
            description = """
                          Show the student an interactive panel with 1 to 3 concise diagnostic questions.

                          Use this before teaching when the next tutoring move depends on the student's goal, current understanding, stuck point, attempt, code, error, wrong output, or missing observable progress.

                          Prefer this for first-turn exercise intake and exercise dumps. If the student provides only an exercise statement or assignment goal, ask what they do not understand or which part they want help analyzing before giving hints.

                          Also use this for conceptual doubts when a brief clarification would make the response more precise, such as the topic, course context, specific confusion, example they are working on, or desired level of detail.

                          Prefer open-ended questions. Use selectable options only for progress-state, goal, or context categories. Good option themes include: I do not understand the statement; I need help identifying inputs/outputs; I need help deciding the condition/formula; I have code but it fails; I only need syntax help.

                          Never offer options that mean: solve it, write full code, provide complete logic, show the full step-by-step solution, or do the assignment.
                          """)
    public InterrogateUserResult interrogateUser(
            @ToolParam(
                    description = "The interactive diagnostic question panel to show to the student.") StudentQuestionSet questionSet) {

        Objects.requireNonNull(questionSet, "questionSet must not be null");
        return InterrogateUserResult.from(questionHandler.ask(questionSet));
    }

    public record InterrogateUserResult(StudentQuestionResponse response) {

        static InterrogateUserResult from(StudentQuestionResponse response) {
            Objects.requireNonNull(response, "question response must not be null");
            return new InterrogateUserResult(response);
        }
    }
}
