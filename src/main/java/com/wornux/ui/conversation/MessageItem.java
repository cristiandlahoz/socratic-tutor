package com.wornux.ui.conversation;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;

public final class MessageItem {

    public enum Variant {
        USER("user"),
        ASSISTANT("assistant");

        private final String value;

        Variant(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @Getter
    private final String time;
    @Getter
    private final String userName;
    private final Variant variant;
    @Getter
    private final boolean loading;
    @Getter
    private String text;
    transient String clientText;
    transient MessagesList host;

    public MessageItem(String text, Instant time, String userName, Variant variant, boolean loading) {
        this.text = text != null ? text : "";
        this.clientText = this.text;
        this.time = time != null ? time.toString() : "";
        this.userName = Objects.requireNonNull(userName, "userName cannot be null");
        this.variant = Objects.requireNonNull(variant, "variant cannot be null");
        this.loading = loading;
    }

    public String getVariant() {
        return variant.value();
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        if (host != null) {
            host.scheduleItemsTextUpdate();
        }
    }

    void setHost(MessagesList host) {
        this.host = host;
    }
}
