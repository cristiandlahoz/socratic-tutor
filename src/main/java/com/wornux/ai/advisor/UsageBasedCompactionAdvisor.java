package com.wornux.ai.advisor;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public final class UsageBasedCompactionAdvisor implements BaseAdvisor {

    public static final String PROMPT_TOKENS_CONTEXT_KEY = "chat_prompt_tokens";

    private static final Logger log = LoggerFactory.getLogger(UsageBasedCompactionAdvisor.class);
    private static final String NAME = "usage-based-compaction-advisor";

    private final int order;
    private final Scheduler scheduler;
    private final SessionService sessionService;
    private final JdbcClient jdbcClient;
    private final int compactionThresholdTokens;
    private final CompactionStrategy compactionStrategy;

    public UsageBasedCompactionAdvisor(
            int order,
            Scheduler scheduler,
            SessionService sessionService,
            JdbcClient jdbcClient,
            int compactionThresholdTokens,
            CompactionStrategy compactionStrategy) {
        this.order = order;
        this.scheduler = scheduler;
        this.sessionService = sessionService;
        this.jdbcClient = jdbcClient;
        this.compactionThresholdTokens = compactionThresholdTokens;
        this.compactionStrategy = compactionStrategy;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        var sessionId = sessionId(response);
        var promptTokens = promptTokens(response.chatResponse());
        if (promptTokens == null || promptTokens <= 0) {
            return response;
        }

        updateConversationPromptTokens(sessionId, promptTokens);
        compactIfNeeded(sessionId, promptTokens);
        return response.mutate().context(PROMPT_TOKENS_CONTEXT_KEY, promptTokens).build();
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Mono.just(request)
                .publishOn(scheduler)
                .map(r -> before(r, chain))
                .flatMapMany(chain::nextStream)
                .publishOn(scheduler)
                .transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux, r -> after(r, chain)));
    }

    private void compactIfNeeded(String sessionId, int promptTokens) {
        if (promptTokens < compactionThresholdTokens) {
            return;
        }
        sessionService.compact(sessionId, request -> {
            log.info(
                "Chat session compaction triggered: sessionId={}, userId={}, events={}, turns={}, promptTokens={}, thresholdTokens={}",
                sessionId,
                request.session() == null ? "unknown" : request.session().userId(),
                request.currentEventCount(),
                request.currentTurnCount(),
                promptTokens,
                compactionThresholdTokens);
            return true;
        }, compactionStrategy);
    }

    private void updateConversationPromptTokens(String sessionId, int promptTokens) {
        try {
            jdbcClient.sql("update conversation set last_prompt_tokens = :promptTokens, updated_at = current_timestamp where id = :conversationId")
                    .param("promptTokens", promptTokens)
                    .param("conversationId", UUID.fromString(sessionId))
                    .update();
        }
        catch (IllegalArgumentException _) {
            log.debug("Skipping prompt-token persistence for non-UUID session id '{}'", sessionId);
        }
    }

    private static String sessionId(ChatClientResponse response) {
        Object value = response.context().get(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalStateException("No session ID found in advisor context");
    }

    private static @Nullable Integer promptTokens(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        return response.getMetadata().getUsage().getPromptTokens();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public Scheduler getScheduler() {
        return scheduler;
    }
}
