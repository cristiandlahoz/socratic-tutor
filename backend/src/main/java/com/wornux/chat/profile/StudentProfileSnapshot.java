package com.wornux.chat.profile;

import java.math.BigDecimal;
import java.util.List;

public record StudentProfileSnapshot(
        String preferredLanguage,
        StudentOverallLevel overallLevel,
        HelpMode helpMode,
        boolean needsConcreteExamples,
        List<TopicKey> topWeakTopics,
        List<String> activeMisconceptions,
        BigDecimal confidenceScore,
        long profileVersion
) {

    public static StudentProfileSnapshot anonymous() {
        return new StudentProfileSnapshot(
                "es",
                StudentOverallLevel.DEVELOPING,
                HelpMode.GUIDED,
                false,
                List.of(),
                List.of(),
                new BigDecimal("0.500"),
                0L
        );
    }
}
