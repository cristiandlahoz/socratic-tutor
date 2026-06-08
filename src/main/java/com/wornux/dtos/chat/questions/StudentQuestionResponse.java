package com.wornux.dtos.chat.questions;

import java.io.Serializable;
import java.util.List;

public record StudentQuestionResponse(List<StudentQuestionAnswer> answers) implements Serializable {

    public StudentQuestionResponse {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("At least one answer is required");
        }
        answers = List.copyOf(answers);
    }
}
