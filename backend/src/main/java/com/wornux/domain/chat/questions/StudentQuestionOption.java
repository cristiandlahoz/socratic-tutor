package com.wornux.domain.chat.questions;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record StudentQuestionOption(
    @JsonPropertyDescription("Short label shown as the selectable answer text.")
        @JsonProperty(required = true)
        String label,
    @JsonPropertyDescription(
            "Short explanation that clarifies what this option means for the student.")
        @JsonProperty(required = true)
        String description)
    implements Serializable {}
