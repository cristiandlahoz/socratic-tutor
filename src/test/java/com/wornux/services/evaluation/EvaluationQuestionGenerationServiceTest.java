package com.wornux.services.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.entities.EvaluationRevision;
import com.wornux.data.entities.Subject;
import com.wornux.data.entities.SubjectConfigRevision;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.services.subject.SubjectConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class EvaluationQuestionGenerationServiceTest {

  @Test
  void retriesOnceWhenModelReturnsEmptyQuestionsThenReturnsValidQuestion() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(response("{\"questions\":[]}"))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":"q1","blueprintKey":"bp1","ordinal":1,"topicKey":"topic","difficulty":"easy","prompt":"What is an algorithm?","options":[],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    var questions =
        service.generate(
            subject(),
            revision(),
            List.of(),
            StudentLearningProfile.empty("es"));

    assertThat(questions).hasSize(1);
    verify(chatModel, times(2)).call(any(Prompt.class));
  }

  @Test
  void failsWhenModelStillReturnsEmptyQuestionsAfterRetry() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(response("{\"questions\":[]}"))
        .thenReturn(response("{\"questions\":[]}"));

    assertThatThrownBy(
            () -> service.generate(subject(), revision(), List.of(), StudentLearningProfile.empty("es")))
        .isInstanceOf(EvaluationGenerationException.class)
        .hasMessageContaining("no evaluation questions");
    verify(chatModel, times(2)).call(any(Prompt.class));
  }

  @Test
  void fillsBlankBlueprintKeyFromQuestionKeyWhenOtherRequiredFieldsArePresent() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":"q-derivatives-1","blueprintKey":"","ordinal":1,"topicKey":"calculo","difficulty":"medium","prompt":"Explicá derivada en un punto.","options":[],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    var questions = service.generate(subject(), revision(), List.of(), StudentLearningProfile.empty("es"));

    assertThat(questions).hasSize(1);
    assertThat(questions.getFirst().blueprintKey()).isEqualTo("q-derivatives-1");
    verify(chatModel).call(any(Prompt.class));
  }

  @Test
  void failsWhenBlueprintAndQuestionKeyAreBlankEvenIfOtherFieldsExist() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":" ","blueprintKey":"","ordinal":1,"topicKey":"calculo","difficulty":"medium","prompt":"Explicá derivada en un punto.","options":[],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    assertThatThrownBy(
            () -> service.generate(subject(), revision(), List.of(), StudentLearningProfile.empty("es")))
        .isInstanceOf(EvaluationGenerationException.class)
        .hasMessageContaining("malformed evaluation questions");
  }

  @Test
  void normalizesMetaPromptIntoSingleDirectQuestionAndClearsOptions() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":"q-meta-1","blueprintKey":"bp-meta-1","ordinal":1,"topicKey":"logica","difficulty":"easy","prompt":"Elige una de las siguientes opciones para continuar.","options":["¿Cómo justificarías tu respuesta?","¿Qué evidencia usarías?"],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    var questions = service.generate(subject(), revision(), List.of(), StudentLearningProfile.empty("es"));

    assertThat(questions).hasSize(1);
    assertThat(questions.getFirst().prompt()).isEqualTo("¿Cómo justificarías tu respuesta?");
    assertThat(questions.getFirst().options()).isEmpty();
  }

  @Test
  void rejectsMetaPromptWhenNoUsableDirectQuestionExists() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":"q-meta-2","blueprintKey":"bp-meta-2","ordinal":1,"topicKey":"logica","difficulty":"easy","prompt":"Elige una de las siguientes opciones.","options":["Opción A","Opción B"],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    assertThatThrownBy(
            () -> service.generate(subject(), revision(), List.of(), StudentLearningProfile.empty("es")))
        .isInstanceOf(EvaluationGenerationException.class)
        .hasMessageContaining("malformed evaluation questions");
  }

  @Test
  void generateNextQuestionUsesExplicitCurrentModeTurnContext() {
    var chatModel = org.mockito.Mockito.mock(ChatModel.class);
    var service = new EvaluationQuestionGenerationService(chatModel);
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                """
                {"questions":[{"questionKey":"q1","blueprintKey":"bp1","ordinal":1,"topicKey":"topic","difficulty":"easy","prompt":"¿Cómo lo resolverías?","options":[],"expectedAnswer":{},"rubric":{},"sourceExampleIds":[]}]}
                """));

    var next =
        service.generateNextQuestion(
            subject(),
            revision(),
            List.of(),
            StudentLearningProfile.empty("es"),
            new CurrentModeTurnContext(
                "SOCRATIC_FREE_TEXT",
                2,
                3,
                List.of(Map.of("questionId", "q1", "answer", "respuesta larga")),
                "CONTINUE",
                5));

    assertThat(next.ordinal()).isEqualTo(3);
    assertThat(next.rubric()).containsEntry("mode", "SOCRATIC_FREE_TEXT");
    assertThat(next.rubric()).containsEntry("completionIntent", "CONTINUE");
    assertThat(next.rubric()).containsEntry("answeredCount", 2);
  }

  private static SubjectConfig subject() {
    return new SubjectConfig(
        UUID.randomUUID(), "introduccion-algoritmia", "Algoritmia", 1, UUID.randomUUID(), Map.of(), Map.of(), Map.of());
  }

  private static EvaluationRevision revision() {
    var subject = org.mockito.Mockito.mock(Subject.class);
    when(subject.getSlug()).thenReturn("introduccion-algoritmia");
    var subjectRevision = org.mockito.Mockito.mock(SubjectConfigRevision.class);
    when(subjectRevision.getSubject()).thenReturn(subject);

    var evaluation = org.mockito.Mockito.mock(Evaluation.class);
    when(evaluation.getSubject()).thenReturn(subject);

    var revision = org.mockito.Mockito.mock(EvaluationRevision.class);
    when(revision.getId()).thenReturn(UUID.randomUUID());
    when(revision.getInstructions()).thenReturn("Keep it diagnostic");
    when(revision.getSettings()).thenReturn(Map.of());
    when(revision.getRubric()).thenReturn(Map.of());
    when(revision.getSubjectConfigRevision()).thenReturn(subjectRevision);
    when(revision.getEvaluation()).thenReturn(evaluation);
    return revision;
  }

  private static ChatResponse response(String raw) {
    return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(raw)))).build();
  }
}
