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
import org.springframework.util.StringUtils;

public record StudentQuestionSet(
    @JsonPropertyDescription("Short title shown at the top of the question panel.")
        @JsonProperty(required = true)
        String title,
    @JsonPropertyDescription(
            "Why the tutor is asking this structured question set. Examples: diagnosis, preference,"
                + " decision, clarification, feedback, next_step.")
        @JsonProperty(required = true)
        String purpose,
    @JsonPropertyDescription(
            "Whether these answers should update the student's pedagogical profile. Use PEDAGOGICAL"
                + " only when the answers reveal level, confusion, confidence, or help"
                + " preferences.")
        @JsonProperty(required = true)
        ProfileImpact profileImpact,
    @JsonPropertyDescription(
            "1 to 3 short questions used to collect structured student input during the tutoring"
                + " flow.")
        @JsonProperty(required = true)
        List<StudentQuestion> questions)
    implements Serializable {

  public StudentQuestionSet {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("Question set title is required");
    }
    if (!StringUtils.hasText(purpose)) {
      throw new IllegalArgumentException("Question set purpose is required");
    }
    if (profileImpact == null) {
      throw new IllegalArgumentException("Question set profileImpact is required");
    }
    if (questions == null || questions.isEmpty()) {
      throw new IllegalArgumentException("Question sets must contain between 1 and 3 questions");
    }
    if (questions.size() > 3) {
      throw new IllegalArgumentException("Question sets must contain between 1 and 3 questions");
    }
    questions = List.copyOf(questions);
    Set<String> ids = new LinkedHashSet<>();
    for (StudentQuestion question : questions) {
      if (!ids.add(question.id())) {
        throw new IllegalArgumentException("Question ids must be unique within a question set");
      }
    }
  }

  public enum ProfileImpact {
    NONE,
    PEDAGOGICAL
  }
}
