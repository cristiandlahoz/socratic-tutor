package com.wornux.services.chat;

import com.wornux.domain.chat.ChatCompactionStatus;
import com.wornux.data.entities.Chat;
import com.wornux.data.entities.ChatMessage;
import com.wornux.data.entities.ChatTranscript;
import com.wornux.domain.chat.ConversationSummary;
import com.wornux.domain.chat.ResolvedConversation;
import com.wornux.domain.chat.StoredChatMessage;
import com.wornux.data.repositories.chat.ChatRepository;
import com.wornux.data.repositories.chat.ChatMessageRepository;
import com.wornux.data.repositories.chat.ChatTranscriptRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private static final int TITLE_MAX_LENGTH = 72;

  private final ChatRepository chatRepository;
  private final ChatTranscriptRepository chatTranscriptRepository;
  private final ChatMessageRepository chatMessageRepository;

  public ConversationService(
      ChatRepository chatRepository,
      ChatTranscriptRepository chatTranscriptRepository,
      ChatMessageRepository chatMessageRepository) {
    this.chatRepository = chatRepository;
    this.chatTranscriptRepository = chatTranscriptRepository;
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional(readOnly = true)
  public List<ConversationSummary> listConversations(UUID clientId) {
    return chatRepository.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId).stream()
        .map(this::toConversationSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StoredChatMessage> loadConversation(UUID clientId, UUID conversationId) {
    return chatMessageRepository.findDisplayMessages(conversationId, clientId).stream()
        .filter(message -> !message.isToolMessage())
        .map(ChatMessage::toStoredChatMessage)
        .toList();
  }

  @Transactional
  public ConversationSummary createConversation(UUID clientId, String firstUserPrompt) {
    var chat = chatRepository.save(Chat.create(clientId, toConversationTitle(firstUserPrompt)));
    var transcript = chatTranscriptRepository.save(ChatTranscript.create(chat));
    chat.activateTranscript(transcript);
    return toConversationSummary(chatRepository.save(chat));
  }

  @Transactional(readOnly = true)
  public ResolvedConversation resolveActiveConversation(
      UUID clientId, UUID requestedConversationId) {
    var conversations =
        chatRepository.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId).stream()
            .map(this::toConversationSummary)
            .toList();

    var resolvedConversationId = requestedConversationId;
    if (resolvedConversationId != null) {
      var requestedConversationExists =
          conversations.stream()
              .map(ConversationSummary::id)
              .anyMatch(resolvedConversationId::equals);
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
    var chat = chatRepository.findByIdAndClientId(conversationId, clientId).orElse(null);
    if (chat == null
        || !expectedCurrentTitle.equals(chat.getTitle())
        || normalizedCandidateTitle.equals(chat.getTitle())) {
      return;
    }

    chat.rename(normalizedCandidateTitle);
    chatRepository.save(chat);
  }

  @Transactional(readOnly = true)
  public ChatCompactionStatus getCompactionStatus(UUID clientId, UUID conversationId) {
    var chat = chatRepository.findByIdAndClientId(conversationId, clientId).orElse(null);
    if (chat == null
        || chat.getCurrentTranscript() == null
        || !chat.getCurrentTranscript().isCompacted()) {
      return ChatCompactionStatus.none();
    }
    var transcript = chat.getCurrentTranscript();
    return new ChatCompactionStatus(
        true, transcript.getCompactionLevel(), transcript.getCompactedFromTranscriptId());
  }

  private ConversationSummary toConversationSummary(Chat chat) {
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
