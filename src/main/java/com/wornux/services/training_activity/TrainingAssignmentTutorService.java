package com.wornux.services.training_activity;

import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import org.springframework.stereotype.Service;

@Service
public class TrainingAssignmentTutorService {

    public String firstQuestion(TrainingActivityAssignment assignment) {
        return "What is your initial understanding of this activity?";
    }

    public String nextQuestion(TrainingActivityAssignment assignment, String answer) {
        var questionCount = assignment.getQuestionCount();
        if (questionCount >= 3) {
            return null;
        }
        return switch (questionCount) {
            case 1 -> "Which concept feels least clear after your first answer?";
            case 2 -> "Can you explain the idea with a concrete example?";
            default -> "What would you improve in your answer after reflecting on it?";
        };
    }

    public String finalReport(TrainingActivityAssignment assignment, String transcriptMarkdown) {
        return """
               # Evaluation report

               Activity: %s

               The student completed %d guided reflection step(s).

               ## Transcript

               %s
               """.formatted(
                assignment.getTrainingActivity().getTitle(),
                assignment.getQuestionCount(),
                transcriptMarkdown);
    }
}
