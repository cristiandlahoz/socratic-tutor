package com.wornux.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wornux.application.evaluation.EvaluationGenerationException;
import com.wornux.application.evaluation.EvaluationQuestionGenerationService;
import com.wornux.application.subject.SubjectConfig;
import com.wornux.domain.evaluation.EvaluationEntity;
import com.wornux.domain.evaluation.EvaluationQuestionExampleEntity;
import com.wornux.domain.evaluation.EvaluationRevisionEntity;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.subject.SubjectConfigRevisionEntity;
import com.wornux.domain.subject.SubjectEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class EvaluationQuestionGenerationServiceTest {

  @Mock private ChatModel chatModel;

  @Test
  void generatesQuestionsWithTeacherExamples() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response(generatedJson()));
    var fixture = fixture();
    var service = new EvaluationQuestionGenerationService(chatModel);

    var questions =
        service.generate(
            fixture.subjectConfig(),
            fixture.revision(),
            List.of(fixture.example()),
            StudentLearningProfile.empty("es"));

    assertThat(questions).hasSize(1);
    assertThat(questions.getFirst().prompt()).contains("fresh generated prompt");
    assertThat(questions.getFirst().sourceExampleIds()).contains(fixture.example().getExampleKey());
  }

  @Test
  void generatesQuestionsWithoutTeacherExamples() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response(generatedJson()));
    var fixture = fixture();
    var service = new EvaluationQuestionGenerationService(chatModel);

    var questions =
        service.generate(
            fixture.subjectConfig(), fixture.revision(), List.of(), StudentLearningProfile.empty("es"));

    assertThat(questions).hasSize(1);
  }

  @Test
  void rejectsMalformedModelOutput() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"questions\":[]}"));
    var fixture = fixture();
    var service = new EvaluationQuestionGenerationService(chatModel);

    assertThatThrownBy(
            () ->
                service.generate(
                    fixture.subjectConfig(),
                    fixture.revision(),
                    List.of(fixture.example()),
                    StudentLearningProfile.empty("es")))
        .isInstanceOf(EvaluationGenerationException.class);
  }

  private static String generatedJson() {
    return """
        {
          "questions": [
            {
              "questionKey": "generated-1",
              "blueprintKey": "loop-guidance",
              "ordinal": 1,
              "topicKey": "loops",
              "difficulty": "foundation",
              "prompt": "fresh generated prompt",
              "options": [],
              "expectedAnswer": {"mustMention": ["state"]},
              "rubric": {"dimensions": ["trace"]},
              "sourceExampleIds": ["loop-guidance"]
            }
          ]
        }
        """;
  }

  private static ChatResponse response(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static Fixture fixture() {
    var subject = SubjectEntity.create("intro", "Intro");
    var configRevision =
        SubjectConfigRevisionEntity.create(subject, 1, Map.of("scope", "intro"), Map.of(), Map.of(), "test");
    subject.publishConfig(configRevision);
    var evaluation = EvaluationEntity.draft(subject, "diagnostic", "Diagnostic");
    var revision =
        EvaluationRevisionEntity.create(evaluation, configRevision, 1, "Generate.", Map.of(), Map.of());
    var example =
        EvaluationQuestionExampleEntity.create(
            revision, "loop-guidance", 1, "Probe loop tracing.", Map.of());
    var subjectConfig =
        new SubjectConfig(
            UUID.randomUUID(),
            "intro",
            "Intro",
            1,
            UUID.randomUUID(),
            Map.of("scope", "intro"),
            Map.of(),
            Map.of("maxQuestions", 1));
    return new Fixture(subjectConfig, revision, example);
  }

  private record Fixture(
      SubjectConfig subjectConfig,
      EvaluationRevisionEntity revision,
      EvaluationQuestionExampleEntity example) {}
}
