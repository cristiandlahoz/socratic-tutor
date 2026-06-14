package com.wornux.data.repositories.evaluation;

import com.wornux.data.entities.Evaluation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

  List<Evaluation> findAllByOrderByUpdatedAtDescCreatedAtDesc();
}
