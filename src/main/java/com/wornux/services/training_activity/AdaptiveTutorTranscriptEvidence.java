package com.wornux.services.training_activity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class AdaptiveTutorTranscriptEvidence {

    private static final Pattern PROFANITY_PATTERN = Pattern.compile("(?iu)\\b(mierda|carajo|diablo|wtf|fuck|shit|frustrad[oa]?|frustró|frustro)\\b");
    private static final int VERY_BRIEF_ANSWER_MAX_LENGTH = 23;
    private static final int SUPPORTED_REASONING_MIN_LENGTH = 30;
    private static final int USEFUL_ANSWER_MIN_LENGTH = 24;

    private final List<String> observableEvidence;
    private final List<String> limitations;
    private final boolean tutorFalsePremiseDetected;
    private final boolean studentCorrectedFalsePremise;
    private final boolean validBracelessLoopConfirmed;
    private final boolean hasUsefulAnswer;
    private final boolean hasProfanity;

    private AdaptiveTutorTranscriptEvidence(
            List<String> observableEvidence,
            List<String> limitations,
            boolean tutorFalsePremiseDetected,
            boolean studentCorrectedFalsePremise,
            boolean validBracelessLoopConfirmed,
            boolean hasUsefulAnswer,
            boolean hasProfanity) {
        this.observableEvidence = List.copyOf(observableEvidence);
        this.limitations = List.copyOf(limitations);
        this.tutorFalsePremiseDetected = tutorFalsePremiseDetected;
        this.studentCorrectedFalsePremise = studentCorrectedFalsePremise;
        this.validBracelessLoopConfirmed = validBracelessLoopConfirmed;
        this.hasUsefulAnswer = hasUsefulAnswer;
        this.hasProfanity = hasProfanity;
    }

    static AdaptiveTutorTranscriptEvidence from(List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return new AdaptiveTutorTranscriptEvidence(
                    List.of("- Todavía no hay intercambios previos registrados."),
                    List.of("- Aún no existe suficiente transcript para una conclusión sólida."),
                    false,
                    false,
                    false,
                    false,
                    false);
        }

        var observableEvidence = new LinkedHashSet<String>();
        var limitations = new LinkedHashSet<String>();
        var tutorFalsePremiseDetected = false;
        var studentCorrectedFalsePremise = false;
        var validBracelessLoopConfirmed = false;
        var hasUsefulAnswer = false;
        var hasProfanity = false;

        for (var exchange : transcript) {
            var question = normalize(exchange.question());
            var answer = normalize(exchange.answer());
            var questionLower = question.toLowerCase(Locale.ROOT);
            var answerSignals = answerSignals(answer);
            var answerLower = answerSignals.normalizedLowerCase();

            var questionTargetsSyntaxError = AdaptiveTutorFalsePremiseSignals.containsErrorPremiseRequest(questionLower);
            var questionShowsValidLoop = AdaptiveTutorFalsePremiseSignals.containsLikelyValidCLoop(question);
            var answerCorrectsPremise = answerSignals.correctionOfTutorPremise();
            var mentionsBracelessLoop = AdaptiveTutorFalsePremiseSignals.mentionsBracelessLoop(questionLower)
                    || AdaptiveTutorFalsePremiseSignals.mentionsBracelessLoop(answerLower);

            if (answerSignals.hasUsefulEvidence()) {
                hasUsefulAnswer = true;
            }

            if (PROFANITY_PATTERN.matcher(answerLower).find() || answerLower.contains("frustr")) {
                hasProfanity = true;
            }

            if (questionTargetsSyntaxError && questionShowsValidLoop) {
                tutorFalsePremiseDetected = true;
                limitations.add("- Parte de la interacción quedó condicionada por una premisa incorrecta del tutor sobre el código mostrado.");
            }

            if (answerCorrectsPremise) {
                studentCorrectedFalsePremise = true;
                observableEvidence.add("- El estudiante corrigió explícitamente una premisa incorrecta sobre si el fragmento mostrado compila.");
            }

            if (answerCorrectsPremise && mentionsBracelessLoop) {
                validBracelessLoopConfirmed = true;
                observableEvidence.add("- El estudiante distinguió correctamente que un `for` sin llaves puede compilar cuando solo controla una instrucción.");
            }

            if (answerSignals.code()) {
                observableEvidence.add("- El transcript incluye fragmentos de código aportados por el estudiante como evidencia observable.");
            }
            else if (answerSignals.reasoning()) {
                observableEvidence.add("- El estudiante ofreció al menos una explicación con justificación explícita y desarrollada.");
            }
        }

        if (!hasUsefulAnswer) {
            limitations.add("- La evidencia recogida no alcanza para una conclusión sólida.");
        }
        if (hasProfanity && hasUsefulAnswer) {
            limitations.add("- Hubo señales de frustración, pero no invalidan por sí solas las respuestas útiles ya registradas.");
        }
        else if (hasProfanity) {
            limitations.add("- Hubo señales de frustración y muy poca evidencia útil para sostener una conclusión confiable.");
        }

        if (observableEvidence.isEmpty()) {
            observableEvidence.add("- La conversación todavía aporta poca evidencia observable y conviene retomar la actividad con una pregunta más acotada.");
        }
        if (limitations.isEmpty()) {
            limitations.add("- No se detectaron limitaciones técnicas adicionales fuera de las propias del transcript disponible.");
        }

        return new AdaptiveTutorTranscriptEvidence(
                new ArrayList<>(observableEvidence),
                new ArrayList<>(limitations),
                tutorFalsePremiseDetected,
                studentCorrectedFalsePremise,
                validBracelessLoopConfirmed,
                hasUsefulAnswer,
                hasProfanity);
    }

    boolean tutorFalsePremiseDetected() {
        return tutorFalsePremiseDetected;
    }

    boolean studentCorrectedFalsePremise() {
        return studentCorrectedFalsePremise;
    }

    boolean validBracelessLoopConfirmed() {
        return validBracelessLoopConfirmed;
    }

    boolean hasUsefulAnswer() {
        return hasUsefulAnswer;
    }

    String promptSummary() {
        return String.join("\n", observableEvidence);
    }

    String reportEvidenceSummary() {
        return String.join("\n", observableEvidence);
    }

    String reportLimitationsSummary() {
        return String.join("\n", limitations);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r", "").trim();
    }

    static AnswerSignals answerSignals(String value) {
        var normalized = normalize(value);
        if (normalized.isBlank()) {
            return new AnswerSignals(normalized, true, false, false, false, false, false, false, 0);
        }
        var lower = normalized.toLowerCase(Locale.ROOT);
        return new AnswerSignals(
                normalized,
                false,
                normalized.length() <= VERY_BRIEF_ANSWER_MAX_LENGTH,
                containsAny(lower, "no sé", "no se", "no entiendo", "no lo sé", "i don't know", "i dont know", "not sure"),
                containsAny(lower, "por ejemplo", "ejemplo", "for example", "e.g."),
                containsSupportedReasoning(normalized),
                containsCode(normalized),
                AdaptiveTutorFalsePremiseSignals.containsStudentCorrection(lower),
                normalized.length());
    }

    private static boolean containsCode(String value) {
        return value.contains("```") || value.contains("for (") || value.contains("printf(") || value.contains(";");
    }

    private static boolean containsSupportedReasoning(String value) {
        var lower = value.toLowerCase(Locale.ROOT);
        if (!(lower.contains("porque") || lower.contains("ya que") || lower.contains("por eso") || lower.contains("because") || lower.contains("therefore"))) {
            return false;
        }
        return value.replaceAll("\\s+", " ").trim().length() >= SUPPORTED_REASONING_MIN_LENGTH;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (var candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    record AnswerSignals(
            String normalized,
            boolean blank,
            boolean veryBrief,
            boolean unknown,
            boolean example,
            boolean reasoning,
            boolean code,
            boolean correctionOfTutorPremise,
            int length) {

        boolean hasUsefulEvidence() {
            return !blank && (length >= USEFUL_ANSWER_MIN_LENGTH || correctionOfTutorPremise || code || reasoning);
        }

        String normalizedLowerCase() {
            return normalized.toLowerCase(Locale.ROOT);
        }
    }
}
