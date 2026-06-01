package com.wornux.data.repositories.evaluation;

import com.wornux.data.entities.EvaluationResultArtifact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationResultArtifactRepository
    extends JpaRepository<EvaluationResultArtifact, UUID> {

  List<EvaluationResultArtifact> findByEvaluation_IdOrderByCompletedAtDesc(UUID evaluationId);
}
