package com.wornux.specdriven.usecases.uc009_finalize_and_report_evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReportStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.services.training_activity.TrainingAssignmentTutorService;
import com.wornux.services.training_activity.TrainingTutorJobService;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewJobWorker;
import com.wornux.services.training_activity.instruction_review.InstructionReviewService;
import com.wornux.ui.training_activity.TrainingAssignmentView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One durable, real-PostgreSQL UC-009 flow. The report model cannot complete until the test releases it. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = UC009FinalizeAndReportEvaluationIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.docker.compose.enabled=false",
                "spring.flyway.locations=classpath:db/migration/prod",
                "spring.ai.openai.timeout=PT30S",
                "spring.ai.openai.max-retries=0" })
class UC009FinalizeAndReportEvaluationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TrainingAssignmentEvaluationService evaluationService;
    @Autowired private TrainingActivityAssignmentRepository assignmentRepository;
    @Autowired private TrainingActivityReportRepository reportRepository;
    @Autowired private TrainingActivityReportProjectionService reportProjectionService;
    @Autowired private InstructionReviewJobWorker worker;
    @Autowired private SafeBrowserAssignmentStateBus assignmentStateBus;
    @Autowired private ActiveAcademicContextResolver contextResolver;
    @Autowired private LatchControlledChatModel chatModel;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void clearUi() {
        UI.setCurrent(null);
    }

    @Test
    void mainFlow_studentCommandAndReturnCompleteBeforeTheBlockedReportWorkerPublishesAuthorizedCanonicalProjection()
            throws InterruptedException {
        var fixture = insertFixture();
        when(contextResolver.requireCurrent()).thenReturn(fixture.studentContext());
        var studentUi = new TrackingUi();
        UI.setCurrent(studentUi);
        var studentView = new TrainingAssignmentView(evaluationService, org.mockito.Mockito.mock(SafeBrowserModeService.class),
                assignmentStateBus, conversationProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(studentView, "assignmentId", fixture.assignmentId());
        org.springframework.test.util.ReflectionTestUtils.setField(
                studentView, "assignment", evaluationService.getForCurrentStudent(fixture.assignmentId()));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(studentView, "subscribeToAssignmentStateChanges", studentUi);

        var commandResult = evaluationService.submitAnswer(
                fixture.assignmentId(), "El recorrido visita cada nodo una vez.", UUID.randomUUID());

        assertThat(commandResult.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        worker.poll();
        await("terminal tutor decision", () -> assignmentStatus(fixture.assignmentId()) == TrainingActivityAssignmentStatus.SUBMITTED);
        await("student navigation", () -> "student".equals(studentUi.navigatedTo()));

        var pendingReport = reportRepository.findByAssignment_Id(fixture.assignmentId()).orElseThrow();
        var finalReportJobCount = jdbcTemplate.queryForObject("""
                select count(*) from training_activity_ai_job
                where training_activity_assignment_id = ? and job_type = 'FINAL_REPORT'
                """, Integer.class, fixture.assignmentId());
        var finalReportJobStatus = jdbcTemplate.queryForObject("""
                select status from training_activity_ai_job
                where training_activity_assignment_id = ? and job_type = 'FINAL_REPORT'
                """, String.class, fixture.assignmentId());
        assertThat(pendingReport.getStatus()).isEqualTo(TrainingActivityReportStatus.PENDING);
        assertThat(finalReportJobCount).isEqualTo(1);
        assertThat(finalReportJobStatus).isEqualTo(TrainingActivityAiJobStatus.PENDING.name());

        worker.poll();
        assertThat(chatModel.reportModelEntered().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reportRepository.findByAssignment_Id(fixture.assignmentId()).orElseThrow().getStatus())
                .isEqualTo(TrainingActivityReportStatus.GENERATING);

        chatModel.releaseReportModel();
        await("ready report", () -> reportRepository.findByAssignment_Id(fixture.assignmentId())
                .map(report -> report.getStatus() == TrainingActivityReportStatus.READY)
                .orElse(false));

        when(contextResolver.requireCurrent()).thenReturn(fixture.professorContext());
        var projection = reportProjectionService.getForCurrentReviewer(fixture.assignmentId());

        assertThat(projection.summary()).isEqualTo("La explicación describe un recorrido secuencial.");
        assertThat(projection.turns()).extracting(
                TrainingActivityReportProjectionService.TurnProjection::sequenceNumber,
                TrainingActivityReportProjectionService.TurnProjection::questionText,
                TrainingActivityReportProjectionService.TurnProjection::answerText)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        1, "¿Cómo recorre una lista enlazada?", "El recorrido visita cada nodo una vez."));
        assertThat(projection.weaknesses()).singleElement().satisfies(finding ->
                assertThat(finding.evidenceReferences()).extracting(reference -> reference.turnSequence()).containsExactly(1));

        when(contextResolver.requireCurrent()).thenReturn(fixture.otherTenantProfessorContext());
        assertThatThrownBy(() -> reportProjectionService.getForCurrentReviewer(fixture.assignmentId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationProperties.Ai.Conversation conversationProperties() {
        var properties = new ApplicationProperties.Ai.Conversation();
        properties.setContextWindowTokens(2_000);
        return properties;
    }

    private TrainingActivityAssignmentStatus assignmentStatus(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId).orElseThrow().getStatus();
    }

    private void await(String description, BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(25);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, exception);
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private Fixture insertFixture() {
        var primary = insertGroup("primary");
        var other = insertGroup("other");
        var now = Timestamp.from(Instant.now());
        var activityId = UUID.randomUUID();
        var assignmentId = UUID.randomUUID();
        var turnId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into training_activity (id, group_class_id, created_by_tenant_account_id, created_by_group_class_member_id,
                  title, instructions, status, safe_browser_enabled, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'PUBLISHED', false, ?, ?)
                """, activityId, primary.groupClassId(), primary.professorTenantAccountId(), primary.professorMemberId(),
                "Listas enlazadas", "Explica el recorrido de una lista enlazada.", now, now);
        jdbcTemplate.update("""
                insert into training_activity_assignment (id, training_activity_id, group_class_member_id, status, assigned_at,
                  started_at, version, updated_at, safe_browser_locked, safe_browser_session_active)
                values (?, ?, ?, 'WAITING_FOR_ANSWER', ?, ?, 0, ?, false, false)
                """, assignmentId, activityId, primary.studentMemberId(), now, now, now);
        jdbcTemplate.update("""
                insert into training_activity_turn (id, training_activity_assignment_id, sequence_number, question_text,
                  question_created_at, created_at, updated_at)
                values (?, ?, 1, ?, ?, ?, ?)
                """, turnId, assignmentId, "¿Cómo recorre una lista enlazada?", now, now, now);

        return new Fixture(
                assignmentId,
                new ActiveAcademicContext(UUID.randomUUID(), primary.studentTenantAccountId(), primary.studentMemberId(),
                        primary.groupClassId(), GroupClassMemberKind.STUDENT),
                new ActiveAcademicContext(UUID.randomUUID(), primary.professorTenantAccountId(), primary.professorMemberId(),
                        primary.groupClassId(), GroupClassMemberKind.PROFESSOR),
                new ActiveAcademicContext(UUID.randomUUID(), other.professorTenantAccountId(), other.professorMemberId(),
                        other.groupClassId(), GroupClassMemberKind.PROFESSOR));
    }

    private GroupFixture insertGroup(String prefix) {
        var now = Timestamp.from(Instant.now());
        var suffix = prefix + "-" + UUID.randomUUID();
        var roleNamespaceId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        var periodId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var professor = insertAccountAndMembership(
                suffix + "-professor", roleNamespaceId, tenantId, groupClassId, subjectId, periodId, now, GroupClassMemberKind.PROFESSOR,
                true);
        var student = insertAccountAndMembership(
                suffix + "-student", roleNamespaceId, tenantId, groupClassId, subjectId, periodId, now, GroupClassMemberKind.STUDENT,
                false);
        return new GroupFixture(groupClassId, professor.tenantAccountId(), professor.memberId(), student.tenantAccountId(), student.memberId());
    }

    private AccountMembership insertAccountAndMembership(
            String suffix, UUID roleNamespaceId, UUID tenantId, UUID groupClassId, UUID subjectId, UUID periodId,
            Timestamp now, GroupClassMemberKind kind, boolean createGroup) {
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        if (createGroup) {
            jdbcTemplate.update("insert into role_namespace (id, code, created_at, updated_at) values (?, ?, ?, ?)",
                    roleNamespaceId, "tenant:" + suffix, now, now);
            jdbcTemplate.update("insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                    accountId, suffix + "@example.test", "hash", now, now);
            jdbcTemplate.update("""
                    insert into tenant (id, role_namespace_id, created_by_account_id, name, locked, created_at, updated_at)
                    values (?, ?, ?, ?, false, ?, ?)
                    """, tenantId, roleNamespaceId, accountId, "Tenant " + suffix, now, now);
            jdbcTemplate.update("""
                    insert into subject (id, tenant_id, code, name, active, created_at, updated_at)
                    values (?, ?, ?, ?, true, ?, ?)
                    """, subjectId, tenantId, "SUB-" + suffix, "Algorithms", now, now);
            jdbcTemplate.update("""
                    insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, true, ?, ?)
                    """, periodId, tenantId, "PER-" + suffix, "2026", LocalDate.now(), LocalDate.now().plusDays(30), now, now);
        }
        else {
            jdbcTemplate.update("insert into account (id, email, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                    accountId, suffix + "@example.test", "hash", now, now);
        }
        jdbcTemplate.update("""
                insert into tenant_account (id, tenant_id, account_id, locked, joined_at, updated_at)
                values (?, ?, ?, false, ?, ?)
                """, tenantAccountId, tenantId, accountId, now, now);
        if (createGroup) {
            jdbcTemplate.update("""
                    insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id,
                      code, name, active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                    """, groupClassId, tenantId, subjectId, periodId, tenantAccountId, "GC-" + suffix, "Algorithms", now, now);
        }
        jdbcTemplate.update("""
                insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked, joined_at, updated_at)
                values (?, ?, ?, ?, false, ?, ?)
                """, memberId, groupClassId, tenantAccountId, kind.name(), now, now);
        return new AccountMembership(tenantAccountId, memberId);
    }

    private record Fixture(UUID assignmentId, ActiveAcademicContext studentContext, ActiveAcademicContext professorContext,
                           ActiveAcademicContext otherTenantProfessorContext) {}
    private record GroupFixture(UUID groupClassId, UUID professorTenantAccountId, UUID professorMemberId,
                                UUID studentTenantAccountId, UUID studentMemberId) {}
    private record AccountMembership(UUID tenantAccountId, UUID memberId) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "com.vaadin.flow.spring.SpringBootAutoConfiguration",
            "com.vaadin.flow.spring.SpringSecurityAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration",
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
            "io.arconia.dev.services.docling.DoclingDevServicesAutoConfiguration",
            "io.arconia.docling.autoconfigure.DoclingAutoConfiguration",
            "io.arconia.docling.autoconfigure.actuate.DoclingServeHealthContributorAutoConfiguration" })
    @EnableJpaRepositories(basePackages = "com.wornux.data.repositories")
    @EntityScan(basePackages = "com.wornux.data.entities")
    @EnableConfigurationProperties({OpenAiCommonProperties.class, OpenAiChatProperties.class})
    @Import({TestBeans.class, PromptResources.class, SafeBrowserAssignmentStateBus.class,
            TrainingAssignmentTutorService.class, TrainingTutorJobService.class, TrainingAssignmentEvaluationService.class,
            TrainingActivityReportProjectionService.class, InstructionReviewJobWorker.class})
    static class TestApp {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean @Primary
        ActiveAcademicContextResolver activeAcademicContextResolver() {
            return org.mockito.Mockito.mock(ActiveAcademicContextResolver.class);
        }

        @Bean @Primary
        LatchControlledChatModel chatModel() {
            return new LatchControlledChatModel();
        }

        @Bean
        AdvisoryInstructionReviewService advisoryInstructionReviewService() {
            return org.mockito.Mockito.mock(AdvisoryInstructionReviewService.class);
        }

        @Bean
        InstructionReviewService instructionReviewService() {
            return org.mockito.Mockito.mock(InstructionReviewService.class);
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean(name = "instructionReviewWorkerExecutor", destroyMethod = "shutdown")
        ThreadPoolExecutor instructionReviewWorkerExecutor() {
            return executor("uc009-worker-");
        }

        @Bean(name = "instructionReviewModelExecutor", destroyMethod = "shutdown")
        ThreadPoolExecutor instructionReviewModelExecutor() {
            return executor("uc009-model-");
        }

        private ThreadPoolExecutor executor(String name) {
            return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2),
                    Thread.ofPlatform().name(name, 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        }
    }

    static class LatchControlledChatModel implements ChatModel {
        private final AtomicInteger callCount = new AtomicInteger();
        private final CountDownLatch reportModelEntered = new CountDownLatch(1);
        private final CountDownLatch reportModelRelease = new CountDownLatch(1);

        @Override
        public ChatResponse call(Prompt prompt) {
            if (callCount.getAndIncrement() == 0) {
                return response("""
                        {"type":"COMPLETE_SUCCESS","answerQuality":"GOOD","evidenceStatus":"STRONG_EVIDENCE",
                        "coverageStatus":"SUFFICIENT","pedagogicalMove":"COMPLETE_SUCCESSFULLY","shouldContinue":false,
                        "coveredInstructionAspects":["recorrido"],"missingInstructionAspects":[],
                        "unproductivePatternDetected":false,"questionText":"","reasonCode":"COMPLETE_SUCCESS"}
                        """);
            }
            reportModelEntered.countDown();
            try {
                if (!reportModelRelease.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The report model was not released by the behavior test.");
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The controlled report model was interrupted.", exception);
            }
            return response("""
                    {"evidenceStatus":"STRONG_EVIDENCE","summary":"La explicación describe un recorrido secuencial.",
                    "strengths":[{"observation":"Describe una visita por nodo.","evidenceReferences":[{"turnSequence":1,"answerExcerpt":"visita cada nodo"}]}],
                    "weaknesses":[{"observation":"Puede justificar el criterio de finalización.","evidenceReferences":[{"turnSequence":1}]}],
                    "observations":[{"observation":"La respuesta se relaciona con el recorrido.","evidenceReferences":[{"turnSequence":1}]}],
                    "recommendations":["Pedir una justificación del final del recorrido."]}
                    """);
        }

        CountDownLatch reportModelEntered() {
            return reportModelEntered;
        }

        void releaseReportModel() {
            reportModelRelease.countDown();
        }

        private static ChatResponse response(String json) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        }
    }

    private static class TrackingUi extends UI {
        private volatile String navigatedTo;

        @Override
        public Future<Void> access(Command command) {
            command.execute();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void navigate(String location) {
            navigatedTo = location;
        }

        String navigatedTo() {
            return navigatedTo;
        }
    }
}
