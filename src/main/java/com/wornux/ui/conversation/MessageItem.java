package com.wornux.ui.conversation;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;
import org.springframework.ai.chat.messages.MessageType;

public final class MessageItem {

    public enum Variant {
        USER("user"), ASSISTANT("assistant");

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
    private final boolean loading;
    private final boolean steered;
    @Getter
    private final String loadingLabel;
    private final boolean debuggableCodeBlocks;
    @Getter
    private String text;
    transient String clientText;
    transient boolean clientLoading;
    transient boolean clientSteered;
    transient MessagesList host;

    public MessageItem(String text, Instant time, String userName, Variant variant, boolean loading) {
        this(text, time, userName, variant, loading, true, false, null);
    }

    public MessageItem(String text, Instant time, String userName, Variant variant, boolean loading, boolean debuggableCodeBlocks) {
        this(text, time, userName, variant, loading, debuggableCodeBlocks, false, null);
    }

    public MessageItem(
            String text,
            Instant time,
            String userName,
            Variant variant,
            boolean loading,
            boolean debuggableCodeBlocks,
            String loadingLabel) {
        this(text, time, userName, variant, loading, debuggableCodeBlocks, false, loadingLabel);
    }

    public static MessageItem conversationMessage(
            MessageState message,
            String userName) {
        var state = Objects.requireNonNull(message, "message cannot be null");
        var variant = state.role() == MessageType.USER ? Variant.USER : Variant.ASSISTANT;
        return new MessageItem(
                state.content(),
                state.createdAt(),
                userName,
                variant,
                state.loading(),
                variant == Variant.ASSISTANT,
                state.steered(),
                null);
    }

    private MessageItem(
            String text,
            Instant time,
            String userName,
            Variant variant,
            boolean loading,
            boolean debuggableCodeBlocks,
            boolean steered,
            String loadingLabel) {
        this.text = text != null ? text : "";
        this.clientText = this.text;
        this.time = time != null ? time.toString() : "";
        this.userName = Objects.requireNonNull(userName, "userName cannot be null");
        this.variant = Objects.requireNonNull(variant, "variant cannot be null");
        this.loading = loading;
        this.loadingLabel = loadingLabel == null || loadingLabel.isBlank() ? null : loadingLabel;
        this.debuggableCodeBlocks = debuggableCodeBlocks;
        this.steered = steered;
        this.clientLoading = loading;
        this.clientSteered = steered;
    }

    public String getVariant() {
        return variant.value();
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isSteered() {
        return steered;
    }

    public boolean isDebuggableCodeBlocks() {
        return debuggableCodeBlocks;
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
