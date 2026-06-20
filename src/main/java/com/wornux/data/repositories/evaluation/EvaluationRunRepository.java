package com.wornux.data.repositories.evaluation;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.EvaluationRun;
import com.wornux.data.enums.EvaluationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {

    List<EvaluationRun> findByEvaluationIdOrderByCreatedAtDesc(UUID evaluationId);

    List<EvaluationRun> findByStudentClientIdOrderByCreatedAtDesc(UUID studentClientId);

    List<EvaluationRun> findByEvaluationIdAndStudentClientIdAndStatus(
            UUID evaluationId,
            UUID studentClientId,
            EvaluationRunStatus status);
}
