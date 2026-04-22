package com.wornux.chat.questions;

import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.List;

public record StudentQuestionAnswer(
        String questionId,
        List<String> selectedOptionLabels,
        String customText
) implements Serializable {

    public StudentQuestionAnswer {
        if (!StringUtils.hasText(questionId)) {
            throw new IllegalArgumentException("Answer questionId is required");
        }
        selectedOptionLabels = selectedOptionLabels == null ? List.of() : List.copyOf(selectedOptionLabels);
        customText = customText == null ? "" : customText.trim();
    }

    public boolean hasContent() {
        return !selectedOptionLabels.isEmpty() || !customText.isBlank();
    }
}
