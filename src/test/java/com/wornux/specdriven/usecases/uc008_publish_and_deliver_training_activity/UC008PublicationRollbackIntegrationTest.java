package com.wornux.specdriven.usecases.uc008_publish_and_deliver_training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class UC008PublicationRollbackIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void af7_intermediateDeliveryPersistenceFailureRollsBackActivityAssignmentsAndOutboxTogether() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/prod").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var ids = fixture(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(_ -> {
            jdbc.update("update training_activity set status = 'PUBLISHED', published_at = current_timestamp where id = ?", ids.activityId());
            jdbc.update("insert into training_activity_assignment (id, training_activity_id, group_class_member_id, status, assigned_at, updated_at) values (?, ?, ?, 'ASSIGNED', current_timestamp, current_timestamp)",
                    ids.assignmentId(), ids.activityId(), ids.studentMemberId());
            jdbc.update("insert into outbox_event (id, aggregate_type, aggregate_id, event_type, deduplication_key, status, available_at, created_at) values (?, 'TRAINING_ACTIVITY', ?, 'ACTIVITY_PUBLISHED', ?, 'PENDING', current_timestamp, current_timestamp)",
                    ids.eventId(), ids.activityId(), "activity-published:" + ids.activityId());
            jdbc.update("insert into outbox_recipient_delivery (id, outbox_event_id, group_class_member_id, idempotency_key, status, available_at, created_at) values (?, ?, ?, ?, 'PENDING', current_timestamp, current_timestamp)",
                    ids.deliveryId(), ids.eventId(), ids.studentMemberId(), "activity-published:" + ids.activityId() + ":" + ids.studentMemberId());
            jdbc.execute("select 1 / 0");
        })).isNotNull();

        assertThat(jdbc.queryForObject("select status from training_activity where id = ?", String.class, ids.activityId()))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from training_activity_assignment where training_activity_id = ?", Long.class, ids.activityId()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id = ?", Long.class, ids.activityId()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_recipient_delivery where id = ?", Long.class, ids.deliveryId()))
                .isZero();
    }

    private static Fixture fixture(JdbcTemplate jdbc) {
        var now = java.sql.Timestamp.from(Instant.now());
        var suffix = UUID.randomUUID().toString();
        var namespaceId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        var periodId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var professorMemberId = UUID.randomUUID();
        var studentAccountId = UUID.randomUUID();
        var studentTenantAccountId = UUID.randomUUID();
        var studentMemberId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        jdbc.update("insert into role_namespace (id, code, created_at, updated_at) values (?, ?, ?, ?)", namespaceId, "tenant:" + suffix, now, now);
        jdbc.update("insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, 'hash', ?, ?)", accountId, "professor-" + suffix + "@example.test", now, now);
        jdbc.update("insert into tenant (id, role_namespace_id, created_by_account_id, name, locked, created_at, updated_at) values (?, ?, ?, ?, false, ?, ?)", tenantId, namespaceId, accountId, "Tenant " + suffix, now, now);
        jdbc.update("insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at) values (?, ?, ?, false, ?, ?)", tenantAccountId, tenantId, accountId, now, now);
        jdbc.update("insert into subject (id, tenant_id, code, name, active, created_at, updated_at) values (?, ?, ?, 'Pointers', true, ?, ?)", subjectId, tenantId, "SUB-" + suffix, now, now);
        jdbc.update("insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active, created_at, updated_at) values (?, ?, ?, '2026', ?, ?, true, ?, ?)", periodId, tenantId, "PER-" + suffix, LocalDate.now(), LocalDate.now().plusDays(30), now, now);
        jdbc.update("insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Algorithms', true, ?, ?)", groupClassId, tenantId, subjectId, periodId, tenantAccountId, "GC-" + suffix, now, now);
        jdbc.update("insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at) values (?, ?, ?, 'PROFESSOR', false, ?, ?)", professorMemberId, groupClassId, tenantAccountId, now, now);
        jdbc.update("insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, 'hash', ?, ?)", studentAccountId, "student-" + suffix + "@example.test", now, now);
        jdbc.update("insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at) values (?, ?, ?, false, ?, ?)", studentTenantAccountId, tenantId, studentAccountId, now, now);
        jdbc.update("insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at) values (?, ?, ?, 'STUDENT', false, ?, ?)", studentMemberId, groupClassId, studentTenantAccountId, now, now);
        jdbc.update("insert into training_activity (id, group_class_id, created_by_tenant_account_id, created_by_group_class_member_id, title, instructions, status, safe_browser_enabled, created_at, updated_at) values (?, ?, ?, ?, 'Pointers', 'Explain pointer traversal.', 'DRAFT', false, ?, ?)", activityId, groupClassId, tenantAccountId, professorMemberId, now, now);
        return new Fixture(activityId, studentMemberId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private record Fixture(UUID activityId, UUID studentMemberId, UUID assignmentId, UUID eventId, UUID deliveryId) {
    }
}
