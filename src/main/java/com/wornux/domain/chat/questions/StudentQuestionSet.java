package com.wornux.domain.chat.questions;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentQuestionSet(
        @JsonProperty(required = true)
        @JsonPropertyDescription("1 to 3 short questions used to collect structured student input during the tutoring flow.")
        @ArraySchema(
                minItems = 1,
                maxItems = 3,
                schema = @Schema(implementation = StudentQuestion.class))
        List<StudentQuestion> questions)
        implements Serializable {

    public StudentQuestionSet {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Question sets must contain between 1 and 3 questions");
        }
        if (questions.size() > 3) {
            throw new IllegalArgumentException("Question sets must contain between 1 and 3 questions");
        }
        questions = List.copyOf(questions);
    }
}
