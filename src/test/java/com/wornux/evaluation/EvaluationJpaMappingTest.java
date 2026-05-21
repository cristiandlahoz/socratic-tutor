package com.wornux.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.domain.evaluation.EvaluationAttemptEntity;
import com.wornux.domain.evaluation.EvaluationAttemptResponseEntity;
import com.wornux.domain.evaluation.EvaluationEntity;
import com.wornux.domain.evaluation.EvaluationQuestionExampleEntity;
import com.wornux.domain.evaluation.EvaluationRevisionEntity;
import com.wornux.domain.subject.SubjectConfigRevisionEntity;
import com.wornux.domain.subject.SubjectEntity;
import com.wornux.infrastructure.persistence.evaluation.EvaluationAttemptJpaRepository;
import com.wornux.infrastructure.persistence.evaluation.EvaluationAttemptQuestionJpaRepository;
import com.wornux.infrastructure.persistence.evaluation.EvaluationAttemptResponseJpaRepository;
import com.wornux.infrastructure.persistence.evaluation.EvaluationJpaRepository;
import com.wornux.infrastructure.persistence.evaluation.EvaluationQuestionExampleJpaRepository;
import com.wornux.infrastructure.persistence.evaluation.EvaluationRevisionJpaRepository;
import com.wornux.infrastructure.persistence.subject.SubjectConfigRevisionJpaRepository;
import com.wornux.infrastructure.persistence.subject.SubjectJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class EvaluationJpaMappingTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired EntityManager entityManager;
  @Autowired SubjectJpaRepository subjectRepository;
  @Autowired SubjectConfigRevisionJpaRepository subjectConfigRevisionRepository;
  @Autowired EvaluationJpaRepository evaluationRepository;
  @Autowired EvaluationRevisionJpaRepository evaluationRevisionRepository;
  @Autowired EvaluationQuestionExampleJpaRepository exampleRepository;
  @Autowired EvaluationAttemptJpaRepository attemptRepository;
  @Autowired EvaluationAttemptQuestionJpaRepository attemptQuestionRepository;
  @Autowired EvaluationAttemptResponseJpaRepository responseRepository;

  @Test
  void mapsGeneratedEvaluationAggregateWithLazyRelationsAndReportGraph() {
    var subject = subjectRepository.save(SubjectEntity.create("jpa-intro", "JPA Intro"));
    var configRevision =
        subjectConfigRevisionRepository.save(
            SubjectConfigRevisionEntity.create(
                subject, 1, Map.of("scope", "loops"), Map.of(), Map.of(), "test"));
    subject.publishConfig(configRevision);
    subjectRepository.saveAndFlush(subject);

    var evaluation =
        evaluationRepository.save(EvaluationEntity.draft(subject, "diagnostic", "Diagnostic"));
    var revision =
        evaluationRevisionRepository.save(
            EvaluationRevisionEntity.create(
                evaluation, configRevision, 1, "Generate questions.", Map.of(), Map.of()));
    var example =
        exampleRepository.save(
            EvaluationQuestionExampleEntity.create(
                revision, "loop-guidance", 1, "Probe loop tracing.", Map.of()));
    evaluation.publish(revision);
    evaluationRepository.saveAndFlush(evaluation);

    var attempt = EvaluationAttemptEntity.launch(revision, UUID.randomUUID(), null, Map.of(), 1);
    var attemptQuestion =
        attempt.addGeneratedQuestion(
            example,
            "q1",
            "loop-guidance",
            1,
            Map.of("prompt", "Generated question", "generationMode", "generated"),
            "stable-hash");
    attemptRepository.saveAndFlush(attempt);
    responseRepository.saveAndFlush(
        EvaluationAttemptResponseEntity.answer(attemptQuestion, "i = i + 1", List.of()));
    entityManager.clear();

    assertCleanGreenfieldSchema();

    var lazyAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
    assertThat(Hibernate.isInitialized(lazyAttempt.getEvaluationRevision())).isFalse();
    assertThat(Hibernate.isInitialized(lazyAttempt.getQuestions())).isFalse();

    var reportAttempt = attemptRepository.findReportById(attempt.getId()).orElseThrow();
    assertThat(Hibernate.isInitialized(reportAttempt.getEvaluationRevision())).isTrue();
    assertThat(Hibernate.isInitialized(reportAttempt.getQuestions())).isTrue();

    var withResponses =
        attemptQuestionRepository.findWithResponsesByAttemptOrderByOrdinalAsc(reportAttempt);
    assertThat(withResponses).hasSize(1);
    assertThat(Hibernate.isInitialized(withResponses.getFirst().getResponses())).isTrue();
    assertThat(withResponses.getFirst().getQuestionSnapshot())
        .containsEntry("generationMode", "generated");
  }

  private void assertCleanGreenfieldSchema() {
    assertThat(tableExists("student_topic_mastery")).isFalse();
    assertThat(tableExists("evaluation_question")).isFalse();
    assertThat(tableExists("evaluation_question_example")).isTrue();
    assertThat(columnExists("student_profile", "overall_level")).isFalse();
    assertThat(columnExists("student_profile", "confidence_score")).isFalse();
  }

  private boolean tableExists(String tableName) {
    var result =
        entityManager
            .createNativeQuery(
                """
                select count(*)
                from information_schema.tables
                where table_name = :tableName
                """)
            .setParameter("tableName", tableName)
            .getSingleResult();
    return ((Number) result).intValue() == 1;
  }

  private boolean columnExists(String tableName, String columnName) {
    var result =
        entityManager
            .createNativeQuery(
                """
                select count(*)
                from information_schema.columns
                where table_name = :tableName and column_name = :columnName
                """)
            .setParameter("tableName", tableName)
            .setParameter("columnName", columnName)
            .getSingleResult();
    return ((Number) result).intValue() == 1;
  }
}
