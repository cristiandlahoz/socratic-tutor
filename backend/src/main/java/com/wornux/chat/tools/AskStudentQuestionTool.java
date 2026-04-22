package com.wornux.chat.tools;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AskStudentQuestionTool {

    private static final Duration STUDENT_RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    private final QuestionInteractionService questionInteractionService;
    private final ToolUsageAuditService toolUsageAuditService;

    @Tool(name = "askStudentQuestion",
            description = "Shows 1 to 3 short structured questions to the student when you need their preference, choice, clarification, confidence signal, feedback, next-step decision, or pedagogical diagnosis. Use concise options and remember that each question also supports complementary custom text. Prefer this tool over plain-text questions when structured input would be faster or clearer. Avoid using this tool for the final lightweight question right before you conclude a response.")
    public AskStudentQuestionResult askStudentQuestion(
            @ToolParam(description = "The interactive question panel to show to the student.")
            StudentQuestionSet questionSet,
            ToolContext toolContext) {

        return toolUsageAuditService.audit(
                "askStudentQuestion",
                toolContext,
                "questions=%d title=%s".formatted(questionSet.questions().size(), compact(questionSet.title())),
                () -> {
                    var routing = routingFrom(toolContext);
                    var response = questionInteractionService.askQuestions(routing, questionSet, STUDENT_RESPONSE_TIMEOUT);
                    var result = AskStudentQuestionResult.from(response);
                    return new ToolUsageAuditService.ToolResult<>(
                            result,
                            summarizeAnswers(response),
                            new ToolLearningSignal("student_question_response", questionSet.profileImpact() == StudentQuestionSet.ProfileImpact.PEDAGOGICAL, "structured_user_elicitation"));
                });
    }

    private QuestionInteractionService.QuestionRouting routingFrom(ToolContext toolContext) {
        var context = toolContext.getContext();
        return new QuestionInteractionService.QuestionRouting(
                toUuid(context.get(ToolUsageAuditService.CLIENT_ID)),
                toUuid(context.get(ToolUsageAuditService.CONVERSATION_ID)),
                toUuid(context.get(ToolUsageAuditService.TURN_ID))
        );
    }

    private UUID toUuid(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw == null) {
            throw new IllegalArgumentException("Missing tool routing identifier");
        }
        return UUID.fromString(String.valueOf(raw));
    }

    private String summarizeAnswers(StudentQuestionResponse response) {
        return response.answers().stream()
                .map(answer -> "%s:%s%s".formatted(
                        answer.questionId(),
                        String.join("|", answer.selectedOptionLabels()),
                        answer.customText().isBlank() ? "" : "+text"))
                .reduce((left, right) -> left + ";" + right)
                .orElse("no_answers");
    }

    private String compact(String value) {
        return value.length() <= 40 ? value : value.substring(0, 37) + "...";
    }

    public record AskStudentQuestionResult(
            @JsonPropertyDescription("One answer per question, preserving the student's selected options and custom text.")
            List<AnsweredQuestion> answers,
            @JsonPropertyDescription("Compact natural-language summary of the student's answers.")
            String summary
    ) {

        static AskStudentQuestionResult from(StudentQuestionResponse response) {
            var answers = response.answers().stream()
                    .map(answer ->
                            new AnsweredQuestion(answer.questionId(),
                                    answer.selectedOptionLabels(),
                                    answer.customText()))
                    .toList();
            var summary = answers.stream()
                    .map(AnsweredQuestion::summaryLine)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            return new AskStudentQuestionResult(answers, summary);
        }
    }

    public record AnsweredQuestion(
            String questionId,
            List<String> selectedOptionLabels,
            String customText
    ) {

        private String summaryLine() {
            var selected = selectedOptionLabels == null || selectedOptionLabels.isEmpty()
                    ? "no predefined option selected"
                    : String.join(", ", selectedOptionLabels);
            var custom = customText == null || customText.isBlank() ? "" : " | custom: " + customText;
            return questionId + " -> " + selected + custom;
        }
    }
}
