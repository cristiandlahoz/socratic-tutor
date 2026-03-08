package com.wornux.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        chatClient = chatClientBuilder.defaultAdvisors(chatMemoryAdvisor).build();
    }

    public Flux<String> chatStream(String userInput, String chatId) {
        return chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .user(userInput)
                .stream()
                .content();
    }
}
