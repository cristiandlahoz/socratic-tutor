package com.wornux.services.chat;

import java.time.Instant;
import java.util.UUID;

import com.wornux.config.ChatProperties;
import com.wornux.dtos.chat.ChatTranscriptUsage;
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
    public void updateActiveTranscriptInputTokens(UUID conversationId, Integer inputTokens) {
        if (inputTokens == null) {
            return;
        }
        var conversation = conversationService.requireOwnedConversation(conversationId);
        var snapshot = conversation.getCurrentSnapshot();
        if (snapshot == null) {
            return;
        }
        snapshot.setTokenCount(inputTokens);
        conversation.setUpdatedAt(Instant.now());
        conversation.setCurrentSnapshot(snapshot);
    }

    @Transactional(readOnly = true)
    public ChatTranscriptUsage getActiveTranscriptUsage(UUID ignoredClientId, UUID conversationId) {
        var conversation = conversationService.findOwnedConversation(conversationId).orElse(null);
        if (conversation == null || conversation.getCurrentSnapshot() == null) {
            return ChatTranscriptUsage.empty();
        }
        var inputTokens = conversation.getCurrentSnapshot().getTokenCount();
        if (inputTokens <= 0) {
            return ChatTranscriptUsage.empty();
        }
        return new ChatTranscriptUsage(inputTokens, usagePercent(inputTokens));
    }

    @Transactional(readOnly = true)
    public boolean exceedsCompactionThreshold(UUID conversationId) {
        var conversation = conversationService.findOwnedConversation(conversationId).orElse(null);
        if (conversation == null || conversation.getCurrentSnapshot() == null) {
            return false;
        }
        return exceedsCompactionThreshold(conversation.getCurrentSnapshot().getTokenCount());
    }

    boolean exceedsCompactionThreshold(int inputTokens) {
        return inputTokens > thresholdTokens();
    }

    int thresholdTokens() {
        int threshold = (int) Math
                .floor(chatProperties.getContextWindowTokens() * chatProperties.getCompactionThresholdRatio());
        if (threshold <= 0) {
            throw new IllegalStateException("Chat compaction threshold must be greater than zero");
        }
        return threshold;
    }

    int usagePercent(int inputTokens) {
        return (int) Math.round(inputTokens * 100.0 / thresholdTokens());
    }
}
