package com.wornux.data.repositories.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EvaluationLifecycleArtifactRepositoryTest {

  @Test
  void migrationDefinesArtifactForeignKeysAndReverseChronologicalResultIndex() throws Exception {
    var sql =
        Files.readString(
            Path.of("src/main/resources/db/migration/V2__evaluation_lifecycle_artifacts.sql"));

    assertThat(sql)
        .contains("evaluation_id uuid not null references evaluation(id) on delete cascade")
        .contains("revision_id uuid not null references evaluation_revision(id) on delete cascade")
        .contains("attempt_id uuid not null references evaluation_attempt(id) on delete cascade")
        .contains("on evaluation_result_artifact (evaluation_id, completed_at desc)");
  }

  @Test
  void repositoriesExposeCatalogAndReverseChronologicalHistoryQueries() {
    var guideMethods = Arrays.stream(EvaluationGuideArtifactRepository.class.getMethods()).map(m -> m.getName()).toList();
    var resultMethods = Arrays.stream(EvaluationResultArtifactRepository.class.getMethods()).map(m -> m.getName()).toList();

    assertThat(guideMethods)
        .contains("findByEvaluation_IdOrderByPublishedAtDesc")
        .contains("findByIdAndEvaluation_Id");
    assertThat(resultMethods).contains("findByEvaluation_IdOrderByCompletedAtDesc");
  }
}
