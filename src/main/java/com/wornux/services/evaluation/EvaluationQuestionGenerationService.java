package com.wornux.services.evaluation;

import com.wornux.services.subject.SubjectConfig;
import com.wornux.data.entities.EvaluationQuestionExample;
import com.wornux.data.entities.EvaluationRevision;
import com.wornux.domain.profile.StudentLearningProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class EvaluationQuestionGenerationService {

  private static final Logger log = LoggerFactory.getLogger(EvaluationQuestionGenerationService.class);

  private static final String SYSTEM =
      """
      Generate a short diagnostic evaluation for one student.
      Return JSON only.
      The response MUST include at least 1 diagnostic question.
      Use teacher examples only as guidance; do not copy them verbatim.
      Every question must be answerable from the subject config and revision instructions.
      If evidence is insufficient, generate broadly diagnostic questions instead of assuming level.
      """;
  private static final int MAX_EMPTY_RESULT_RETRIES = 1;

  private final ChatModel chatModel;
  private final BeanOutputConverter<GeneratedEvaluationQuestionSet> outputConverter =
      new BeanOutputConverter<>(GeneratedEvaluationQuestionSet.class);

  public EvaluationQuestionGenerationService(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  public List<GeneratedEvaluationQuestion> generate(
      SubjectConfig subject,
      EvaluationRevision revision,
      List<EvaluationQuestionExample> examples,
      StudentLearningProfile profile) {
    try {
      log.info(
          "Evaluation question generation input subjectSlug={} revisionId={} instructions={} settings={} rubric={} examples={} profile={}",
          subject.slug(),
          revision.getId(),
          revision.getInstructions(),
          revision.getSettings(),
          revision.getRubric(),
          examples == null ? List.of() : examples.stream().map(EvaluationQuestionExample::toPromptMap).toList(),
          profile);
      for (int attempt = 0; attempt <= MAX_EMPTY_RESULT_RETRIES; attempt++) {
        var result = requestQuestions(subject, revision, examples, profile, attempt);
        if (result == null || result.questions().isEmpty()) {
          if (attempt < MAX_EMPTY_RESULT_RETRIES) {
            log.warn(
                "Evaluation question generation returned no questions; retrying subjectSlug={} revisionId={} attempt={}",
                subject.slug(),
                revision.getId(),
                attempt + 1);
            continue;
          }
          throw new EvaluationGenerationException("Model returned no evaluation questions");
        }
        var validQuestions = result.questions().stream().map(this::normalize).filter(this::valid).toList();
        if (!validQuestions.isEmpty()) {
          return validQuestions;
        }
        log.error(
            "Evaluation question generation returned malformed questions subjectSlug={} revisionId={} attempt={}",
            subject.slug(),
            revision.getId(),
            attempt + 1);
        throw new EvaluationGenerationException("Model returned malformed evaluation questions");
      }
      throw new EvaluationGenerationException("Model returned no evaluation questions");
    } catch (EvaluationGenerationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.error(
          "Evaluation question generation failed subjectSlug={} revisionId={} examplesCount={}",
          subject.slug(),
          revision.getId(),
          examples == null ? 0 : examples.size(),
          exception);
      throw new EvaluationGenerationException("Evaluation question generation failed", exception);
    }
  }

  private GeneratedEvaluationQuestionSet requestQuestions(
      SubjectConfig subject,
      EvaluationRevision revision,
      List<EvaluationQuestionExample> examples,
      StudentLearningProfile profile,
      int attempt) {
    var prompt =
        Prompt.builder()
            .messages(
                new SystemMessage(SYSTEM),
                new UserMessage(
                    """
                    Subject:
                    %s

                    Evaluation instructions:
                    %s

                    Evaluation settings:
                    %s

                    Rubric:
                    %s

                    Teacher examples / blueprints:
                    %s

                    Student profile:
                    %s

                    Contract:
                    - Return JSON matching the schema exactly.
                    - questions MUST contain at least 1 item.
                    - If unsure, include one broad diagnostic starter question.

                    Required JSON format:
                    %s
                    """
                        .formatted(
                            Map.of(
                                "subjectId",
                                subject.subjectId(),
                                "slug",
                                subject.slug(),
                                "displayName",
                                subject.displayName(),
                                "configVersion",
                                subject.version(),
                                "config",
                                subject.config(),
                                "questionPolicy",
                                subject.questionPolicy()),
                            revision.getInstructions(),
                            revision.getSettings(),
                            revision.getRubric(),
                            examples.stream().map(EvaluationQuestionExample::toPromptMap).toList(),
                            profile,
                            outputConverter.getFormat())))
            .chatOptions(
                OllamaChatOptions.builder()
                    .temperature(0.45)
                    .format(outputConverter.getJsonSchemaMap())
                    .build())
            .build();
    var raw = chatModel.call(prompt).getResult().getOutput().getText();
    log.info(
        "Evaluation question generation raw output subjectSlug={} revisionId={} examplesCount={} attempt={} raw={}",
        subject.slug(),
        revision.getId(),
        examples == null ? 0 : examples.size(),
        attempt + 1,
        raw);
    return outputConverter.convert(raw);
  }

  public GeneratedEvaluationQuestion generateNextQuestion(
      SubjectConfig subject,
      EvaluationRevision revision,
      List<EvaluationQuestionExample> examples,
      StudentLearningProfile profile,
      CurrentModeTurnContext turnContext) {
    int nextOrdinal = turnContext.nextOrdinal();
    List<Map<String, Object>> priorAnswers = turnContext.priorQaEvidence();
    int maxQuestions = turnContext.maxQuestions();
    var generated = generate(subject, revision, examples, profile);
    return generated.stream()
        .filter(question -> question.ordinal() == nextOrdinal)
        .findFirst()
        .orElseGet(
            () -> {
              var source = generated.getFirst();
              return new GeneratedEvaluationQuestion(
                  source.questionKey() + "-turn-" + nextOrdinal,
                  source.blueprintKey(),
                  nextOrdinal,
                  source.topicKey(),
                  source.difficulty(),
                  source.prompt(),
                  source.options(),
                  source.expectedAnswer(),
                  Map.of(
                      "priorAnswers", priorAnswers == null ? List.of() : priorAnswers,
                      "maxQuestions", maxQuestions,
                       "generationMode", "adaptive-next-turn",
                       "mode", turnContext.mode(),
                       "completionIntent", turnContext.completionIntent(),
                       "answeredCount", turnContext.answeredCount()),
                   source.sourceExampleIds());
             });
  }

  private boolean valid(GeneratedEvaluationQuestion question) {
    return question != null
        && question.ordinal() > 0
        && hasText(question.questionKey())
        && hasText(question.blueprintKey())
        && hasText(question.prompt());
  }

  private GeneratedEvaluationQuestion normalize(GeneratedEvaluationQuestion question) {
    if (question == null) {
      return null;
    }
    var blueprintKey =
        hasText(question.blueprintKey())
            ? question.blueprintKey().trim()
            : hasText(question.questionKey()) ? question.questionKey().trim() : "";
    return new GeneratedEvaluationQuestion(
        question.questionKey(),
        blueprintKey,
        question.ordinal(),
        question.topicKey(),
        question.difficulty(),
        normalizeSocraticPrompt(question.prompt(), question.options()),
        List.of(),
          question.expectedAnswer(),
          question.rubric(),
          question.sourceExampleIds());
  }

  private String normalizeSocraticPrompt(String prompt, List<String> options) {
    var normalizedPrompt = prompt == null ? "" : prompt.trim();
    var normalizedOptions = options == null ? List.<String>of() : options;
    if (looksLikeMetaPrompt(normalizedPrompt)) {
      var fromOptions = firstDirectQuestion(normalizedOptions);
      if (fromOptions != null) {
        return fromOptions;
      }
      return "";
    }
    var singleQuestion = firstDirectQuestion(List.of(normalizedPrompt));
    return singleQuestion == null ? normalizedPrompt : singleQuestion;
  }

  private boolean looksLikeMetaPrompt(String prompt) {
    if (!hasText(prompt)) {
      return false;
    }
    var lowered = prompt.toLowerCase(Locale.ROOT);
    return lowered.contains("elige una de las siguientes opciones")
        || lowered.contains("choose one of the following options")
        || lowered.contains("selecciona una de las siguientes opciones")
        || lowered.contains("select one of the following options");
  }

  private String firstDirectQuestion(List<String> candidates) {
    var extracted = new ArrayList<String>();
    for (var candidate : candidates) {
      if (!hasText(candidate)) {
        continue;
      }
      var text = candidate.trim();
      if (text.endsWith("?")) {
        extracted.add(text);
      }
      int closingQuestion = text.indexOf('?');
      if (closingQuestion > 0) {
        var maybeQuestion = text.substring(0, closingQuestion + 1).trim();
        if (hasText(maybeQuestion) && maybeQuestion.endsWith("?")) {
          extracted.add(maybeQuestion);
        }
      }
    }
    return extracted.stream().filter(EvaluationQuestionGenerationService::hasText).findFirst().orElse(null);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
