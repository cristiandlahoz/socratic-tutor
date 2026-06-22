package com.wornux.data.repositories.evaluation;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.evaluation.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("academicEvaluationRepository")
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
    List<Evaluation> findByGroupClass_IdOrderByUpdatedAtDesc(UUID groupClassId);
}
