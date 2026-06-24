package com.wornux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    private int contextWindowTokens;
    private double compactionThresholdRatio;
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

    public int compactionThresholdTokens() {
        int threshold = (int) Math.floor(contextWindowTokens * compactionThresholdRatio);
        if (threshold <= 0) {
            throw new IllegalStateException("Chat compaction threshold must be greater than zero");
        }
        return threshold;
    }

    public Ui getUi() {
        return ui;
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    public static class Ui {

        private String thinkingSpinner;

        public String getThinkingSpinner() {
            return thinkingSpinner;
        }

        public void setThinkingSpinner(String thinkingSpinner) {
            this.thinkingSpinner = thinkingSpinner;
        }
    }
}
