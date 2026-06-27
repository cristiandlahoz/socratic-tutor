package com.wornux.services.chat;

public class ChatStreamTimeoutException extends RuntimeException {

    public ChatStreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
