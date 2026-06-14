package com.wornux.dtos.chat.questions;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.util.StringUtils;

public record StudentQuestionAnswer(@JsonProperty(
        required = true) @JsonPropertyDescription("Stable id of the question being answered.") String questionId,
        @JsonProperty(
                required = true) @JsonPropertyDescription("Selected option labels. Leave empty when only custom text is provided.") List<String> selectedOptionLabels,
        @JsonProperty(
                required = true) @JsonPropertyDescription("Complementary free-text answer. Use an empty string when omitted.") String customText)
        implements Serializable {

    public StudentQuestionAnswer {
        if (!StringUtils.hasText(questionId)) {
            throw new IllegalArgumentException("Answer questionId is required");
        }
        selectedOptionLabels = selectedOptionLabels == null ? List.of() : List.copyOf(selectedOptionLabels);
        customText = customText == null ? "" : customText.trim();
    }
}
