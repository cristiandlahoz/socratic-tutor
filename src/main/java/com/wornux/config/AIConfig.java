package com.wornux.config;

import com.wornux.ai.advisor.TutorGuardAdvisor;
import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.ai.session.compaction.RecursiveSummarizationCompactionStrategy;
import org.springframework.ai.session.compaction.TokenCountTrigger;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);

    private static final int RETAINED_SESSION_EVENT_COUNT = 4;

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            ChatModel chatModel,
            SessionService sessionService,
            ChatProperties chatProperties,
            GuardClassifierService guardClassifierService,
            RetrieveInformationTool retrieveInformationTool,
            TutorPromptResources promptResources) {

        var compactionClient = ChatClient.builder(chatModel).build();
        var tokenCountEstimator = new JTokkitTokenCountEstimator();
        int compactionThresholdTokens = chatProperties.compactionThresholdTokens();
        var tokenCountTrigger = TokenCountTrigger.builder()
                .threshold(compactionThresholdTokens)
                .tokenCountEstimator(tokenCountEstimator)
                .build();
        var compactionStrategy = RecursiveSummarizationCompactionStrategy.builder(compactionClient)
                .maxEventsToKeep(RETAINED_SESSION_EVENT_COUNT)
                .tokenCountEstimator(tokenCountEstimator)
                .build();
        var sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionService)
                .eventFilter(EventFilter.active())
                .compactionTrigger(loggingCompactionTrigger(tokenCountTrigger, compactionThresholdTokens))
                .compactionStrategy(loggingCompactionStrategy(compactionStrategy))
                .build();
        var tutorGuardAdvisor = new TutorGuardAdvisor(
            sessionMemoryAdvisor.getOrder() + 1,
            guardClassifierService,
            promptResources);

        return builder.defaultSystem(promptResources.baseIdentitySystemResource())
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.6)
                        .topP(0.95)
                        .topK(20))
                .defaultAdvisors(sessionMemoryAdvisor, tutorGuardAdvisor)
                .defaultTools(retrieveInformationTool)
                .build();
    }

    private CompactionTrigger loggingCompactionTrigger(
            CompactionTrigger delegate,
            int compactionThresholdTokens) {
        return request -> {
            boolean shouldCompact = delegate.shouldCompact(request);
            if (shouldCompact) {
                log.info(
                    "Chat session compaction triggered: sessionId={}, userId={}, events={}, turns={}, thresholdTokens={}",
                    sessionId(request),
                    userId(request),
                    request.currentEventCount(),
                    request.currentTurnCount(),
                    compactionThresholdTokens);
            }
            return shouldCompact;
        };
    }

    private CompactionStrategy loggingCompactionStrategy(CompactionStrategy delegate) {
        return request -> {
            log.info(
                "Chat session compaction started: sessionId={}, userId={}, events={}, turns={}",
                sessionId(request),
                userId(request),
                request.currentEventCount(),
                request.currentTurnCount());

            var result = delegate.compact(request);

            log.info(
                "Chat session compaction completed: sessionId={}, userId={}, compactedEvents={}, archivedEvents={}, eventsRemoved={}, estimatedTokensSaved={}",
                sessionId(request),
                userId(request),
                result.compactedEvents().size(),
                result.archivedEvents().size(),
                result.eventsRemoved(),
                result.tokensEstimatedSaved());

            return result;
        };
    }

    private static String sessionId(CompactionRequest request) {
        return request.session() == null ? "unknown" : request.session().id();
    }

    private static String userId(CompactionRequest request) {
        return request.session() == null ? "unknown" : request.session().userId();
    }
}
