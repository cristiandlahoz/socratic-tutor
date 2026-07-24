package com.wornux.data.entities.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class TrainingActivityTurnSchemaMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void forwardMigrationAllowsEveryCurrentAnswerQualityValue() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/prod").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var assignmentId = fixtureAssignment(jdbc);

        var sequence = 1;
        for (var quality : AnswerQuality.values()) {
            jdbc.update("""
                    insert into training_activity_turn (
                        id, training_activity_assignment_id, sequence_number, question_text, question_created_at,
                        answer_quality, created_at, updated_at)
                    values (?, ?, ?, ?, current_timestamp, ?, current_timestamp, current_timestamp)
                    """, UUID.randomUUID(), assignmentId, sequence++, "Question " + quality.name(), quality.name());
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from training_activity_turn where training_activity_assignment_id = ?",
                Integer.class, assignmentId)).isEqualTo(AnswerQuality.values().length);
    }

    private static UUID fixtureAssignment(JdbcTemplate jdbc) {
        var now = java.sql.Timestamp.from(Instant.now());
        var suffix = UUID.randomUUID().toString();
        var namespaceId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        var periodId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var assignmentId = UUID.randomUUID();
        jdbc.update("insert into role_namespace (id, code, created_at, updated_at) values (?, ?, ?, ?)",
                namespaceId, "tenant:" + suffix, now, now);
        jdbc.update("insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, 'hash', ?, ?)",
                accountId, "student-" + suffix + "@example.test", now, now);
        jdbc.update("insert into tenant (id, role_namespace_id, created_by_account_id, name, locked, created_at, updated_at) values (?, ?, ?, ?, false, ?, ?)",
                tenantId, namespaceId, accountId, "Tenant " + suffix, now, now);
        jdbc.update("insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at) values (?, ?, ?, false, ?, ?)",
                tenantAccountId, tenantId, accountId, now, now);
        jdbc.update("insert into subject (id, tenant_id, code, name, active, created_at, updated_at) values (?, ?, ?, 'Pointers', true, ?, ?)",
                subjectId, tenantId, "SUB-" + suffix, now, now);
        jdbc.update("insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active, created_at, updated_at) values (?, ?, ?, '2026', ?, ?, true, ?, ?)",
                periodId, tenantId, "PER-" + suffix, LocalDate.now(), LocalDate.now().plusDays(30), now, now);
        jdbc.update("insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Algorithms', true, ?, ?)",
                groupClassId, tenantId, subjectId, periodId, tenantAccountId, "GC-" + suffix, now, now);
        jdbc.update("insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at) values (?, ?, ?, 'STUDENT', false, ?, ?)",
                memberId, groupClassId, tenantAccountId, now, now);
        jdbc.update("insert into training_activity (id, group_class_id, created_by_tenant_account_id, title, instructions, status, safe_browser_enabled, created_at, updated_at) values (?, ?, ?, 'Pointers', 'Explain pointer traversal.', 'PUBLISHED', false, ?, ?)",
                activityId, groupClassId, tenantAccountId, now, now);
        jdbc.update("insert into training_activity_assignment (id, training_activity_id, group_class_member_id, status, assigned_at, version, updated_at, safe_browser_locked, safe_browser_session_active) values (?, ?, ?, 'WAITING_FOR_ANSWER', ?, 0, ?, false, false)",
                assignmentId, activityId, memberId, now, now);
        return assignmentId;
    }
}
