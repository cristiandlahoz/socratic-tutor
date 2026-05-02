package com.wornux.application.chat;

import com.wornux.application.chat.port.ChatMessagePersistencePort;
import com.wornux.application.chat.port.ChatPersistencePort;
import com.wornux.application.chat.port.ChatTranscriptPersistencePort;
import com.wornux.domain.chat.ChatCompactionStatus;
import com.wornux.domain.chat.ChatEntity;
import com.wornux.domain.chat.ChatMessageEntity;
import com.wornux.domain.chat.ChatTranscriptEntity;
import com.wornux.domain.chat.ConversationSummary;
import com.wornux.domain.chat.ResolvedConversation;
import com.wornux.domain.chat.StoredChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private static final int TITLE_MAX_LENGTH = 72;

  private final ChatPersistencePort chatPort;
  private final ChatTranscriptPersistencePort chatTranscriptPort;
  private final ChatMessagePersistencePort chatMessagePort;

  public ConversationService(
      ChatPersistencePort chatPort,
      ChatTranscriptPersistencePort chatTranscriptPort,
      ChatMessagePersistencePort chatMessagePort) {
    this.chatPort = chatPort;
    this.chatTranscriptPort = chatTranscriptPort;
    this.chatMessagePort = chatMessagePort;
  }

  @Transactional(readOnly = true)
  public List<ConversationSummary> listConversations(UUID clientId) {
    return chatPort.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId).stream()
        .map(this::toConversationSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StoredChatMessage> loadConversation(UUID clientId, UUID conversationId) {
    return chatMessagePort.findDisplayMessages(conversationId, clientId).stream()
        .filter(message -> !message.isToolMessage())
        .map(ChatMessageEntity::toStoredChatMessage)
        .toList();
  }

  @Transactional
  public ConversationSummary createConversation(UUID clientId, String firstUserPrompt) {
    var chat = chatPort.save(ChatEntity.create(clientId, toConversationTitle(firstUserPrompt)));
    var transcript = chatTranscriptPort.save(ChatTranscriptEntity.create(chat));
    chat.activateTranscript(transcript);
    return toConversationSummary(chatPort.save(chat));
  }

  @Transactional(readOnly = true)
  public ResolvedConversation resolveActiveConversation(UUID clientId, UUID requestedConversationId) {
    var conversations =
        chatPort.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId).stream()
            .map(this::toConversationSummary)
            .toList();

    var resolvedConversationId = requestedConversationId;
    if (resolvedConversationId != null) {
      var requestedConversationExists =
          conversations.stream().map(ConversationSummary::id).anyMatch(resolvedConversationId::equals);
      if (!requestedConversationExists) {
        resolvedConversationId = null;
      }
    }

    if (resolvedConversationId == null && !conversations.isEmpty()) {
      resolvedConversationId = conversations.getFirst().id();
    }

    var messages =
        resolvedConversationId == null
            ? List.<StoredChatMessage>of()
            : loadConversation(clientId, resolvedConversationId);

    return new ResolvedConversation(resolvedConversationId, conversations, messages);
  }

  @Transactional
  public void renameConversationIfTitleMatches(
      UUID clientId, UUID conversationId, String expectedCurrentTitle, String candidateTitle) {
    if (expectedCurrentTitle == null || expectedCurrentTitle.isBlank()) {
      return;
    }

    var normalizedCandidateTitle = toConversationTitle(candidateTitle);
    var chat = chatPort.findByIdAndClientId(conversationId, clientId).orElse(null);
    if (chat == null
        || !expectedCurrentTitle.equals(chat.getTitle())
        || normalizedCandidateTitle.equals(chat.getTitle())) {
      return;
    }

    chat.rename(normalizedCandidateTitle);
    chatPort.save(chat);
  }

  @Transactional(readOnly = true)
  public ChatCompactionStatus getCompactionStatus(UUID clientId, UUID conversationId) {
    var chat = chatPort.findByIdAndClientId(conversationId, clientId).orElse(null);
    if (chat == null || chat.getCurrentTranscript() == null || !chat.getCurrentTranscript().isCompacted()) {
      return ChatCompactionStatus.none();
    }
    var transcript = chat.getCurrentTranscript();
    return new ChatCompactionStatus(
        true, transcript.getCompactionLevel(), transcript.getCompactedFromTranscriptId());
  }

  private ConversationSummary toConversationSummary(ChatEntity chat) {
    return new ConversationSummary(chat.getId(), chat.getTitle(), chat.getUpdatedAt());
  }

  String toConversationTitle(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return "Nueva conversación";
    }

    var normalized = prompt.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= TITLE_MAX_LENGTH) {
      return normalized;
    }
    return normalized.substring(0, TITLE_MAX_LENGTH - 3).trim() + "...";
  }
}
