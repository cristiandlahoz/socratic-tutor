package com.wornux.domain.chat.questions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StudentQuestion(
    @JsonProperty(required = true)
        @JsonPropertyDescription("Stable id for this question, used to match the student's answer.")
        String id,
    @JsonProperty(required = true)
        @JsonPropertyDescription(
            "Short header shown above the question. Keep it brief, 1 to 3 words.")
        String header,
    @JsonProperty(required = true)
        @JsonPropertyDescription(
            "The full question shown to the student. End with a question mark.")
        String question,
    @JsonProperty(required = true)
        @JsonPropertyDescription("Selectable options. Provide 2 to 4 concrete options.")
        List<StudentQuestionOption> options,
    @JsonProperty(required = true)
        @JsonPropertyDescription("Whether the student can select more than one option.")
        boolean multiSelect)
    implements Serializable {

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
