package com.wornux.services.training_activity;

import java.util.List;

import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;

public record AdaptiveTutorDecision(
        TutorDecisionType type,
        AnswerQuality answerQuality,
        EvidenceStatus evidenceStatus,
        CoverageStatus coverageStatus,
        PedagogicalMove pedagogicalMove,
        boolean shouldContinue,
        List<String> coveredInstructionAspects,
        List<String> missingInstructionAspects,
        boolean unproductivePatternDetected,
        String questionText,
        String reason) {
}
