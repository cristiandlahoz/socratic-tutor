package com.wornux.data.repositories.evaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.EvaluationAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationAttemptRepository extends JpaRepository<EvaluationAttempt, UUID> {

    @EntityGraph(value = "EvaluationAttempt.withQuestions")
    List<EvaluationAttempt> findByClientIdAndEvaluationRevision_Evaluation_Subject_SlugOrderByStartedAtDesc(
            UUID clientId,
            String subjectSlug);

    @EntityGraph(value = "EvaluationAttempt.withQuestions")
    List<EvaluationAttempt> findByClientIdOrderByStartedAtDesc(UUID clientId);

    @EntityGraph(value = "EvaluationAttempt.report")
    Optional<EvaluationAttempt> findFirstByClientIdAndEvaluationRevision_Evaluation_Subject_IdOrderByStartedAtDesc(
            UUID clientId,
            UUID subjectId);

    @EntityGraph(value = "EvaluationAttempt.report")
    @Query("select a from EvaluationAttempt a where a.id = :id")
    Optional<EvaluationAttempt> findReportById(UUID id);
}
