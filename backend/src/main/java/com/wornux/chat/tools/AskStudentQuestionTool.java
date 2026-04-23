package com.wornux.chat.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AskStudentQuestionTool {

  private static final Duration STUDENT_RESPONSE_TIMEOUT = Duration.ofMinutes(5);

  private final QuestionInteractionService questionInteractionService;
  private final ToolUsageAuditService toolUsageAuditService;

  @Tool(
      name = "askStudentQuestion",
      description =
          "Shows 1 to 3 short structured questions to the student when you need their preference,"
              + " choice, clarification, confidence signal, feedback, next-step decision, or"
              + " pedagogical diagnosis. Use concise options and remember that each question also"
              + " supports complementary custom text. Prefer this tool over plain-text questions"
              + " when structured input would be faster or clearer. Avoid using this tool for the"
              + " final lightweight question right before you conclude a response.")
  public AskStudentQuestionResult askStudentQuestion(
      @ToolParam(description = "The interactive question panel to show to the student.")
          StudentQuestionSet questionSet,
      ToolContext toolContext) {

    return toolUsageAuditService.audit(
        "askStudentQuestion",
        toolContext,
        "questions=%d title=%s"
            .formatted(questionSet.questions().size(), compact(questionSet.title())),
        () -> {
          var routing = routingFrom(toolContext);
          var response =
              questionInteractionService.askQuestions(
                  routing, questionSet, STUDENT_RESPONSE_TIMEOUT);
          var result = AskStudentQuestionResult.from(response);
          return new ToolUsageAuditService.ToolResult<>(
              result,
              "answers=%d".formatted(response.answers().size()),
              new ToolLearningSignal(
                  "student_question_response",
                  questionSet.profileImpact() == StudentQuestionSet.ProfileImpact.PEDAGOGICAL,
                  "structured_user_elicitation"));
        });
  }

  private QuestionInteractionService.QuestionRouting routingFrom(ToolContext toolContext) {
    var context = toolContext.getContext();
    return new QuestionInteractionService.QuestionRouting(
        toUuid(context.get(ToolUsageAuditService.CLIENT_ID)),
        toUuid(context.get(ToolUsageAuditService.CONVERSATION_ID)),
        toUuid(context.get(ToolUsageAuditService.TURN_ID)));
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

  private String compact(String value) {
    return value.length() <= 40 ? value : value.substring(0, 37) + "...";
  }

  public record AskStudentQuestionResult(
      @JsonProperty(required = true)
          @JsonPropertyDescription(
              "One answer per question, preserving the student's selected options and custom text.")
          List<AnsweredQuestion> answers) {

    static AskStudentQuestionResult from(StudentQuestionResponse response) {
      var answers =
          response.answers().stream()
              .map(
                  answer ->
                      new AnsweredQuestion(
                          answer.questionId(), answer.selectedOptionLabels(), answer.customText()))
              .toList();
      return new AskStudentQuestionResult(answers);
    }
  }

  public record AnsweredQuestion(
      @JsonProperty(required = true) @JsonPropertyDescription("Stable id of the answered question.")
          String questionId,
      @JsonProperty(required = true)
          @JsonPropertyDescription(
              "Selected option labels in the order they were chosen. Empty when the student only"
                  + " provided custom text.")
          List<String> selectedOptionLabels,
      @JsonProperty(required = true)
          @JsonPropertyDescription(
              "Complementary free-text answer provided by the student. Empty when the student used"
                  + " only predefined options.")
          String customText) {}
}
