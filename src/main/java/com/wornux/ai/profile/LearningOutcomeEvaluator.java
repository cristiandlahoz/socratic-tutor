package com.wornux.ai.profile;

import java.util.List;

import com.wornux.dtos.profile.*;
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
        if (!evaluationCase.forbiddenPhrases()
                .stream()
                .anyMatch(phrase -> response.toLowerCase().contains(phrase.toLowerCase()))) {
            score++;
        }
        return score;
    }

    public record LearningEvaluationCase(StudentProfileSnapshot initialProfile, String studentPrompt,
            String baselineResponse, String treatmentResponse, List<String> requiredPhrases,
            List<String> forbiddenPhrases) {}

    public record LearningOutcomeEvaluation(int totalCases, int baselineWins, int treatmentWins) {}
}
