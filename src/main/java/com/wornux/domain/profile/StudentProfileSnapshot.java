package com.wornux.domain.profile;

import java.util.List;

public record StudentProfileSnapshot(
    String preferredLanguage,
    HelpMode helpMode,
    boolean needsConcreteExamples,
    List<String> activeMisconceptions,
    long profileVersion,
    StudentLearningProfile learningProfile) {

  public static StudentProfileSnapshot anonymous() {
    return new StudentProfileSnapshot(
        "es",
        HelpMode.GUIDED,
        false,
        List.of(),
        0L,
        StudentLearningProfile.empty("es"));
  }
}
