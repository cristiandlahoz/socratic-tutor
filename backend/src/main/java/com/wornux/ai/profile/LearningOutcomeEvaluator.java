package com.wornux.ai.profile;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
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
import org.springframework.stereotype.Component;

@Component
public class LearningOutcomeEvaluator {

  public LearningOutcomeEvaluation compare(List<LearningEvaluationCase> cases) {
    int baselineWins = 0;
    int treatmentWins = 0;

    for (var evaluationCase : cases) {
      int baselineScore = score(evaluationCase.baselineResponse(), evaluationCase);
      int treatmentScore = score(evaluationCase.treatmentResponse(), evaluationCase);
      if (treatmentScore > baselineScore) {
        treatmentWins++;
      }
      if (baselineScore > treatmentScore) {
        baselineWins++;
      }
    }

    return new LearningOutcomeEvaluation(cases.size(), baselineWins, treatmentWins);
  }

  private int score(String response, LearningEvaluationCase evaluationCase) {
    int score = 0;
    for (String requiredPhrase : evaluationCase.requiredPhrases()) {
      if (response.toLowerCase().contains(requiredPhrase.toLowerCase())) {
        score++;
      }
    }
    if (!evaluationCase.forbiddenPhrases().stream()
        .anyMatch(phrase -> response.toLowerCase().contains(phrase.toLowerCase()))) {
      score++;
    }
    return score;
  }

  public record LearningEvaluationCase(
      StudentProfileSnapshot initialProfile,
      String studentPrompt,
      String baselineResponse,
      String treatmentResponse,
      List<String> requiredPhrases,
      List<String> forbiddenPhrases) {}

  public record LearningOutcomeEvaluation(int totalCases, int baselineWins, int treatmentWins) {}
}
