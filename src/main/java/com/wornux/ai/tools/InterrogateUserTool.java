package com.wornux.ai.tools;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.dtos.chat.GuardCheck;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

public class InterrogateUserTool {

    public static final String INTERROGATE_USER = "interrogateUser";

    private final QuestionHandler questionHandler;
    private final ResponseGuard responseGuard;

    public InterrogateUserTool(QuestionHandler questionHandler, ResponseGuard responseGuard) {
        this.questionHandler = Objects.requireNonNull(questionHandler, "questionHandler must not be null");
        this.responseGuard = Objects.requireNonNull(responseGuard, "responseGuard must not be null");
    }

    @FunctionalInterface
    public interface QuestionHandler {
        StudentQuestionResponse ask(StudentQuestionSet questionSet);
    }

    @FunctionalInterface
    public interface ResponseGuard {
        GuardCheck check(StudentQuestionSet questionSet, StudentQuestionResponse response);
    }

    public static ToolExecutionExceptionProcessor toolExceptionProcessor() {
        return DefaultToolExecutionExceptionProcessor.builder()
                .rethrowExceptions(List.of(InteractiveResponseRejectedException.class))
                .build();
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
        var response = questionHandler.ask(questionSet);
        validateResponse(questionSet, response);
        return InterrogateUserResult.from(applyGuard(response, responseGuard.check(questionSet, response)));
    }

    private StudentQuestionResponse applyGuard(StudentQuestionResponse response, GuardCheck guardCheck) {
        return switch (guardCheck.action()) {
            case ALLOW -> response;
            case STEER -> steer(response, guardCheck.safeUserMessage());
            case SHORT_CIRCUIT -> throw new InteractiveResponseRejectedException(guardCheck.directResponse());
        };
    }

    private StudentQuestionResponse steer(StudentQuestionResponse response, String safeUserMessage) {
        var rewrittenAnswers = response.answers()
                .stream()
                .map(answer -> new StudentQuestionAnswer(
                    answer.questionId(),
                    answer.selectedOptionLabels(),
                    answer.customText().isBlank() ? "" : safeUserMessage))
                .toList();
        if (rewrittenAnswers.stream().noneMatch(answer -> !answer.customText().isBlank())) {
            var first = rewrittenAnswers.getFirst();
            rewrittenAnswers = new java.util.ArrayList<>(rewrittenAnswers);
            rewrittenAnswers.set(0, new StudentQuestionAnswer(
                first.questionId(), first.selectedOptionLabels(), safeUserMessage));
        }
        return new StudentQuestionResponse(rewrittenAnswers);
    }

    private void validateResponse(StudentQuestionSet questionSet, StudentQuestionResponse response) {
        Objects.requireNonNull(response, "question response must not be null");
        if (response.answers().size() != questionSet.questions().size()) {
            throw invalidResponse();
        }
        var seenQuestionIds = new HashSet<String>();
        for (var answer : response.answers()) {
            int questionIndex = questionIndex(answer.questionId(), questionSet.questions().size());
            if (!seenQuestionIds.add(answer.questionId())) {
                throw invalidResponse();
            }
            List<String> offeredLabels = questionSet.questions()
                    .get(questionIndex)
                    .options()
                    .stream()
                    .map(option -> option.label())
                    .toList();
            if (!offeredLabels.containsAll(answer.selectedOptionLabels())) {
                throw invalidResponse();
            }
        }
    }

    private int questionIndex(String questionId, int questionCount) {
        try {
            int index = Integer.parseInt(questionId.substring(1));
            if (!questionId.equals("q" + index) || index < 0 || index >= questionCount) {
                throw new IllegalArgumentException();
            }
            return index;
        }
        catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private InteractiveResponseRejectedException invalidResponse() {
        return new InteractiveResponseRejectedException(GuardClassifierService.TECHNICAL_FAILURE_RESPONSE);
    }

    public static final class InteractiveResponseRejectedException extends RuntimeException {

        private final String directResponse;

        public InteractiveResponseRejectedException(String directResponse) {
            super("Interactive student response was rejected");
            this.directResponse = directResponse;
        }

        public String directResponse() {
            return directResponse;
        }
    }

    public record InterrogateUserResult(StudentQuestionResponse response) {

        static InterrogateUserResult from(StudentQuestionResponse response) {
            Objects.requireNonNull(response, "question response must not be null");
            return new InterrogateUserResult(response);
        }
    }
}
