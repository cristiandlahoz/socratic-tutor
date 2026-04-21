package com.wornux.chat.profile;

import com.wornux.chat.StoredChatMessage;
import com.wornux.chat.tools.ToolExecutionAudit;
import com.wornux.chat.tools.QuestionInteractionService;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TurnProfileInferenceService {

    private static final Pattern SPANISH_PATTERN = Pattern.compile("\\b(que|como|porque|ciclo|bucle|arreglo|funcion|función|explica|ayuda|dame|paso a paso)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXAMPLE_PATTERN = Pattern.compile("\\b(ejemplo|example|paso a paso|step by step|traza|trace)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFUSION_PATTERN = Pattern.compile("\\b(no entiendo|me perdi|me perdí|confundo|confund[io]|why|lost|stuck|ayuda)\\b", Pattern.CASE_INSENSITIVE);

    private final QuestionAnswerProfileSignalService questionAnswerProfileSignalService;

    public TurnProfileInferenceService(QuestionAnswerProfileSignalService questionAnswerProfileSignalService) {
        this.questionAnswerProfileSignalService = questionAnswerProfileSignalService;
    }

    public TurnProfileUpdate infer(UUID conversationId,
                                   UUID turnId,
                                   String userInput,
                                   String assistantResponse,
                                   List<StoredChatMessage> memoryWindow,
                                   List<ToolExecutionAudit> toolAudits,
                                   List<QuestionInteractionService.CompletedQuestionInteraction> questionInteractions) {
        var combinedText = "%s %s".formatted(userInput == null ? "" : userInput, assistantResponse == null ? "" : assistantResponse);
        var topics = new ArrayList<>(TopicKey.detectTopics(combinedText));
        if (topics.isEmpty()) {
            topics.addAll(memoryWindow.stream()
                    .filter(message -> message.role() == MessageType.USER)
                    .flatMap(message -> TopicKey.detectTopics(message.content()).stream())
                    .distinct()
                    .toList());
        }

        var levelSignals = new ArrayList<TurnProfileUpdate.LevelSignal>();
        if (CONFUSION_PATTERN.matcher(userInput == null ? "" : userInput).find()) {
            topics.forEach(topic -> levelSignals.add(new TurnProfileUpdate.LevelSignal(topic, TurnProfileUpdate.SignalDirection.DOWN, "student_confusion")));
        }
        if (toolAudits.stream().anyMatch(audit -> "evaluateStudentAnswer".equals(audit.toolName()) && audit.usefulForProfile())) {
            topics.forEach(topic -> levelSignals.add(new TurnProfileUpdate.LevelSignal(topic, TurnProfileUpdate.SignalDirection.DOWN, "evaluation_tool_signal")));
        }

        var interactiveSignals = questionAnswerProfileSignalService.interpret(questionInteractions);
        topics.addAll(interactiveSignals.topics());
        levelSignals.addAll(interactiveSignals.levelSignals());

        var misconceptions = detectMisconceptions(userInput, toolAudits, topics);
        var preferredLanguage = SPANISH_PATTERN.matcher(userInput == null ? "" : userInput).find() ? "es" : "en";
        boolean needsConcreteExamples = EXAMPLE_PATTERN.matcher(userInput == null ? "" : userInput).find()
                || toolAudits.stream().anyMatch(audit -> "traceCProgram".equals(audit.toolName()) && audit.usefulForProfile())
                || interactiveSignals.needsConcreteExamples();
        var confidenceDelta = BigDecimal.valueOf(levelSignals.isEmpty() ? 0.040 : -0.060)
                .add(interactiveSignals.confidenceDelta())
                .setScale(3, RoundingMode.HALF_UP);
        var recommendedHelpMode = interactiveSignals.recommendedHelpMode() != null
                ? interactiveSignals.recommendedHelpMode()
                : toolAudits.stream().anyMatch(audit -> audit.usefulForProfile())
                ? HelpMode.GUIDED
                : null;
        var toolEvidence = toolAudits.stream()
                .map(audit -> new TurnProfileUpdate.ToolEvidence(audit.toolName(), audit.usefulForProfile(), audit.outputSummary()))
                .toList();

        Map<String, Object> signalPayload = new LinkedHashMap<>();
        signalPayload.put("preferredLanguage", preferredLanguage);
        signalPayload.put("topicsDetected", topics.stream().map(Enum::name).toList());
        signalPayload.put("misconceptionsObserved", misconceptions.stream().map(TurnProfileUpdate.MisconceptionObservation::misconceptionKey).toList());
        signalPayload.put("needsConcreteExamples", needsConcreteExamples);
        signalPayload.put("toolEvidence", toolAudits.stream().map(ToolExecutionAudit::toMap).toList());
        signalPayload.putAll(interactiveSignals.payload());

        return new TurnProfileUpdate(
                conversationId,
                turnId,
                topics.stream().distinct().toList(),
                levelSignals,
                misconceptions,
                preferredLanguage,
                recommendedHelpMode,
                needsConcreteExamples,
                confidenceDelta,
                toolEvidence,
                signalPayload
        );
    }

    private List<TurnProfileUpdate.MisconceptionObservation> detectMisconceptions(String userInput, List<ToolExecutionAudit> toolAudits, List<TopicKey> topics) {
        var normalized = userInput == null ? "" : userInput.toLowerCase(Locale.ROOT);
        var observations = new ArrayList<TurnProfileUpdate.MisconceptionObservation>();

        if ((normalized.contains("contador") && normalized.contains("acumulador")) || normalized.contains("counter") && normalized.contains("accumulator")) {
            observations.add(new TurnProfileUpdate.MisconceptionObservation(
                    TopicKey.LOOPS,
                    "counter_vs_accumulator",
                    "El estudiante mezcla el rol de contador y acumulador",
                    new BigDecimal("0.820")));
        }
        if (normalized.contains("indice empieza en 1") || normalized.contains("index starts at 1")) {
            observations.add(new TurnProfileUpdate.MisconceptionObservation(
                    TopicKey.ARRAYS,
                    "array_index_origin",
                    "El estudiante asume que los arreglos en C empiezan en 1",
                    new BigDecimal("0.900")));
        }
        if (toolAudits.stream().anyMatch(audit -> audit.toolName().equals("evaluateStudentAnswer")
                && audit.outputSummary().contains("misconception=loop_condition"))) {
            observations.add(new TurnProfileUpdate.MisconceptionObservation(
                    TopicKey.LOOPS,
                    "loop_condition",
                    "El estudiante confunde la condición que mantiene activo el bucle",
                    new BigDecimal("0.760")));
        }

        if (observations.isEmpty() && topics.contains(TopicKey.LOOPS) && CONFUSION_PATTERN.matcher(normalized).find()) {
            observations.add(new TurnProfileUpdate.MisconceptionObservation(
                    TopicKey.LOOPS,
                    "loop_trace_confusion",
                    "El estudiante necesita apoyo para seguir el estado del bucle",
                    new BigDecimal("0.610")));
        }

        return observations;
    }
}
