package com.wornux.data.repositories.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = TrainingActivityAssignmentRepositoryIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.locations=classpath:db/migration/prod" })
class TrainingActivityAssignmentRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingActivityAssignmentRepository repository;

    @Test
    void findByIdLeavesTrainingActivityLazyOutsideRepositoryBoundary() {
        var fixture = insertFixture();

        var assignment = repository.findById(fixture.assignmentId()).orElseThrow();

        assertThatThrownBy(() -> assignment.getTrainingActivity().getStatus())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void findWithTrainingActivityByIdKeepsTrainingActivityAccessibleOutsideRepositoryBoundary() {
        var fixture = insertFixture();

        var assignment = repository.findWithTrainingActivityById(fixture.assignmentId()).orElseThrow();

        assertThat(assignment.getTrainingActivity().getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(assignment.getTrainingActivity().getTitle()).isEqualTo("Pointers");
        assertThat(assignment.getGroupClassMember().getId()).isEqualTo(fixture.studentMemberId());
    }

    @Test
    void rejectsASecondPublishedActivityForTheSameProfessor() {
        var fixture = insertFixture();

        assertThatThrownBy(() -> insertActivity(
                UUID.randomUUID(), fixture.groupClassId(), fixture.tenantAccountId(), "PUBLISHED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(publishedActivityCount(fixture.tenantAccountId())).isEqualTo(1);
    }

    @Test
    void allowsAnotherActivityAfterTheFirstIsClosed() {
        var fixture = insertFixture();
        jdbcTemplate.update("update training_activity set status = 'CLOSED' where id = ?", fixture.trainingActivityId());

        insertActivity(UUID.randomUUID(), fixture.groupClassId(), fixture.tenantAccountId(), "PUBLISHED");

        assertThat(publishedActivityCount(fixture.tenantAccountId())).isEqualTo(1);
    }

    @Test
    void allowsDifferentProfessorsToHaveIndependentPublishedActivities() {
        var fixture = insertFixture();
        var otherProfessorTenantAccountId = insertTenantAccount(fixture);

        insertActivity(UUID.randomUUID(), fixture.groupClassId(), otherProfessorTenantAccountId, "PUBLISHED");

        assertThat(publishedActivityCount(fixture.tenantAccountId())).isEqualTo(1);
        assertThat(publishedActivityCount(otherProfessorTenantAccountId)).isEqualTo(1);
    }

    @Test
    void concurrentPublicationAttemptsCannotCreateTwoPublishedActivitiesForOneProfessor() throws Exception {
        var fixture = insertFixture();
        jdbcTemplate.update("update training_activity set status = 'CLOSED' where id = ?", fixture.trainingActivityId());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tryInsertPublishedActivity(fixture, ready, start));
            var second = executor.submit(() -> tryInsertPublishedActivity(fixture, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat((first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(publishedActivityCount(fixture.tenantAccountId())).isEqualTo(1);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private FixtureIds insertFixture() {
        var suffix = UUID.randomUUID().toString();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        var roleNamespaceId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        var academicPeriodId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var studentMemberId = UUID.randomUUID();
        var trainingActivityId = UUID.randomUUID();
        var assignmentId = UUID.randomUUID();

        jdbcTemplate.update(
                "insert into role_namespace (id, code, created_at, updated_at) values (?, ?, ?, ?)",
                roleNamespaceId,
                "tenant:" + suffix,
                now,
                now);
        jdbcTemplate.update(
                "insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                accountId,
                "student-" + suffix + "@example.test",
                "hash",
                now,
                now);
        jdbcTemplate.update(
                "insert into tenant (id, role_namespace_id, created_by_account_id, name, locked, created_at, updated_at) values (?, ?, ?, ?, false, ?, ?)",
                tenantId,
                roleNamespaceId,
                accountId,
                "Tenant " + suffix,
                now,
                now);
        jdbcTemplate.update(
                "insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at) values (?, ?, ?, false, ?, ?)",
                tenantAccountId,
                tenantId,
                accountId,
                now,
                now);
        jdbcTemplate.update(
                "insert into subject (id, tenant_id, code, name, active, created_at, updated_at) values (?, ?, ?, ?, true, ?, ?)",
                subjectId,
                tenantId,
                "SUB-" + suffix,
                "Pointers",
                now,
                now);
        jdbcTemplate.update(
                "insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, true, ?, ?)",
                academicPeriodId,
                tenantId,
                "PER-" + suffix,
                "2026",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                now,
                now);
        jdbcTemplate.update(
                "insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, true, ?, ?)",
                groupClassId,
                tenantId,
                subjectId,
                academicPeriodId,
                tenantAccountId,
                "GC-" + suffix,
                "Algorithms",
                now,
                now);
        jdbcTemplate.update(
                "insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at) values (?, ?, ?, 'STUDENT', false, ?, ?)",
                studentMemberId,
                groupClassId,
                tenantAccountId,
                now,
                now);
        jdbcTemplate.update(
                "insert into training_activity (id, group_class_id, created_by_tenant_account_id, created_by_group_class_member_id, title, instructions, status, opens_at, closes_at, safe_browser_enabled, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'PUBLISHED', null, null, false, ?, ?)",
                trainingActivityId,
                groupClassId,
                tenantAccountId,
                studentMemberId,
                "Pointers",
                "Describe pointer traversal.",
                now,
                now);
        jdbcTemplate.update(
                "insert into training_activity_assignment (id, training_activity_id, group_class_member_id, status, assigned_at, started_at, submitted_at, version, updated_at, safe_browser_locked, safe_browser_session_active) values (?, ?, ?, 'WAITING_FOR_ANSWER', ?, ?, null, 0, ?, false, false)",
                assignmentId,
                trainingActivityId,
                studentMemberId,
                now,
                now,
                now);
        return new FixtureIds(tenantId, tenantAccountId, groupClassId, trainingActivityId, assignmentId, studentMemberId);
    }

    private UUID insertTenantAccount(FixtureIds fixture) {
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbcTemplate.update(
                "insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                accountId,
                "professor-" + accountId + "@example.test",
                "hash",
                now,
                now);
        jdbcTemplate.update(
                "insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at) values (?, ?, ?, false, ?, ?)",
                tenantAccountId,
                fixture.tenantId(),
                accountId,
                now,
                now);
        jdbcTemplate.update(
                "insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at) values (?, ?, ?, 'PROFESSOR', false, ?, ?)",
                UUID.randomUUID(),
                fixture.groupClassId(),
                tenantAccountId,
                now,
                now);
        return tenantAccountId;
    }

    private void insertActivity(UUID activityId, UUID groupClassId, UUID tenantAccountId, String status) {
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbcTemplate.update(
                "insert into training_activity (id, group_class_id, created_by_tenant_account_id, title, instructions, status, safe_browser_enabled, created_at, updated_at) values (?, ?, ?, ?, ?, ?, false, ?, ?)",
                activityId,
                groupClassId,
                tenantAccountId,
                "Activity " + activityId,
                "Describe pointer traversal.",
                status,
                now,
                now);
    }

    private boolean tryInsertPublishedActivity(FixtureIds fixture, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start publication.");
            }
            insertActivity(UUID.randomUUID(), fixture.groupClassId(), fixture.tenantAccountId(), "PUBLISHED");
            return true;
        }
        catch (DataIntegrityViolationException exception) {
            return false;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing.", exception);
        }
    }

    private int publishedActivityCount(UUID tenantAccountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from training_activity where created_by_tenant_account_id = ? and status = 'PUBLISHED'",
                Integer.class,
                tenantAccountId);
    }

    private record FixtureIds(
            UUID tenantId,
            UUID tenantAccountId,
            UUID groupClassId,
            UUID trainingActivityId,
            UUID assignmentId,
            UUID studentMemberId) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.wornux.data.entities")
    @EnableJpaRepositories(basePackages = "com.wornux.data.repositories")
    static class TestApp {
    }
}
