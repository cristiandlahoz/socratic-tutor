package com.wornux.config;

import com.wornux.ai.advisor.DynamicContextManagementAdvisor;
import com.wornux.ai.advisor.TutorGuardAdvisor;
import com.wornux.ai.advisor.UsageBasedCompactionAdvisor;
import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.session.TokenBudgetRecursiveSummarizationCompactionStrategy;
import com.wornux.ai.tools.InterrogateUserTool;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.services.chat.ChatSessionActivity;
import com.wornux.services.chat.ChatSessionActivityBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

@Configuration
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            ChatModel chatModel,
            SessionService sessionService,
            ApplicationProperties.Ai.Conversation chatProperties,
            GuardClassifierService guardClassifierService,
            RetrieveInformationTool retrieveInformationTool,
            PromptResources promptResources,
            ChatSessionActivityBus activityBus,
            JdbcClient jdbcClient) {

        var compactionClient = ChatClient.builder(chatModel).build();
        var tokenCountEstimator = new JTokkitTokenCountEstimator();
        int compactionThresholdTokens = chatProperties.compactionThresholdTokens();
        var compactionStrategy = TokenBudgetRecursiveSummarizationCompactionStrategy.builder(compactionClient)
                .recentHistoryTokenBudget(chatProperties.recentHistoryRetentionTokens())
                .systemPrompt(promptResources.compactionSystem())
                .userPromptTemplate(promptResources.compactionUser())
                .tokenCountEstimator(tokenCountEstimator)
                .build();
        var loggingCompactionStrategy = loggingCompactionStrategy(compactionStrategy, activityBus);
        var sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionService)
                .eventFilter(EventFilter.active())
                .build();
        var usageBasedCompactionAdvisor = new UsageBasedCompactionAdvisor(
                sessionMemoryAdvisor.getOrder() - 1,
                sessionMemoryAdvisor.getScheduler(),
                sessionService,
                jdbcClient,
                compactionThresholdTokens,
                loggingCompactionStrategy);
        var dynamicContextManagementAdvisor =
                new DynamicContextManagementAdvisor(sessionMemoryAdvisor.getOrder() + 1, jdbcClient);
        var tutorGuardAdvisor =
                new TutorGuardAdvisor(sessionMemoryAdvisor.getOrder() - 2, guardClassifierService, sessionService);

        return builder.defaultSystem(promptResources.baseIdentitySystemResource())
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.6).topP(0.95).topK(20))
                .defaultAdvisors(
                    tutorGuardAdvisor,
                    usageBasedCompactionAdvisor,
                    sessionMemoryAdvisor,
                    dynamicContextManagementAdvisor)
                .defaultTools(retrieveInformationTool)
                .build();
    }

    @Bean
    ToolExecutionExceptionProcessor tutorToolExceptionProcessor() {
        return InterrogateUserTool.toolExceptionProcessor();
    }

    private CompactionStrategy loggingCompactionStrategy(
            CompactionStrategy delegate,
            ChatSessionActivityBus activityBus) {
        return request -> {
            var requestSessionId = sessionId(request);
            announceCompactionStarted(request, activityBus, requestSessionId);
            try {
                var result = delegate.compact(request);
                logCompactionCompleted(request, requestSessionId, result);
                return result;
            }
            finally {
                activityBus.publish(requestSessionId, ChatSessionActivity.GENERATING);
            }
        };
    }

    private void announceCompactionStarted(
            CompactionRequest request,
            ChatSessionActivityBus activityBus,
            String requestSessionId) {
        log.info(
            "Chat session compaction started: sessionId={}, userId={}, events={}, turns={}",
            requestSessionId,
            userId(request),
            request.currentEventCount(),
            request.currentTurnCount());
        activityBus.publish(requestSessionId, ChatSessionActivity.COMPACTING);
    }

    private void logCompactionCompleted(CompactionRequest request, String requestSessionId, CompactionResult result) {
        log.info(
            "Chat session compaction completed: sessionId={}, userId={}, compactedEvents={}, archivedEvents={}, eventsRemoved={}, estimatedTokensSaved={}",
            requestSessionId,
            userId(request),
            result.compactedEvents().size(),
            result.archivedEvents().size(),
            result.eventsRemoved(),
            result.tokensEstimatedSaved());
    }

    private static String sessionId(CompactionRequest request) {
        return request.session() == null ? "unknown" : request.session().id();
    }

    private static String userId(CompactionRequest request) {
        return request.session() == null ? "unknown" : request.session().userId();
    }
}
