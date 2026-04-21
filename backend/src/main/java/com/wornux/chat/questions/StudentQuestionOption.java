package com.wornux.chat.questions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;

public record StudentQuestionOption(
        @JsonPropertyDescription("Short label shown as the selectable answer text.")
        String label,
        @JsonPropertyDescription("Short explanation that clarifies what this option means for the student.")
        String description
) implements Serializable {

    public StudentQuestionOption {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Option label is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Option description is required");
        }
    }
}
