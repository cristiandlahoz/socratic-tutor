package com.wornux.dtos.chat.questions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;

public record StudentQuestionSet(
    @JsonProperty(required = false)
        @JsonPropertyDescription(
            "Up to 3 short questions for collecting student context, observable progress, or"
                + " concise pedagogical input. Questions may be open-ended; selectable options are"
                + " optional and should be used only when they add real value.")
        @ArraySchema(maxItems = 3, schema = @Schema(implementation = StudentQuestion.class))
        List<StudentQuestion> questions)
    implements Serializable {

  public StudentQuestionSet {
    if (questions.size() > 3) {
      throw new IllegalArgumentException("Question sets must contain up to 3 questions");
    }
    questions = List.copyOf(questions);
  }
}
