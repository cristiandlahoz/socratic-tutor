package com.wornux.data.repositories.evaluation;

import com.wornux.data.entities.EvaluationGuideArtifact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationGuideArtifactRepository
    extends JpaRepository<EvaluationGuideArtifact, UUID> {

  List<EvaluationGuideArtifact> findByEvaluation_IdOrderByPublishedAtDesc(UUID evaluationId);

  Optional<EvaluationGuideArtifact> findByIdAndEvaluation_Id(UUID id, UUID evaluationId);
}
