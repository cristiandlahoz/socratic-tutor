package com.wornux.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.domain.evaluation.EvaluationAttemptEntity;
import com.wornux.domain.evaluation.EvaluationAttemptQuestionEntity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationAttemptQuestionEntityTest {

  @Test
  void snapshotsGeneratedQuestionContentSoLaterMapEditsDoNotMutateAttempt() throws Exception {
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("prompt", "generated prompt");

    var attempt = newAttempt();
    set(attempt, "id", UUID.randomUUID());
    var attemptQuestion =
        EvaluationAttemptQuestionEntity.generated(
            attempt, null, "q1", "loops", 1, snapshot, "hash");

    snapshot.put("prompt", "mutated prompt");

    assertThat(attemptQuestion.getQuestionSnapshot()).containsEntry("prompt", "generated prompt");
  }

  private static void set(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static EvaluationAttemptEntity newAttempt() throws Exception {
    Constructor<EvaluationAttemptEntity> constructor =
        EvaluationAttemptEntity.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
