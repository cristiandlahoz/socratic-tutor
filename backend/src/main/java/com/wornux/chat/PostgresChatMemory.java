package com.wornux.chat;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class PostgresChatMemory implements ChatMemory {

    private final ChatConversationJpaRepository chatConversationRepository;
    private final ChatMessageJpaRepository chatMessageRepository;
    private final int maxMessages;

    public PostgresChatMemory(ChatConversationJpaRepository chatConversationRepository,
                              ChatMessageJpaRepository chatMessageRepository,
                              ChatProperties chatProperties) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.maxMessages = chatProperties.getMemory().getMaxMessages();
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        var conversation = chatConversationRepository.findById(UUID.fromString(conversationId))
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));
        conversation.touch();
        chatConversationRepository.save(conversation);
        chatMessageRepository.saveAll(messages.stream()
                .map(message -> ChatMessageEntity.from(conversation, message))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        var messages = chatMessageRepository.findByConversation_Id(
                        UUID.fromString(conversationId),
                        PageRequest.of(0, maxMessages, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(ChatMessageEntity::toSpringAiMessage)
                .toList();
        var orderedMessages = new java.util.ArrayList<>(messages);
        Collections.reverse(orderedMessages);
        return orderedMessages;
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        var conversation = chatConversationRepository.findById(UUID.fromString(conversationId))
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));
        chatMessageRepository.deleteByConversation_Id(conversation.getId());
        conversation.touch();
        chatConversationRepository.save(conversation);
    }
}
