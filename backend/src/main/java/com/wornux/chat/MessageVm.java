package com.wornux.chat;

import java.time.Instant;
import org.springframework.ai.chat.messages.MessageType;

public record MessageVm(MessageType role, String content, Instant createdAt, boolean loading) {

  public static MessageVm fromStored(StoredChatMessage message) {
    return new MessageVm(message.role(), message.content(), message.createdAt(), false);
  }

  public static MessageVm user(String content, Instant createdAt) {
    return new MessageVm(MessageType.USER, content, createdAt, false);
  }

  public static MessageVm assistantLoading(Instant createdAt) {
    return new MessageVm(MessageType.ASSISTANT, "", createdAt, true);
  }

  public MessageVm append(String token) {
    return new MessageVm(role, content + token, createdAt, false);
  }

  public MessageVm stopLoading() {
    return new MessageVm(role, content, createdAt, false);
  }

  public MessageVm fallback(String fallbackContent) {
    var nextContent = content.isBlank() ? fallbackContent : content;
    return new MessageVm(role, nextContent, createdAt, false);
  }
}
