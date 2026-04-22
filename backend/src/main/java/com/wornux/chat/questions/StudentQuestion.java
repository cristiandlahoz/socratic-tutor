package com.wornux.chat.questions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StudentQuestion(
        @JsonPropertyDescription("Stable id for this question, used to match the student's answer.")
        String id,
        @JsonPropertyDescription("Short header shown above the question. Keep it brief, 1 to 3 words.")
        String header,
        @JsonPropertyDescription("The full question shown to the student. End with a question mark.")
        String question,
        @JsonPropertyDescription("Selectable options. Provide 2 to 4 concrete options.")
        List<StudentQuestionOption> options,
        @JsonPropertyDescription("Whether the student can select more than one option.")
        boolean multiSelect
) implements Serializable {

    public StudentQuestion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Question id is required");
        }
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("Question header is required");
        }
        if (header.length() > 24) {
            throw new IllegalArgumentException("Question header must be 24 characters or fewer");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (options == null || options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("Questions must have between 2 and 4 options");
        }
        options = List.copyOf(options);
        Set<String> labels = new LinkedHashSet<>();
        for (StudentQuestionOption option : options) {
            if (!labels.add(option.label())) {
                throw new IllegalArgumentException("Question options must have unique labels");
            }
        }
    }
}
