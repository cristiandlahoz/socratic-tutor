package com.wornux.services.evaluation;

import com.wornux.ai.prompt.EvaluationPromptResources;
import com.wornux.data.entities.Evaluation;
import com.wornux.data.entities.EvaluationRun;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class EvaluationChatService {

  private static final Logger log = LoggerFactory.getLogger(EvaluationChatService.class);

  private final ConcurrentHashMap<UUID, EvaluationSession> sessions = new ConcurrentHashMap<>();
  private final ChatModel chatModel;
  private final EvaluationRunService runService;
  private final EvaluationService evaluationService;
  private final EvaluationPromptResources promptResources;
  private final BeanOutputConverter<NextTurnResponse> nextTurnConverter =
      new BeanOutputConverter<>(NextTurnResponse.class);
  private final BeanOutputConverter<ReportResponse> reportConverter =
      new BeanOutputConverter<>(ReportResponse.class);

  static final Pattern BLOCKED_PATTERN = Pattern.compile(
      "\\b(no sé|no entiendo|no lo sé|no tengo idea|no comprendo|no lo entiendo|no sé nada)\\b",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

  static final int MIN_QUESTIONS = 2;
  static final int MAX_QUESTIONS = 8;
  static final int FALLBACK_MAX_RETRIES = 3;

  public EvaluationChatService(
      ChatModel chatModel,
      EvaluationRunService runService,
      EvaluationService evaluationService,
      EvaluationPromptResources promptResources) {
    this.chatModel = chatModel;
    this.runService = runService;
    this.evaluationService = evaluationService;
    this.promptResources = promptResources;
  }

  public EvaluationTurnResponse startSession(UUID runId) {
    var run = runService.loadRun(runId);
    var evaluation = evaluationService.get(run.getEvaluationId());

    var session = new EvaluationSession(run, evaluation);
    sessions.put(runId, session);

    var firstQuestion = generateNextQuestion(session);

    return new EvaluationTurnResponse(
        TurnType.QUESTION,
        firstQuestion.questionText(),
        firstQuestion.questionKey(),
        null);
  }

  public EvaluationTurnResponse processAnswer(UUID runId, String answer) {
    var session = sessions.get(runId);
    if (session == null) {
      throw new IllegalStateException("No active evaluation session for run " + runId);
    }

    var lastQuestion = session.questions.getLast();
    session.answers.add(new AnswerRecord(
        lastQuestion.questionKey(), lastQuestion.questionText(), answer));

    if (session.questions.size() >= MAX_QUESTIONS) {
      return completeEvaluation(session, runId);
    }

    try {
      var decision = decideNextTurn(session);

      if ("question".equals(decision.type())
          && decision.questionText() != null
          && !decision.questionText().isBlank()) {

        if (isTooSimilarToAnyAsked(decision.questionText(), session)) {
          log.info("Model question too similar to a previous one; forcing fallback");
          var forced = generateFallbackQuestion(session);
          session.questions.add(forced);
          persistState(session, runId);
          return new EvaluationTurnResponse(
              TurnType.QUESTION, forced.questionText(), forced.questionKey(), null);
        }

        var nextQuestion = new GeneratedQuestion(decision.questionText(), nextKey(session));
        session.questions.add(nextQuestion);
        persistState(session, runId);

        return new EvaluationTurnResponse(
            TurnType.QUESTION, nextQuestion.questionText(), nextQuestion.questionKey(), null);
      }

      if (session.questions.size() < MIN_QUESTIONS) {
        var forced = generateFallbackQuestion(session);
        session.questions.add(forced);
        persistState(session, runId);
        return new EvaluationTurnResponse(
            TurnType.QUESTION, forced.questionText(), forced.questionKey(), null);
      }
    } catch (Exception e) {
      log.warn("Adaptive next-turn model failed, forcing question or completing: {}", e.getMessage());
      if (session.questions.size() < MIN_QUESTIONS) {
        var forced = generateFallbackQuestion(session);
        session.questions.add(forced);
        persistState(session, runId);
        return new EvaluationTurnResponse(
            TurnType.QUESTION, forced.questionText(), forced.questionKey(), null);
      }
    }

    return completeEvaluation(session, runId);
  }

  public EvaluationSession getSession(UUID runId) {
    return sessions.get(runId);
  }

  public void clearSession(UUID runId) {
    sessions.remove(runId);
  }

  private static String nextKey(EvaluationSession session) {
    return "q" + (session.questions.size() + 1);
  }

  private GeneratedQuestion generateNextQuestion(EvaluationSession session) {
    var decision = decideNextTurn(session);
    if (!"question".equals(decision.type())
        || decision.questionText() == null
        || decision.questionText().isBlank()) {
      throw new IllegalStateException("El modelo no generó una pregunta de diagnóstico");
    }
    var gq = new GeneratedQuestion(decision.questionText(), nextKey(session));
    session.questions.add(gq);
    return gq;
  }

  private GeneratedQuestion generateFallbackQuestion(EvaluationSession session) {
    var history = buildConversationHistory(session);
    var fallbackPrompt = promptResources.fallbackQuestion().formatted(
        session.evaluation.getInstruction(),
        session.evaluation.getTitle(),
        history);
    for (int attempt = 0; attempt < FALLBACK_MAX_RETRIES; attempt++) {
      var prompt = Prompt.builder()
          .messages(new SystemMessage(fallbackPrompt))
          .chatOptions(OllamaChatOptions.builder()
              .temperature(Math.min(0.3 + attempt * 0.15, 0.7))
              .format(nextTurnConverter.getJsonSchemaMap())
              .build())
          .build();
      var response = chatModel.call(prompt);
      var decision = nextTurnConverter.convert(response.getResult().getOutput().getText());
      var text = decision.questionText() != null && !decision.questionText().isBlank()
          ? decision.questionText().trim()
          : null;
      if (text != null && !isTooSimilarToAnyAsked(text, session)) {
        return new GeneratedQuestion(text, nextKey(session));
      }
      log.warn("Fallback attempt {} produced too-similar or blank question; retrying", attempt + 1);
      if (attempt < FALLBACK_MAX_RETRIES - 1) {
        fallbackPrompt += "\n\nADVERTENCIA: La(s) pregunta(s) anterior(es) era(n) demasiado similar(es) a preguntas ya realizadas. Generá una pregunta COMPLETAMENTE DIFERENTE sobre un aspecto no cubierto previamente.\n";
      }
    }
    log.warn("All {} fallback retries exhausted for session {}; forcing generic question",
        FALLBACK_MAX_RETRIES, session.run.getId());
    var forcedText = "¿Podés explicar con tus palabras qué entendés sobre \""
        + session.evaluation.getTitle() + "\"?";
    return new GeneratedQuestion(forcedText, nextKey(session));
  }

  private NextTurnResponse decideNextTurn(EvaluationSession session) {
    var history = buildConversationHistory(session);
    var antiLoopSegment = shouldInjectAntiLoop(session)
        ? promptResources.antiLoopBlocked()
        : "";
    var promptText = promptResources.adaptivePrompt().formatted(
        session.evaluation.getInstruction(),
        session.evaluation.getTitle(),
        history,
        antiLoopSegment);

    var prompt = Prompt.builder()
        .messages(new SystemMessage(promptText))
        .chatOptions(OllamaChatOptions.builder()
            .temperature(0.3)
            .format(nextTurnConverter.getJsonSchemaMap())
            .build())
        .build();

    var response = chatModel.call(prompt);
    return nextTurnConverter.convert(response.getResult().getOutput().getText());
  }

  static String buildConversationHistory(EvaluationSession session) {
    if (session.answers.isEmpty()) return "";
    var sb = new StringBuilder("Historial de preguntas y respuestas:\n");
    for (int i = 0; i < session.answers.size(); i++) {
      var q = session.questions.get(i);
      var a = session.answers.get(i);
      sb.append("Pregunta %d: %s\nRespuesta %d: %s\n\n".formatted(i + 1, q.questionText(), i + 1, a.answer()));
    }
    return sb.toString();
  }

  static boolean shouldInjectAntiLoop(EvaluationSession session) {
    if (session.answers.isEmpty()) return false;
    var latestAnswer = session.answers.getLast().answer();
    return BLOCKED_PATTERN.matcher(latestAnswer).find();
  }

  static boolean isTooSimilarToAnyAsked(String candidate, EvaluationSession session) {
    var candidateWords = tokenize(candidate);
    if (candidateWords.isEmpty()) return false;
    for (var q : session.questions) {
      var prevWords = tokenize(q.questionText());
      if (prevWords.isEmpty()) continue;
      var intersection = new HashSet<>(candidateWords);
      intersection.retainAll(prevWords);
      var union = new HashSet<>(candidateWords);
      union.addAll(prevWords);
      double jaccard = (double) intersection.size() / union.size();
      if (jaccard > 0.55) return true;
    }
    return false;
  }

  static Set<String> tokenize(String text) {
    var words = new HashSet<>(List.of(text.toLowerCase().split("[\\s¿?¡!.,;:()\"'«»-]+")));
    words.removeIf(w -> w.length() < 3 || w.isBlank());
    return words;
  }

  private void persistState(EvaluationSession session, UUID runId) {
    runService.persistConversation(runId, session.questions, session.answers);
    evaluationService.saveAnswers(
        session.evaluation.getId(),
        serializeAnswers(session.answers));
  }

  private EvaluationTurnResponse completeEvaluation(EvaluationSession session, UUID runId) {
    try {
      var report = generateReport(session);
      evaluationService.saveAnswers(
          session.evaluation.getId(),
          serializeAnswers(session.answers));
      evaluationService.completeReport(session.evaluation.getId(), report);
      runService.completeReport(runId, report);
      sessions.remove(runId);
      return new EvaluationTurnResponse(
          TurnType.COMPLETE, "¡Actividad formativa completada!", null, report);
    } catch (Exception e) {
      sessions.remove(runId);
      throw new RuntimeException("Error al completar la evaluación", e);
    }
  }

  private String generateReport(EvaluationSession session) {
    var pairsBuilder = new StringBuilder();
    for (int i = 0; i < session.answers.size(); i++) {
      var a = session.answers.get(i);
      pairsBuilder.append("%d. **%s**\n   Respuesta: %s\n\n".formatted(
          i + 1, a.questionText(), a.answer()));
    }

    var reportPrompt = promptResources.reportPrompt().formatted(
        session.evaluation.getInstruction(),
        session.evaluation.getTitle(),
        pairsBuilder.toString());

    var prompt = Prompt.builder()
        .messages(new SystemMessage(reportPrompt))
        .chatOptions(OllamaChatOptions.builder()
            .temperature(0.3)
            .format(reportConverter.getJsonSchemaMap())
            .build())
        .build();

    var response = chatModel.call(prompt);
    var content = response.getResult().getOutput().getText();
    var report = reportConverter.convert(content);

    log.info("Generated report for run {}", session.run.getId());

    return report.report();
  }

  public static class EvaluationSession {
    public final EvaluationRun run;
    public final Evaluation evaluation;
    public final List<GeneratedQuestion> questions;
    public final List<AnswerRecord> answers;

    public EvaluationSession(EvaluationRun run, Evaluation evaluation) {
      this.run = run;
      this.evaluation = evaluation;
      this.questions = new ArrayList<>();
      this.answers = new ArrayList<>();
    }
  }

  public record AnswerRecord(String questionKey, String questionText, String answer) {}

  public record EvaluationTurnResponse(TurnType type, String message, String questionKey, String reportMarkdown) {}

  public enum TurnType { QUESTION, COMPLETE }

  record NextTurnResponse(String type, String questionText) {}

  record ReportResponse(String report) {}

  private String serializeAnswers(List<AnswerRecord> answers) {
    var json = new StringBuilder("[");
    for (int i = 0; i < answers.size(); i++) {
      var answer = answers.get(i);
      if (i > 0) {
        json.append(',');
      }
      json.append('{')
          .append("\"questionKey\":\"").append(escapeJson(answer.questionKey())).append("\",")
          .append("\"questionText\":\"").append(escapeJson(answer.questionText())).append("\",")
          .append("\"answer\":\"").append(escapeJson(answer.answer())).append("\"}");
    }
    return json.append(']').toString();
  }

  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
