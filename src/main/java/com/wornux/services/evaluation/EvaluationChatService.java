package com.wornux.services.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.Evaluation;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

  private final Map<UUID, EvaluationSession> sessions = new ConcurrentHashMap<>();
  private final ChatModel chatModel;
  private final EvaluationService evaluationService;
  private final EvaluationQuestionGenerationService questionGenerationService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BeanOutputConverter<FeedbackResponse> feedbackConverter =
      new BeanOutputConverter<>(FeedbackResponse.class);
  private final BeanOutputConverter<ReportResponse> reportConverter =
      new BeanOutputConverter<>(ReportResponse.class);

  public EvaluationChatService(
      ChatModel chatModel,
      EvaluationService evaluationService,
      EvaluationQuestionGenerationService questionGenerationService) {
    this.chatModel = chatModel;
    this.evaluationService = evaluationService;
    this.questionGenerationService = questionGenerationService;
  }

  public EvaluationTurnResponse startSession(UUID evaluationId) {
    var evaluation = evaluationService.get(evaluationId);
    var questions = questionGenerationService.fromJson(evaluation.getQuestionsJson());

    var session = new EvaluationSession(evaluation, questions);
    sessions.put(evaluationId, session);

    var firstQuestion = questions.getFirst();

    return new EvaluationTurnResponse(
        TurnType.QUESTION,
        firstQuestion.questionText(),
        firstQuestion.questionKey(),
        null);
  }

  public EvaluationTurnResponse processAnswer(UUID evaluationId, String answer) {
    var session = sessions.get(evaluationId);
    if (session == null) {
      throw new IllegalStateException("No active evaluation session for " + evaluationId);
    }

    var currentQuestion = session.questions.get(session.currentIndex);
    var answerRecord = new AnswerRecord(
        currentQuestion.questionKey(),
        currentQuestion.questionText(),
        answer);
    session.answers.add(answerRecord);

    session.currentIndex++;

    if (session.currentIndex < session.questions.size()) {
      var feedback = generateFeedback(session, currentQuestion, answer);

      var nextQuestion = session.questions.get(session.currentIndex);

      return new EvaluationTurnResponse(
          TurnType.QUESTION,
          feedback + "\n\n**Siguiente pregunta:**\n" + nextQuestion.questionText(),
          nextQuestion.questionKey(),
          null);
    }

    try {
      var report = generateReport(session);
      var answersJson = objectMapper.writeValueAsString(session.answers);

      evaluationService.saveAnswers(session.evaluation.getId(), answersJson);
      evaluationService.completeReport(session.evaluation.getId(), report);

      sessions.remove(evaluationId);

      return new EvaluationTurnResponse(
          TurnType.COMPLETE,
          "## Evaluación Completada\n\n" + report,
          null,
          report);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize answers to JSON", e);
    }
  }

  public EvaluationSession getSession(UUID evaluationId) {
    return sessions.get(evaluationId);
  }

  public void clearSession(UUID evaluationId) {
    sessions.remove(evaluationId);
  }

  private String generateFeedback(EvaluationSession session, GeneratedQuestion question, String answer) {
    var historyBuilder = new StringBuilder();
    for (int i = 0; i < session.answers.size() - 1; i++) {
      var prev = session.answers.get(i);
      historyBuilder.append("Pregunta %d: %s\nRespuesta: %s\n\n".formatted(i + 1, prev.questionText(), prev.answer()));
    }

    var feedbackPrompt = FEEDBACK_PROMPT.formatted(
        session.evaluation.getInstruction(),
        session.currentIndex,
        session.questions.size(),
        question.questionText(),
        answer,
        historyBuilder.toString());

    var prompt = Prompt.builder()
        .messages(new SystemMessage(feedbackPrompt))
        .chatOptions(OllamaChatOptions.builder()
            .temperature(0.3)
            .format(feedbackConverter.getJsonSchemaMap())
            .build())
        .build();

    var response = chatModel.call(prompt);
    var content = response.getResult().getOutput().getText();
    var feedback = feedbackConverter.convert(content);

    log.info("Generated feedback for question {} of evaluation {}",
        session.currentIndex, session.evaluation.getId());

    return feedback.feedback();
  }

  private String generateReport(EvaluationSession session) {
    var pairsBuilder = new StringBuilder();
    for (int i = 0; i < session.answers.size(); i++) {
      var a = session.answers.get(i);
      pairsBuilder.append("%d. **%s**\n   Respuesta: %s\n\n".formatted(i + 1, a.questionText(), a.answer()));
    }

    var reportPrompt = REPORT_PROMPT.formatted(
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

    log.info("Generated report for evaluation {}", session.evaluation.getId());

    return report.report();
  }

  public static class EvaluationSession {
    public final Evaluation evaluation;
    public final List<GeneratedQuestion> questions;
    public int currentIndex;
    public final List<AnswerRecord> answers;

    public EvaluationSession(Evaluation evaluation, List<GeneratedQuestion> questions) {
      this.evaluation = evaluation;
      this.questions = questions;
      this.currentIndex = 0;
      this.answers = new ArrayList<>();
    }
  }

  public record AnswerRecord(String questionKey, String questionText, String answer) {}

  public record EvaluationTurnResponse(TurnType type, String message, String questionKey, String reportMarkdown) {}

  public enum TurnType { QUESTION, COMPLETE }

  record FeedbackResponse(String feedback) {}

  record ReportResponse(String report) {}

  private static final String FEEDBACK_PROMPT = """
      Eres un evaluador académico. Tu tarea es dar feedback breve y constructivo
      sobre la respuesta del estudiante a una pregunta de diagnóstico.

      Instrucción de la evaluación: %s

      Pregunta %d de %d: %s
      Respuesta del estudiante: %s

      Historial de respuestas anteriores:
      %s

      Da un feedback de 2-3 líneas que:
      - Señale si la respuesta es correcta, parcial o incorrecta
      - Corrija suavemente si hay errores
      - Destaque aciertos si los hay
      - Sea alentador y constructivo

      Devuelve SOLO JSON: {"feedback": "texto del feedback aquí"}
      """;

  private static final String REPORT_PROMPT = """
      Eres un evaluador académico. Genera un reporte de evaluación completo
      en formato markdown.

      Instrucción de la evaluación: %s
      Título: %s

      Todas las preguntas y respuestas del estudiante:
      %s

      El reporte DEBE incluir estas secciones:

      ## Resumen
      [Breve resumen del desempeño general]

      ## Fortalezas
      [Qué conceptos domina bien el estudiante]

      ## Áreas de mejora
      [Qué conceptos necesita reforzar]

      ## Conclusión
      [Nota general y recomendaciones]

      Devuelve SOLO JSON: {"report": "markdown completo aquí"}
      """;
}
