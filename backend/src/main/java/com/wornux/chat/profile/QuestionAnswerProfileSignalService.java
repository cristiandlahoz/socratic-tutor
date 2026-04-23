package com.wornux.chat.profile;

import com.wornux.chat.tools.QuestionInteractionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QuestionAnswerProfileSignalService {

  public QuestionAnswerProfileSignals interpret(
      List<QuestionInteractionService.CompletedQuestionInteraction> interactions) {
    if (interactions == null || interactions.isEmpty()) {
      return QuestionAnswerProfileSignals.empty();
    }

    var pedagogicalInteractions =
        interactions.stream()
            .filter(
                interaction ->
                    interaction.questionSet().profileImpact()
                        == com.wornux.chat.questions.StudentQuestionSet.ProfileImpact.PEDAGOGICAL)
            .toList();
    if (pedagogicalInteractions.isEmpty()) {
      return QuestionAnswerProfileSignals.empty();
    }

    var levelSignals = new ArrayList<TurnProfileUpdate.LevelSignal>();
    var topics = new ArrayList<TopicKey>();
    var summaries = new ArrayList<String>();
    boolean needsConcreteExamples = false;
    HelpMode recommendedHelpMode = null;
    BigDecimal confidenceDelta = BigDecimal.ZERO;

    for (var interaction : pedagogicalInteractions) {
      for (var answer : interaction.response().answers()) {
        var normalized = normalize(answer.selectedOptionLabels(), answer.customText());
        var answerTopics = TopicKey.detectTopics(normalized);
        topics.addAll(answerTopics);
        summaries.add(summary(answer));

        if (containsAny(
            normalized,
            "desde cero",
            "beginner",
            "principiante",
            "muy perdido",
            "no entiendo",
            "confundido",
            "confundida")) {
          answerTopics.forEach(
              topic ->
                  levelSignals.add(
                      new TurnProfileUpdate.LevelSignal(
                          topic,
                          TurnProfileUpdate.SignalDirection.DOWN,
                          "interactive_question_low_confidence")));
          confidenceDelta = confidenceDelta.subtract(new BigDecimal("0.080"));
        }
        if (containsAny(
            normalized, "puedo solo", "seguro", "confidence", "intermedio", "intermediate")) {
          answerTopics.forEach(
              topic ->
                  levelSignals.add(
                      new TurnProfileUpdate.LevelSignal(
                          topic,
                          TurnProfileUpdate.SignalDirection.UP,
                          "interactive_question_high_confidence")));
          confidenceDelta = confidenceDelta.add(new BigDecimal("0.040"));
        }
        if (containsAny(
            normalized, "ejemplo", "example", "traza", "trace", "paso a paso", "step by step")) {
          needsConcreteExamples = true;
        }
        if (containsAny(normalized, "pista", "hint", "guiado", "guided", "paso a paso")) {
          recommendedHelpMode = HelpMode.GUIDED;
        }
        if (recommendedHelpMode == null
            && containsAny(normalized, "desafio", "challenge", "reto")) {
          recommendedHelpMode = HelpMode.CHALLENGE;
        }
        if (recommendedHelpMode == null && containsAny(normalized, "mixto", "mixed", "mezcla")) {
          recommendedHelpMode = HelpMode.MIXED;
        }
      }
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("interactiveAnswers", summaries);
    payload.put(
        "interactiveTopics",
        topics.stream().distinct().map(Enum::name).collect(Collectors.toList()));
    payload.put(
        "interactiveQuestionPurpose",
        pedagogicalInteractions.stream()
            .map(interaction -> interaction.questionSet().purpose())
            .distinct()
            .toList());

    return new QuestionAnswerProfileSignals(
        topics.stream().distinct().toList(),
        levelSignals,
        needsConcreteExamples,
        recommendedHelpMode,
        confidenceDelta.setScale(3, RoundingMode.HALF_UP),
        payload);
  }

  private boolean containsAny(String normalized, String... hints) {
    for (String hint : hints) {
      if (normalized.contains(hint)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(List<String> selectedOptionLabels, String customText) {
    var combined = new ArrayList<String>();
    if (selectedOptionLabels != null) {
      combined.addAll(selectedOptionLabels);
    }
    if (customText != null && !customText.isBlank()) {
      combined.add(customText);
    }
    return String.join(" ", combined).toLowerCase(Locale.ROOT);
  }

  private String summary(com.wornux.chat.questions.StudentQuestionAnswer answer) {
    var labels =
        answer.selectedOptionLabels().isEmpty()
            ? "-"
            : String.join("|", answer.selectedOptionLabels());
    if (answer.customText().isBlank()) {
      return answer.questionId() + ":" + labels;
    }
    return answer.questionId() + ":" + labels + ":" + answer.customText();
  }

  public record QuestionAnswerProfileSignals(
      List<TopicKey> topics,
      List<TurnProfileUpdate.LevelSignal> levelSignals,
      boolean needsConcreteExamples,
      HelpMode recommendedHelpMode,
      BigDecimal confidenceDelta,
      Map<String, Object> payload) {
    static QuestionAnswerProfileSignals empty() {
      return new QuestionAnswerProfileSignals(
          List.of(),
          List.of(),
          false,
          null,
          BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP),
          Map.of());
    }
  }
}
