package com.wornux.chat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private static final int TITLE_MAX_LENGTH = 72;

    private final ChatConversationJpaRepository chatConversationRepository;
    private final ChatMessageJpaRepository chatMessageRepository;

    public ConversationService(ChatConversationJpaRepository chatConversationRepository,
                               ChatMessageJpaRepository chatMessageRepository) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID clientId) {
        return chatConversationRepository.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId).stream()
                .map(this::toConversationSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoredChatMessage> loadConversation(UUID clientId, UUID conversationId) {
        return chatMessageRepository.findByConversation_IdAndConversation_ClientIdOrderByIdAsc(conversationId, clientId)
                .stream()
                .map(ChatMessageEntity::toStoredChatMessage)
                .toList();
    }

    @Transactional
    public ConversationSummary createConversation(UUID clientId, String firstUserPrompt) {
        var conversation = chatConversationRepository.save(ChatConversationEntity.create(clientId, toConversationTitle(firstUserPrompt)));
        return toConversationSummary(conversation);
    }

    @Transactional(readOnly = true)
    public ResolvedConversation resolveActiveConversation(UUID clientId, UUID requestedConversationId) {
        var conversationEntities = chatConversationRepository.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId);
        var conversations = conversationEntities.stream()
                .map(this::toConversationSummary)
                .toList();
        var resolvedConversationId = requestedConversationId;

        if (resolvedConversationId != null) {
            var requestedConversationExists = conversations.stream()
                    .map(ConversationSummary::id)
                    .anyMatch(resolvedConversationId::equals);
            if (!requestedConversationExists) {
                resolvedConversationId = null;
            }
        }

        if (resolvedConversationId == null && !conversations.isEmpty()) {
            resolvedConversationId = conversations.getFirst().id();
        }

        var messages = resolvedConversationId == null
                ? List.<StoredChatMessage>of()
                : loadConversation(clientId, resolvedConversationId);

        return new ResolvedConversation(resolvedConversationId, conversations, messages);
    }

    private ConversationSummary toConversationSummary(ChatConversationEntity conversation) {
        return new ConversationSummary(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
    }

    String toConversationTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "Nueva conversacion";
        }

        var normalized = prompt.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH - 3).trim() + "...";
    }
}
