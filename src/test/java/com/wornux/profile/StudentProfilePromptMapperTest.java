package com.wornux.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.ai.profile.StudentProfilePromptMapper;
import com.wornux.domain.profile.HelpMode;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.profile.StudentProfileSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentProfilePromptMapperTest {

  private final StudentProfilePromptMapper mapper = new StudentProfilePromptMapper();

  @Test
  void omitsNoisyEnumLevelWhenEvaluationEvidenceIsMissing() {
    var prompt = mapper.toPrompt(StudentProfileSnapshot.anonymous());

    assertThat(prompt).contains("Evaluation evidence: none yet");
    assertThat(prompt).doesNotContain("beginner");
    assertThat(prompt).doesNotContain("DEVELOPING");
  }

  @Test
  void emitsCompactEvidenceBackedProfile() {
    var signal =
        new StudentLearningProfile.LearningSignal(
            "loops",
            "loop tracing",
            "evaluation:attempt-1",
            2,
            Instant.parse("2026-05-08T00:00:00Z"),
            "score:82",
            false,
            false);
    var learningProfile =
        new StudentLearningProfile(
            List.of(signal),
            List.of(signal),
            List.of(),
            List.of(),
            List.of("guided"),
            "es",
            List.of("attempt-1"),
            List.of(),
            List.of("Use short traces."));
    var snapshot =
        new StudentProfileSnapshot(
            "es",
            HelpMode.GUIDED,
            false,
            List.of(),
            3,
            learningProfile);

    var prompt = mapper.toPrompt(snapshot);

    assertThat(prompt).contains("loop tracing");
    assertThat(prompt).contains("evaluation:attempt-1");
    assertThat(prompt).contains("Use short traces.");
    assertThat(prompt.length()).isLessThanOrEqualTo(1_600);
  }
}
