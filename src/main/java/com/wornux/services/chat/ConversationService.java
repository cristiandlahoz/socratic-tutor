package com.wornux.services.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.repositories.conversation.ConversationRepository;
import com.wornux.dtos.chat.ChatCompactionStatus;
import com.wornux.dtos.chat.ConversationMessage;
import com.wornux.dtos.chat.ConversationSummary;
import com.wornux.dtos.chat.ResolvedConversation;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final int TITLE_MAX_LENGTH = 72;

    private final ConversationRepository conversationRepository;
    private final ActiveAcademicContextResolver contextResolver;

    public ConversationService(
            ConversationRepository conversationRepository,
            ActiveAcademicContextResolver contextResolver) {
        this.conversationRepository = conversationRepository;
        this.contextResolver = contextResolver;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations() {
        return contextResolver.resolveCurrent()
                .map(
                    context -> conversationRepository
                            .findByGroupClassMember_IdOrderByUpdatedAtDesc(context.groupClassMemberId())
                            .stream()
                            .map(this::toConversationSummary)
                            .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> loadConversation(UUID conversationId) {
        var conversation = findOwnedConversation(conversationId).orElse(null);
        if (conversation == null || conversation.getCurrentSnapshot() == null) {
            return List.of();
        }
        return toConversationMessages(conversation.getCurrentSnapshot().getMessages());
    }

    @Transactional
    public ConversationSummary createConversation(String firstUserPrompt) {
        var context = contextResolver.requireCurrent();
        var conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setGroupClassMember(new com.wornux.data.entities.academic.GroupClassMember());
        conversation.getGroupClassMember().setId(context.groupClassMemberId());
        conversation.setTitle(toConversationTitle(firstUserPrompt));
        conversation.setVersion(0L);
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        return toConversationSummary(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public ResolvedConversation resolveActiveConversation(UUID requestedConversationId) {
        var conversations = listConversations();
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

        var messages = resolvedConversationId == null
                ? List.<ConversationMessage>of()
                : loadConversation(resolvedConversationId);

        return new ResolvedConversation(resolvedConversationId, conversations, messages);
    }

    @Transactional
    public void renameConversationIfTitleMatches(
            UUID conversationId,
            String expectedCurrentTitle,
            String candidateTitle) {
        if (expectedCurrentTitle == null || expectedCurrentTitle.isBlank()) {
            return;
        }

        var normalizedCandidateTitle = toConversationTitle(candidateTitle);
        var conversation = findOwnedConversation(conversationId).orElse(null);
        if (conversation == null
                || !expectedCurrentTitle.equals(conversation.getTitle())
                || normalizedCandidateTitle.equals(conversation.getTitle())) {
            return;
        }

        conversation.setTitle(normalizedCandidateTitle);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public ChatCompactionStatus getCompactionStatus(UUID conversationId) {
        var conversation = findOwnedConversation(conversationId).orElse(null);
        var snapshot = conversation == null ? null : conversation.getCurrentSnapshot();
        if (snapshot == null || snapshot.getCompactedAt() == null) {
            return ChatCompactionStatus.none();
        }
        return new ChatCompactionStatus(true,
                Math.toIntExact(snapshot.getSnapshotNo()),
                snapshot.getPreviousSnapshot() == null ? null : snapshot.getPreviousSnapshot().getId());
    }

    ConversationSummary toConversationSummary(Conversation conversation) {
        return new ConversationSummary(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
    }

    public List<ConversationMessage> toConversationMessages(List<Map<String, Object>> rawMessages) {
        return rawMessages.stream()
                .map(this::toConversationMessage)
                .filter(message -> message.role() != MessageType.TOOL)
                .toList();
    }

    public ConversationMessage toConversationMessage(Map<String, Object> rawMessage) {
        var roleValue = String.valueOf(rawMessage.getOrDefault("role", MessageType.ASSISTANT.name()));
        var contentValue = String.valueOf(rawMessage.getOrDefault("content", ""));
        var createdAtValue = String.valueOf(rawMessage.getOrDefault("createdAt", Instant.now().toString()));
        return new ConversationMessage(MessageType.valueOf(roleValue), contentValue, Instant.parse(createdAtValue));
    }

    String toConversationTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "New conversation";
        }

        var normalized = prompt.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH - 3).trim() + "...";
    }

    public Conversation requireOwnedConversation(UUID conversationId) {
        return findOwnedConversation(conversationId)
                .orElseThrow(() -> new SecurityException("Conversation does not belong to the active class member."));
    }

    public java.util.Optional<Conversation> findOwnedConversation(UUID conversationId) {
        return contextResolver.resolveCurrent()
                .flatMap(
                    context -> conversationRepository
                            .findByIdAndGroupClassMember_Id(conversationId, context.groupClassMemberId()));
    }
}
