package com.wornux.dtos.profile;

import java.util.List;

public record StudentProfileSnapshot(String preferredLanguage, boolean needsConcreteExamples,
        List<String> activeMisconceptions, long profileVersion, StudentLearningProfile learningProfile) {

    public static StudentProfileSnapshot anonymous() {
        return new StudentProfileSnapshot("es", false, List.of(), 0L, StudentLearningProfile.empty("es"));
    }
}
