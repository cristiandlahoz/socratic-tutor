package com.wornux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    private static final double COMPOSER_PROMPT_LIMIT_RATIO = 0.40;

    private int contextWindowTokens;
    private double compactionThresholdRatio;
    private double recentHistoryRetentionRatio;
    private Ui ui = new Ui();

    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    public void setContextWindowTokens(int contextWindowTokens) {
        this.contextWindowTokens = contextWindowTokens;
    }

    public double getCompactionThresholdRatio() {
        return compactionThresholdRatio;
    }

    public void setCompactionThresholdRatio(double compactionThresholdRatio) {
        this.compactionThresholdRatio = compactionThresholdRatio;
    }

    public double getRecentHistoryRetentionRatio() {
        return recentHistoryRetentionRatio;
    }

    public void setRecentHistoryRetentionRatio(double recentHistoryRetentionRatio) {
        this.recentHistoryRetentionRatio = recentHistoryRetentionRatio;
    }

    public int compactionThresholdTokens() {
        int threshold = (int) Math.floor(contextWindowTokens * compactionThresholdRatio);
        if (threshold <= 0) {
            throw new IllegalStateException("Chat compaction threshold must be greater than zero");
        }
        return threshold;
    }

    public int composerPromptLimit() {
        int limit = (int) Math.floor(contextWindowTokens * COMPOSER_PROMPT_LIMIT_RATIO);
        if (limit <= 0) {
            throw new IllegalStateException("Composer prompt limit must be greater than zero");
        }
        return limit;
    }

    public int recentHistoryRetentionTokens() {
        int limit = (int) Math.floor(contextWindowTokens * recentHistoryRetentionRatio);
        if (limit <= 0) {
            throw new IllegalStateException("Recent history retention must be greater than zero");
        }
        return limit;
    }

    public Ui getUi() {
        return ui;
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    public static class Ui {

        private String thinkingSpinner = "";

        public String getThinkingSpinner() {
            return thinkingSpinner;
        }

        public void setThinkingSpinner(String thinkingSpinner) {
            this.thinkingSpinner = thinkingSpinner;
        }
    }
}
