package com.wornux.services.training_activity;

import java.util.Locale;
import java.util.regex.Pattern;

final class AdaptiveTutorDecisionValidator {

    private static final Pattern FENCED_C_BLOCK_PATTERN = Pattern.compile("(?is)```c\\s+.*?```");
    private static final Pattern INLINE_FOR_LOOP_PATTERN = Pattern.compile("for\\s*\\([^)]*;[^)]*;[^)]*\\)");

    AdaptiveTutorDecision validate(AdaptiveTutorDecision decision, AdaptiveTutorTranscriptEvidence evidence) {
        if (decision == null || decision.type() != TutorDecisionType.QUESTION) {
            return decision;
        }

        var questionText = normalize(decision.questionText());
        if (questionText.isBlank()) {
            throw new IllegalStateException("Adaptive tutor questionText is required when continuing.");
        }

        if (looksLikeCodeQuestion(questionText) && !FENCED_C_BLOCK_PATTERN.matcher(questionText).find()) {
            throw new IllegalStateException("Adaptive tutor code questions must use fenced Markdown code blocks.");
        }

        if (AdaptiveTutorFalsePremiseSignals.containsErrorPremiseRequest(questionText)
                && (evidence.studentCorrectedFalsePremise()
                || evidence.validBracelessLoopConfirmed()
                || AdaptiveTutorFalsePremiseSignals.containsLikelyValidCLoop(questionText))) {
            throw new IllegalStateException("Adaptive tutor question contradicts transcript evidence or invents a syntax-error premise.");
        }

        return decision;
    }

    private boolean looksLikeCodeQuestion(String questionText) {
        return AdaptiveTutorFalsePremiseSignals.containsCLikeCodeSignal(questionText)
                || FENCED_C_BLOCK_PATTERN.matcher(questionText).find()
                || INLINE_FOR_LOOP_PATTERN.matcher(questionText).find();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('\r', ' ').trim();
    }
}
