package com.wornux.presentation.chat;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
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
