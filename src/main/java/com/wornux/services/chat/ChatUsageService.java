package com.wornux.services.chat;

import java.time.Instant;
import java.util.UUID;

import com.wornux.config.ChatProperties;
import com.wornux.dtos.chat.ConversationTokenUsage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatUsageService {

    private final ConversationService conversationService;
    private final ChatProperties chatProperties;

    public ChatUsageService(ConversationService conversationService, ChatProperties chatProperties) {
        this.conversationService = conversationService;
        this.chatProperties = chatProperties;
    }

    @Transactional
    public void updateConversationInputTokens(UUID conversationId, Integer inputTokens) {
        if (inputTokens == null) {
            return;
        }
        var conversation = conversationService.requireOwnedConversation(conversationId);
        conversation.setLastPromptTokens(inputTokens);
        conversation.setUpdatedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public ConversationTokenUsage getConversationTokenUsage(UUID conversationId) {
        var conversation = conversationService.findOwnedConversation(conversationId).orElse(null);
        if (conversation == null || conversation.getLastPromptTokens() == null) {
            return ConversationTokenUsage.empty();
        }
        var inputTokens = conversation.getLastPromptTokens();
        if (inputTokens <= 0) {
            return ConversationTokenUsage.empty();
        }
        return new ConversationTokenUsage(inputTokens, usagePercent(inputTokens));
    }

    int thresholdTokens() {
        return chatProperties.compactionThresholdTokens();
    }

    int usagePercent(int inputTokens) {
        return (int) Math.round(inputTokens * 100.0 / thresholdTokens());
    }
}
