package com.wornux.domain.chat.questions;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentQuestion(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The full question shown to the student. End with a question mark.")
        @Schema(pattern = "^.*\\?$")
        String question,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Selectable options. Provide 1 to 4 concrete options.")
        @ArraySchema(
                minItems = 1,
                maxItems = 4,
                schema = @Schema(implementation = StudentQuestionOption.class))
        List<StudentQuestionOption> options)
        implements Serializable {

    public StudentQuestion {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (options == null || options.isEmpty() || options.size() > 4) {
            throw new IllegalArgumentException("Questions must have between 1 and 4 options");
        }
        options = List.copyOf(options);
    }
}
