package com.wornux.ai.profile;

import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.profile.StudentProfileSnapshot;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudentProfilePromptMapper {

  private static final int MAX_PROFILE_CHARS = 1_600;

  public String toPrompt(StudentProfileSnapshot profile) {
    var learningProfile = profile.learningProfile();
    if (learningProfile == null || !learningProfile.hasEvidence()) {
      return """
      Student profile evidence:
      - Language: %s
      - Evaluation evidence: none yet

      Adaptation:
      - Do not infer a global level from missing data.
      - Ask for observable work before assuming mastery or confusion.
      """
          .formatted(profile.preferredLanguage());
    }

    var prompt =
        """
        Student profile evidence:
        - Language: %s
        - Recent evidence ids: %s
        - Strengths: %s
        - Weak concepts: %s
        - Active misconceptions: %s
        - Uncertainty notes: %s

        Adaptation:
        %s
        """
            .formatted(
                learningProfile.preferredLanguage(),
                learningProfile.recentEvidenceIds().stream().limit(4).toList(),
                summarizeSignals(learningProfile.strengths(), 3),
                summarizeSignals(learningProfile.weakConcepts(), 4),
                summarizeSignals(learningProfile.activeMisconceptions(), 4),
                learningProfile.uncertaintyNotes().stream().limit(3).toList(),
                adaptationLines(learningProfile));
    return prompt.length() <= MAX_PROFILE_CHARS ? prompt : prompt.substring(0, MAX_PROFILE_CHARS);
  }

  private List<String> summarizeSignals(
      List<StudentLearningProfile.LearningSignal> signals, int limit) {
    return signals.stream()
        .sorted(
            Comparator.comparing(StudentLearningProfile.LearningSignal::needsMoreEvidence)
                .thenComparing(StudentLearningProfile.LearningSignal::evidenceCount)
                .reversed())
        .limit(limit)
        .map(
            signal ->
                "%s [evidence=%d, source=%s, rubric=%s%s]"
                    .formatted(
                        signal.label(),
                        signal.evidenceCount(),
                        signal.evidenceSource(),
                        signal.rubricMatch(),
                        signal.needsMoreEvidence() ? ", needs more evidence" : ""))
        .toList();
  }

  private String adaptationLines(StudentLearningProfile learningProfile) {
    var guidance = learningProfile.tutorAdaptationGuidance().stream().limit(5).toList();
    if (guidance.isEmpty()) {
      return "- Use the evidence above only; do not rely on vague beginner/intermediate labels.";
    }
    return guidance.stream().map("- "::concat).reduce((a, b) -> a + "\n" + b).orElse("");
  }
}
