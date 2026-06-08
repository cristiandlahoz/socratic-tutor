package com.wornux.services.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wornux.services.evaluation.EvaluationChatService.AnswerRecord;
import com.wornux.services.evaluation.EvaluationChatService.EvaluationSession;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationChatServiceTest {

  @Nested
  class Tokenize {

    @Test
    void splitsOnPunctuationAndSpaces() {
      var result = EvaluationChatService.tokenize("¿Qué es una variable?");
      assertTrue(result.contains("qué"));
      assertTrue(result.contains("variable"));
    }

    @Test
    void removesShortWords() {
      var result = EvaluationChatService.tokenize("el perro y el gato");
      assertTrue(result.contains("perro"));
      assertTrue(result.contains("gato"));
      assertFalse(result.contains("el"));
      assertFalse(result.contains("y"));
    }

    @Test
    void lowercasesAllWords() {
      var result = EvaluationChatService.tokenize("HOLA MUNDO");
      assertTrue(result.contains("hola"));
      assertTrue(result.contains("mundo"));
    }

    @Test
    void handlesEmptyString() {
      var result = EvaluationChatService.tokenize("");
      assertEquals(Set.of(), result);
    }
  }

  @Nested
  class TooSimilar {

    @Test
    void returnsFalseForEmptyCandidate() {
      var session = sessionWithQuestions("¿Qué es una variable?");
      assertFalse(EvaluationChatService.isTooSimilarToAnyAsked("", session));
    }

    @Test
    void returnsTrueWhenJaccardExceedsThreshold() {
      var session = sessionWithQuestions("¿Qué es una variable en programación?");
      assertTrue(EvaluationChatService.isTooSimilarToAnyAsked(
          "Decime qué es una variable", session));
    }

    @Test
    void returnsFalseWhenDifferentEnough() {
      var session = sessionWithQuestions("¿Qué es un bucle for?");
      assertFalse(EvaluationChatService.isTooSimilarToAnyAsked(
          "Explicame qué es un puntero", session));
    }

    @Test
    void returnsTrueForExactDuplicate() {
      var session = sessionWithQuestions("¿Qué es una variable en programación?");
      assertTrue(EvaluationChatService.isTooSimilarToAnyAsked(
          "¿Qué es una variable en programación?", session));
    }

    @Test
    void checksAgainstAllPreviousQuestionsNotJustLast() {
      var session = sessionWithQuestions(
          "¿Qué es un bucle for?",
          "Explicame qué es un puntero");
      assertTrue(EvaluationChatService.isTooSimilarToAnyAsked(
          "Explicame qué es un bucle for", session));
    }
  }

  @Nested
  class BuildConversationHistory {

    @Test
    void returnsEmptyForNoAnswers() {
      var session = new EvaluationSession(null, null);
      assertEquals("", EvaluationChatService.buildConversationHistory(session));
    }

    @Test
    void formatsQuestionsAndAnswers() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es una variable?", "Un lugar en memoria");
      addAnswer(session, "q2", "¿Qué es un puntero?", "Una dirección");

      var result = EvaluationChatService.buildConversationHistory(session);
      assertTrue(result.contains("Pregunta 1"));
      assertTrue(result.contains("¿Qué es una variable?"));
      assertTrue(result.contains("Un lugar en memoria"));
      assertTrue(result.contains("Pregunta 2"));
      assertTrue(result.contains("¿Qué es un puntero?"));
      assertTrue(result.contains("Una dirección"));
    }
  }

  @Nested
  class ShouldInjectAntiLoop {

    @Test
    void returnsFalseWhenNoAnswers() {
      var session = new EvaluationSession(null, null);
      assertFalse(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void returnsTrueForNoSe() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "no sé");
      assertTrue(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void returnsTrueForNoEntiendo() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "no entiendo el concepto");
      assertTrue(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void returnsTrueForNoTengoIdea() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "no tengo idea de qué es");
      assertTrue(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void returnsFalseForNormalAnswer() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "es un lugar en memoria que guarda valores");
      assertFalse(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void checksOnlyLatestAnswer() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "es un lugar en memoria");
      addAnswer(session, "q2", "¿Qué es Y?", "no sé");
      assertTrue(EvaluationChatService.shouldInjectAntiLoop(session));
    }

    @Test
    void doesNotMatchPartialWords() {
      var session = new EvaluationSession(null, null);
      addAnswer(session, "q1", "¿Qué es X?", "no tengo");
      assertFalse(EvaluationChatService.shouldInjectAntiLoop(session));
    }
  }

  private static EvaluationSession sessionWithQuestions(String... texts) {
    var session = new EvaluationSession(null, null);
    for (int i = 0; i < texts.length; i++) {
      var key = "q" + (i + 1);
      session.questions.add(
          new com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion(
              texts[i], key));
    }
    return session;
  }

  private static void addAnswer(EvaluationSession session, String key, String question, String answer) {
    session.questions.add(
        new com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion(
            question, key));
    session.answers.add(new AnswerRecord(key, question, answer));
  }
}
