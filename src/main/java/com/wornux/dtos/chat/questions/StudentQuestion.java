package com.wornux.dtos.chat.questions;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentQuestion(
        @JsonProperty(required = true) @JsonPropertyDescription("The full question shown to the student") @Schema(
                pattern = "^.*\\?$") String question,

        @JsonPropertyDescription("Optional selectable options. Provide up to 3 concrete options only when the question is naturally categorical or multi-choice.") @ArraySchema(
                maxItems = 3,
                schema = @Schema(implementation = StudentQuestionOption.class)) List<StudentQuestionOption> options)
        implements Serializable {

    public StudentQuestion {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (options == null) {
            options = List.of();
        }
        if (options.size() > 3) {
            throw new IllegalArgumentException("Questions must have at most 3 options");
        }
        options = List.copyOf(options);
    }
}
