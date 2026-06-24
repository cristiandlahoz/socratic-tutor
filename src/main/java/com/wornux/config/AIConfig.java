package com.wornux.config;

import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private static final int CHAT_MEMORY_ADVISOR_ORDER = 100;

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            RetrieveInformationTool retrieveInformationTool,
            TutorPromptResources promptResources) {

        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).order(CHAT_MEMORY_ADVISOR_ORDER).build();

        return builder.defaultSystem(promptResources.baseIdentitySystemResource())
                .defaultOptions(OllamaChatOptions.builder().enableThinking())
                .defaultAdvisors(chatMemoryAdvisor)
                .defaultTools(retrieveInformationTool)
                .build();
    }
}
