package com.wornux.chat;

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
