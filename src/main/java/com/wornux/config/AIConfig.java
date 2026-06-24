package com.wornux.config;

import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.RecursiveSummarizationCompactionStrategy;
import org.springframework.ai.session.compaction.TokenCountTrigger;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private static final int RETAINED_SESSION_EVENT_COUNT = 4;

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            ChatModel chatModel,
            SessionService sessionService,
            ChatProperties chatProperties,
            RetrieveInformationTool retrieveInformationTool,
            TutorPromptResources promptResources) {

        var compactionClient = ChatClient.builder(chatModel).build();
        var tokenCountEstimator = new JTokkitTokenCountEstimator();
        var sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionService)
                .eventFilter(EventFilter.active())
                .compactionTrigger(
                    TokenCountTrigger.builder()
                            .threshold(chatProperties.compactionThresholdTokens())
                            .tokenCountEstimator(tokenCountEstimator)
                            .build())
                .compactionStrategy(
                    RecursiveSummarizationCompactionStrategy.builder(compactionClient)
                            .maxEventsToKeep(RETAINED_SESSION_EVENT_COUNT)
                            .tokenCountEstimator(tokenCountEstimator)
                            .build())
                .build();

        return builder.defaultSystem(promptResources.baseIdentitySystemResource())
                .defaultOptions(OllamaChatOptions.builder().enableThinking())
                .defaultAdvisors(sessionMemoryAdvisor)
                .defaultTools(retrieveInformationTool)
                .build();
    }
}
