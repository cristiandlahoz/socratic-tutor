package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.List;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;

public record InstructionReviewResult(
        boolean validInstruction,
        InstructionQualityStatus qualityStatus,
        InstructionReviewExecutionStatus executionStatus,
        boolean canSave,
        boolean canLaunch,
        String summary,
        String modelVerdict,
        List<InstructionReviewIssue> issues,
        String improvedInstructions,
        String improvedInstructionsHash,
        String instructionsHash,
        Instant reviewedAt,
        String modelName,
        String rubricVersion) {

    public InstructionReviewResult(
            boolean validInstruction,
            InstructionQualityStatus qualityStatus,
            boolean canSave,
            boolean canLaunch,
            String summary,
            String modelVerdict,
            List<InstructionReviewIssue> issues,
            String improvedInstructions,
            String improvedInstructionsHash,
            String instructionsHash,
            Instant reviewedAt,
            String modelName,
            String rubricVersion) {
        this(
                validInstruction,
                qualityStatus,
                InstructionReviewExecutionStatus.COMPLETED,
                canSave,
                canLaunch,
                summary,
                modelVerdict,
                issues,
                improvedInstructions,
                improvedInstructionsHash,
                instructionsHash,
                reviewedAt,
                modelName,
                rubricVersion);
    }

    public boolean isGood() {
        return executionStatus == InstructionReviewExecutionStatus.COMPLETED
                && validInstruction
                && qualityStatus == InstructionQualityStatus.GOOD
                && canSave
                && canLaunch;
    }
}
