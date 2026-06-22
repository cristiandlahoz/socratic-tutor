package com.wornux.legacy.data.repositories.evaluation;

import java.util.List;
import java.util.UUID;

import com.wornux.legacy.data.entities.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("legacyEvaluationRepository")
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    List<Evaluation> findAllByOrderByUpdatedAtDescCreatedAtDesc();
}
