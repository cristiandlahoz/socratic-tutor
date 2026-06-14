package com.wornux.data.repositories.evaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.enums.EvaluationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    @EntityGraph(value = "Evaluation.withCurrentRevision")
    Optional<Evaluation> findBySubject_SlugAndSlug(String subjectSlug, String slug);

    List<Evaluation> findBySubject_SlugOrderByUpdatedAtDesc(String subjectSlug);

    Optional<Evaluation> findFirstByStatusOrderByUpdatedAtDesc(EvaluationStatus status);

    @EntityGraph(value = "Evaluation.withCurrentRevision")
    @Query("select e from Evaluation e where e.id = :id")
    Optional<Evaluation> findWithCurrentRevisionById(UUID id);
}
