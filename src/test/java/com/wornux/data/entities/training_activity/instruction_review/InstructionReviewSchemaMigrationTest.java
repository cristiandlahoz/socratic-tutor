package com.wornux.data.entities.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InstructionReviewSchemaMigrationTest {

    @Test
    void v9RemovesTheLegacyPersistentInstructionReviewProjection() throws Exception {
        try (var migration = getClass().getResourceAsStream(
                "/db/migration/prod/V9__simplify_training_activities.sql")) {
            assertThat(migration).isNotNull();
            var sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                    .contains("drop table if exists instruction_review_cache")
                    .contains("drop column if exists instruction_review_instructions_hash")
                    .contains("drop column if exists instruction_reviewed_at");
        }
    }
}
