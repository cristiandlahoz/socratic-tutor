package com.wornux.ai.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.data.repositories.conversation.ConversationSnapshotRepository;
import com.wornux.services.chat.ConversationService;
import org.jspecify.annotations.NullMarked;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@NullMarked
public class PostgresChatMemory implements ChatMemory {

    private final ConversationSnapshotRepository snapshotRepository;
    private final ConversationService conversationService;

    public PostgresChatMemory(
            ConversationSnapshotRepository snapshotRepository,
            ConversationService conversationService) {
        this.snapshotRepository = snapshotRepository;
        this.conversationService = conversationService;
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        var conversation = conversationService.requireOwnedConversation(UUID.fromString(conversationId));
        var previousSnapshot = conversation.getCurrentSnapshot();

        List<Map<String, Object>> nextMessages = new ArrayList<>(
                previousSnapshot == null ? List.<Map<String, Object>>of() : previousSnapshot.getMessages());
        messages.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(this::toMessageMap)
                .forEach(nextMessages::add);

        var snapshot = new ConversationSnapshot();
        snapshot.setConversation(conversation);
        snapshot.setPreviousSnapshot(previousSnapshot);
        snapshot.setSnapshotNo(previousSnapshot == null ? 1L : previousSnapshot.getSnapshotNo() + 1L);
        snapshot.setCarryContext(previousSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previousSnapshot.getCarryContext()));
        snapshot.setMessages(List.copyOf(nextMessages));
        snapshot.setMessageCount(nextMessages.size());
        snapshot.setTokenCount(previousSnapshot == null ? 0 : previousSnapshot.getTokenCount());
        snapshot.setVersion(previousSnapshot == null ? 1L : previousSnapshot.getVersion() + 1L);
        snapshot.setCreatedAt(Instant.now());

        var persistedSnapshot = snapshotRepository.save(snapshot);
        conversation.setCurrentSnapshot(persistedSnapshot);
        conversation.setVersion(conversation.getVersion() + 1L);
        conversation.setUpdatedAt(Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        var conversation = conversationService.requireOwnedConversation(UUID.fromString(conversationId));
        var snapshot = conversation.getCurrentSnapshot();
        if (snapshot == null) {
            return List.of();
        }

        var messages = new ArrayList<Message>();
        var carryContextText = String.valueOf(snapshot.getCarryContext().getOrDefault("text", ""));
        if (!carryContextText.isBlank()) {
            messages.add(AssistantMessage.builder().content(carryContextText).properties(Map.of("memory", true)).build());
        }
        conversationService.toStoredMessages(snapshot.getMessages()).forEach(message -> messages.add(toSpringMessage(message)));
        return List.copyOf(messages);
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        var conversation = conversationService.requireOwnedConversation(UUID.fromString(conversationId));
        var previousSnapshot = conversation.getCurrentSnapshot();

        var snapshot = new ConversationSnapshot();
        snapshot.setConversation(conversation);
        snapshot.setPreviousSnapshot(previousSnapshot);
        snapshot.setSnapshotNo(previousSnapshot == null ? 1L : previousSnapshot.getSnapshotNo() + 1L);
        snapshot.setCarryContext(new LinkedHashMap<>());
        snapshot.setMessages(List.of());
        snapshot.setMessageCount(0);
        snapshot.setTokenCount(0);
        snapshot.setVersion(previousSnapshot == null ? 1L : previousSnapshot.getVersion() + 1L);
        snapshot.setCreatedAt(Instant.now());

        conversation.setCurrentSnapshot(snapshotRepository.save(snapshot));
        conversation.setVersion(conversation.getVersion() + 1L);
        conversation.setUpdatedAt(Instant.now());
    }

    private Map<String, Object> toMessageMap(Message message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", message.getMessageType().name());
        value.put("content", message.getText());
        value.put("createdAt", Instant.now().toString());
        return value;
    }

    private Message toSpringMessage(com.wornux.dtos.chat.StoredChatMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
            default -> new AssistantMessage(message.content());
        };
    }
}
