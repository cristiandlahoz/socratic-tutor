package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationEntity;
import com.wornux.domain.evaluation.EvaluationRevisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRevisionJpaRepository
    extends JpaRepository<EvaluationRevisionEntity, UUID> {

  Optional<EvaluationRevisionEntity> findFirstByEvaluationOrderByVersionDesc(
      EvaluationEntity evaluation);

  @EntityGraph(value = "EvaluationRevision.withExamples")
  @Query("select r from EvaluationRevisionEntity r where r.id = :id")
  Optional<EvaluationRevisionEntity> findWithExamplesById(UUID id);
}
