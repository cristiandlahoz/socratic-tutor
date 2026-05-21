package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationEntity;
import com.wornux.domain.evaluation.EvaluationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationJpaRepository extends JpaRepository<EvaluationEntity, UUID> {

  @EntityGraph(value = "Evaluation.withCurrentRevision")
  Optional<EvaluationEntity> findBySubject_SlugAndSlug(String subjectSlug, String slug);

  List<EvaluationEntity> findBySubject_SlugOrderByUpdatedAtDesc(String subjectSlug);

  Optional<EvaluationEntity> findFirstByStatusOrderByUpdatedAtDesc(EvaluationStatus status);

  @EntityGraph(value = "Evaluation.withCurrentRevision")
  @Query("select e from EvaluationEntity e where e.id = :id")
  Optional<EvaluationEntity> findWithCurrentRevisionById(UUID id);
}
