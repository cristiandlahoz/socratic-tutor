package com.wornux.data.repositories.evaluation;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.entities.EvaluationRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRevisionRepository extends JpaRepository<EvaluationRevision, UUID> {

    Optional<EvaluationRevision> findFirstByEvaluationOrderByVersionDesc(Evaluation evaluation);

    @EntityGraph(value = "EvaluationRevision.withExamples")
    @Query("select r from EvaluationRevision r where r.id = :id")
    Optional<EvaluationRevision> findWithExamplesById(UUID id);
}
