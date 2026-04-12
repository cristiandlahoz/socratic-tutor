package com.wornux.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    private String clientIdCookieName = "st_client_id";
    private final Memory memory = new Memory();
    private final Ui ui = new Ui();

    public String getClientIdCookieName() {
        return clientIdCookieName;
    }

    public void setClientIdCookieName(String clientIdCookieName) {
        this.clientIdCookieName = clientIdCookieName;
    }

    public Memory getMemory() {
        return memory;
    }

    public Ui getUi() {
        return ui;
    }

    public static class Memory {

        private int maxMessages = 20;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

    }

    public static class Ui {

        private String thinkingSpinner = "braille";

        public String getThinkingSpinner() {
            return thinkingSpinner;
        }

        public void setThinkingSpinner(String thinkingSpinner) {
            this.thinkingSpinner = thinkingSpinner;
        }
    }
}
