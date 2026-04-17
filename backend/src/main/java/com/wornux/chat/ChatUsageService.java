package com.wornux.chat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChatUsageService {

    private final ChatJpaRepository chatRepository;
    private final ChatProperties chatProperties;

    public ChatUsageService(ChatJpaRepository chatRepository, ChatProperties chatProperties) {
        this.chatRepository = chatRepository;
        this.chatProperties = chatProperties;
    }

    @Transactional
    public void updateActiveTranscriptInputTokens(UUID chatId, Integer inputTokens) {
        if (inputTokens == null) {
            return;
        }
        var chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Chat not found: " + chatId));
        var transcript = chat.getCurrentTranscript();
        if (transcript == null) {
            throw new IllegalStateException("Active transcript not found for chat: " + chatId);
        }
        transcript.setInputTokens(inputTokens);
        chat.touch();
        chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public ChatTranscriptUsage getActiveTranscriptUsage(UUID clientId, UUID chatId) {
        var chat = chatRepository.findByIdAndClientId(chatId, clientId).orElse(null);
        if (chat == null || chat.getCurrentTranscript() == null || chat.getCurrentTranscript().getInputTokens() == null) {
            return ChatTranscriptUsage.empty();
        }
        var inputTokens = chat.getCurrentTranscript().getInputTokens();
        return new ChatTranscriptUsage(inputTokens, usagePercent(inputTokens));
    }

    int usagePercent(int inputTokens) {
        int ceiling = chatProperties.getContextWindowTokens() - chatProperties.getReservedOutputTokens();
        if (ceiling <= 0) {
            throw new IllegalStateException("Chat context ceiling must be greater than zero");
        }
        return (int) Math.round(inputTokens * 100.0 / ceiling);
    }
}
