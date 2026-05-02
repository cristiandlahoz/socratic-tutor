package com.wornux.ai.memory;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
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
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Component
@NullMarked
public class PostgresChatMemory implements ChatMemory {

  private final ChatJpaRepository chatRepository;
  private final ChatTranscriptJpaRepository chatTranscriptRepository;
  private final ChatMessageJpaRepository chatMessageRepository;

  public PostgresChatMemory(
      ChatJpaRepository chatRepository,
      ChatTranscriptJpaRepository chatTranscriptRepository,
      ChatMessageJpaRepository chatMessageRepository) {
    this.chatRepository = chatRepository;
    this.chatTranscriptRepository = chatTranscriptRepository;
    this.chatMessageRepository = chatMessageRepository;
  }

  @Override
  @Transactional
  public void add(String conversationId, List<Message> messages) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");
    Assert.notNull(messages, "messages cannot be null");
    Assert.noNullElements(messages, "messages cannot contain null elements");
    var chat =
        chatRepository
            .findById(UUID.fromString(conversationId))
            .orElseThrow(() -> new IllegalStateException("Chat not found: " + conversationId));
    var transcript = currentTranscript(chat);
    chat.touch();
    chatRepository.save(chat);
    chatMessageRepository.saveAll(
        messages.stream()
            .filter(message -> message.getMessageType() != MessageType.SYSTEM)
            .map(message -> ChatMessageEntity.from(transcript, message))
            .toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<Message> get(String conversationId) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");
    var chat =
        chatRepository
            .findById(UUID.fromString(conversationId))
            .orElseThrow(() -> new IllegalStateException("Chat not found: " + conversationId));
    var transcript = chat.getCurrentTranscript();
    if (transcript == null) {
      return List.of();
    }

    var messages =
        chatMessageRepository.findByTranscript_IdOrderByIdAsc(transcript.getId()).stream()
            .map(ChatMessageEntity::toSpringAiMessage)
            .toList();

    var memoryText = transcript.memoryText();
    if (memoryText.isBlank()) {
      return messages;
    }

    var memoryMessage =
        AssistantMessage.builder().content(memoryText).properties(Map.of("memory", true)).build();
    var orderedMessages = new java.util.ArrayList<Message>(messages.size() + 1);
    orderedMessages.add(memoryMessage);
    orderedMessages.addAll(messages);
    return orderedMessages;
  }

  @Override
  @Transactional
  public void clear(String conversationId) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");
    var chat =
        chatRepository
            .findById(UUID.fromString(conversationId))
            .orElseThrow(() -> new IllegalStateException("Chat not found: " + conversationId));
    chatTranscriptRepository.deleteByChat_Id(chat.getId());
    var freshTranscript = chatTranscriptRepository.save(ChatTranscriptEntity.create(chat));
    chat.activateTranscript(freshTranscript);
    chatRepository.save(chat);
  }

  private ChatTranscriptEntity currentTranscript(ChatEntity chat) {
    var transcript = chat.getCurrentTranscript();
    if (transcript != null) {
      return transcript;
    }
    var createdTranscript = chatTranscriptRepository.save(ChatTranscriptEntity.create(chat));
    chat.activateTranscript(createdTranscript);
    chatRepository.save(chat);
    return createdTranscript;
  }
}
