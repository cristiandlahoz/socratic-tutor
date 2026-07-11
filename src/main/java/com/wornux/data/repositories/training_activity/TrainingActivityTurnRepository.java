package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingActivityTurnRepository extends JpaRepository<TrainingActivityTurn, UUID> {
    List<TrainingActivityTurn> findByAssignment_IdOrderBySequenceNumberAsc(UUID assignmentId);
    Optional<TrainingActivityTurn> findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(UUID assignmentId);
    Optional<TrainingActivityTurn> findByAssignment_IdAndAnswerSubmissionId(UUID assignmentId, UUID answerSubmissionId);
}
