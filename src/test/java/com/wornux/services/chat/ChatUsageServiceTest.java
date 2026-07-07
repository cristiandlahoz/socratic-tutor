package com.wornux.services.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.wornux.config.ChatProperties;
import com.wornux.data.entities.conversation.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatUsageServiceTest {

    @Mock
    private ConversationService conversationService;

    private ChatUsageService chatUsageService;

    @BeforeEach
    void setUp() {
        var properties = new ChatProperties();
        properties.setContextWindowTokens(10_000);
        properties.setCompactionThresholdRatio(0.5);
        chatUsageService = new ChatUsageService(conversationService, properties);
    }

    @Test
    void readsActualProviderPromptTokensFromTheDomainConversation() {
        var conversationId = UUID.randomUUID();
        var conversation = new Conversation();
        conversation.setLastPromptTokens(1_250);
        when(conversationService.findOwnedConversation(conversationId)).thenReturn(Optional.of(conversation));

        var usage = chatUsageService.getConversationTokenUsage(conversationId);

        assertThat(usage.inputTokens()).isEqualTo(1_250);
        assertThat(usage.usagePercent()).isEqualTo(25);
    }

    @Test
    void returnsEmptyUsageWhenProviderUsageIsMissing() {
        var conversationId = UUID.randomUUID();
        var conversation = new Conversation();
        when(conversationService.findOwnedConversation(conversationId)).thenReturn(Optional.of(conversation));

        assertThat(chatUsageService.getConversationTokenUsage(conversationId).inputTokens()).isNull();
        assertThat(chatUsageService.getConversationTokenUsage(conversationId).usagePercent()).isNull();
    }
}
