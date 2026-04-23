package com.wornux.chat.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ViewPackages(classes = StudentQuestionPanel.class)
class StudentQuestionPanelTest extends BrowserlessTest {

  private final AtomicReference<StudentQuestionResponse> submittedResponse =
      new AtomicReference<>();
  private StudentQuestionPanel panel;

  @BeforeEach
  void setUp() {
    panel = new StudentQuestionPanel();
    panel.setSubmitHandler(submittedResponse::set);
    UI.getCurrent().add(panel);
  }

  @Test
  void singleSelect_replaces_previous_choice_and_submits_payload() {
    panel.setQuestionSet(
        questionSet(
            singleSelectQuestion(
                "q1",
                "Ayuda",
                "Como prefieres que te ayude?",
                option("Paso a paso", "Te guio paso a paso."),
                option("Pista breve", "Te doy una pista corta.")),
            singleSelectQuestion(
                "q2",
                "Contexto",
                "Que mas necesitas?",
                option("Ejemplo", "Quieres ver un ejemplo."),
                option("Resumen", "Quieres un resumen."))));

    test(panel.optionButton("q1", "Paso a paso")).click();
    test(panel.optionButton("q1", "Pista breve")).click();

    assertThat(panel.isOptionSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.isOptionSelected("q1", "Pista breve")).isTrue();
    assertThat(panel.isOptionRowSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.isOptionRowSelected("q1", "Pista breve")).isTrue();
    assertThat(panel.isOptionButtonSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.isOptionButtonSelected("q1", "Pista breve")).isTrue();

    test(panel.nextButton()).click();
    test(panel.customTextArea("q2")).setValue("Necesito un ejemplo concreto");

    assertThat(panel.submitButton().isEnabled()).isTrue();

    test(panel.submitButton()).click();

    assertThat(submittedResponse.get()).isNotNull();
    assertThat(submittedResponse.get().answers())
        .containsExactly(
            new StudentQuestionAnswer("q1", List.of("Pista breve"), ""),
            new StudentQuestionAnswer("q2", List.of(), "Necesito un ejemplo concreto"));
  }

  @Test
  void multiSelect_submits_all_selected_labels() {
    panel.setQuestionSet(
        questionSet(
            multiSelectQuestion(
                "q1",
                "Tema",
                "Que te esta frenando ahora mismo?",
                option("Loops", "Te cuesta entender loops."),
                option("Funciones", "Te cuesta entender funciones."),
                option("Arreglos", "Te cuesta entender arreglos."))));

    test(panel.optionButton("q1", "Loops")).click();
    test(panel.optionButton("q1", "Funciones")).click();

    assertThat(panel.isOptionSelected("q1", "Loops")).isTrue();
    assertThat(panel.isOptionSelected("q1", "Funciones")).isTrue();

    test(panel.submitButton()).click();

    assertThat(submittedResponse.get()).isNotNull();
    assertThat(submittedResponse.get().answers())
        .containsExactly(new StudentQuestionAnswer("q1", List.of("Loops", "Funciones"), ""));
  }

  @Test
  void singleSelect_clicking_selected_option_again_clears_row_and_button_state() {
    panel.setQuestionSet(
        questionSet(
            singleSelectQuestion(
                "q1",
                "Ayuda",
                "Como prefieres que te ayude?",
                option("Paso a paso", "Te guio paso a paso."),
                option("Pista breve", "Te doy una pista corta."))));

    test(panel.optionButton("q1", "Paso a paso")).click();
    test(panel.optionButton("q1", "Paso a paso")).click();

    assertThat(panel.isOptionSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.isOptionRowSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.isOptionButtonSelected("q1", "Paso a paso")).isFalse();
    assertThat(panel.submitButton().isEnabled()).isFalse();
  }

  @Test
  void multiSelect_clicking_selected_option_again_removes_only_that_option() {
    panel.setQuestionSet(
        questionSet(
            multiSelectQuestion(
                "q1",
                "Tema",
                "Que te esta frenando ahora mismo?",
                option("Loops", "Te cuesta entender loops."),
                option("Funciones", "Te cuesta entender funciones."),
                option("Arreglos", "Te cuesta entender arreglos."))));

    test(panel.optionButton("q1", "Loops")).click();
    test(panel.optionButton("q1", "Funciones")).click();
    test(panel.optionButton("q1", "Loops")).click();

    assertThat(panel.isOptionSelected("q1", "Loops")).isFalse();
    assertThat(panel.isOptionSelected("q1", "Funciones")).isTrue();
    assertThat(panel.isOptionRowSelected("q1", "Loops")).isFalse();
    assertThat(panel.isOptionRowSelected("q1", "Funciones")).isTrue();
    assertThat(panel.isOptionButtonSelected("q1", "Loops")).isFalse();
    assertThat(panel.isOptionButtonSelected("q1", "Funciones")).isTrue();

    test(panel.submitButton()).click();

    assertThat(submittedResponse.get()).isNotNull();
    assertThat(submittedResponse.get().answers())
        .containsExactly(new StudentQuestionAnswer("q1", List.of("Funciones"), ""));
  }

  @Test
  void customTextOnly_submits_when_last_question_has_no_selected_option() {
    panel.setQuestionSet(
        questionSet(
            singleSelectQuestion(
                "q1",
                "Ayuda",
                "Como prefieres que te ayude?",
                option("Paso a paso", "Te guio paso a paso."),
                option("Pista breve", "Te doy una pista corta."))));

    test(panel.customTextArea("q1")).setValue("Prefiero contarte mi duda con mis palabras");

    assertThat(panel.submitButton().isEnabled()).isTrue();

    test(panel.submitButton()).click();

    assertThat(submittedResponse.get()).isNotNull();
    assertThat(submittedResponse.get().answers())
        .containsExactly(
            new StudentQuestionAnswer(
                "q1", List.of(), "Prefiero contarte mi duda con mis palabras"));
  }

  @Test
  void unanswered_question_keeps_submit_disabled_and_does_not_fire_handler() {
    panel.setQuestionSet(
        questionSet(
            singleSelectQuestion(
                "q1",
                "Ayuda",
                "Como prefieres que te ayude?",
                option("Paso a paso", "Te guio paso a paso."),
                option("Pista breve", "Te doy una pista corta."))));

    assertThat(panel.submitButton().isVisible()).isTrue();
    assertThat(panel.submitButton().isEnabled()).isFalse();
    assertThat(submittedResponse.get()).isNull();
  }

  private static StudentQuestionSet questionSet(StudentQuestion... questions) {
    return new StudentQuestionSet(
        "Antes de seguir", "diagnosis", StudentQuestionSet.ProfileImpact.NONE, List.of(questions));
  }

  private static StudentQuestion singleSelectQuestion(
      String id, String header, String question, StudentQuestionOption... options) {
    return new StudentQuestion(id, header, question, List.of(options), false);
  }

  private static StudentQuestion multiSelectQuestion(
      String id, String header, String question, StudentQuestionOption... options) {
    return new StudentQuestion(id, header, question, List.of(options), true);
  }

  private static StudentQuestionOption option(String label, String description) {
    return new StudentQuestionOption(label, description);
  }
}
