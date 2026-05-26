package com.wornux.services.evaluation;

import com.wornux.services.subject.SubjectConfig;
import com.wornux.data.entities.EvaluationQuestionExample;
import com.wornux.data.entities.EvaluationRevision;
import com.wornux.domain.profile.StudentLearningProfile;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class EvaluationQuestionGenerationService {

  private static final String SYSTEM =
      """
      Generate a short diagnostic evaluation for one student.
      Return JSON only.
      Use teacher examples only as guidance; do not copy them verbatim.
      Every question must be answerable from the subject config and revision instructions.
      If evidence is insufficient, generate broadly diagnostic questions instead of assuming level.
      """;

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
      var result = outputConverter.convert(raw);
      if (result == null || result.questions().isEmpty()) {
        throw new EvaluationGenerationException("Model returned no evaluation questions");
      }
      var validQuestions = result.questions().stream().filter(this::valid).toList();
      if (validQuestions.isEmpty()) {
        throw new EvaluationGenerationException("Model returned malformed evaluation questions");
      }
      return validQuestions;
    } catch (EvaluationGenerationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new EvaluationGenerationException("Evaluation question generation failed", exception);
    }
  }

  private boolean valid(GeneratedEvaluationQuestion question) {
    return question != null
        && question.ordinal() > 0
        && hasText(question.questionKey())
        && hasText(question.blueprintKey())
        && hasText(question.prompt());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
