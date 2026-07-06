package com.wornux.ai.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.wornux.ai.prompt.PromptUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

public final class TokenBudgetRecursiveSummarizationCompactionStrategy implements CompactionStrategy {

    private static final Logger log =
            LoggerFactory.getLogger(TokenBudgetRecursiveSummarizationCompactionStrategy.class);

    private static final String STRATEGY_NAME = "token-budget-recursive-summarization";
    private static final int DEFAULT_OVERLAP_EVENTS = 2;
    private static final int MAX_FORMATTED_EVENT_CHARS = 2_000;

    private static final String DEFAULT_SHADOW_PROMPT = "Resume la conversación de tutoría hasta ahora.";
    private static final String DEFAULT_SYSTEM_PROMPT = "Summarize the tutoring conversation.";
    private static final String DEFAULT_USER_PROMPT_TEMPLATE = """
                                                               <prior-summary>
                                                               $priorSummary$
                                                               </prior-summary>

                                                               <conversation-to-summarize>
                                                               $conversationToSummarize$
                                                               </conversation-to-summarize>

                                                               <upcoming-context purpose="continuity-only">
                                                               $upcomingContext$
                                                               </upcoming-context>

                                                               Please write the summary now.
                                                               """;

    private final ChatClient chatClient;
    private final int recentHistoryTokenBudget;
    private final int overlapEvents;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final String shadowPrompt;
    private final TokenCountEstimator tokenCountEstimator;

    private TokenBudgetRecursiveSummarizationCompactionStrategy(
            ChatClient chatClient,
            int recentHistoryTokenBudget,
            int overlapEvents,
            String systemPrompt,
            String userPromptTemplate,
            String shadowPrompt,
            TokenCountEstimator tokenCountEstimator) {
        this.chatClient = chatClient;
        this.recentHistoryTokenBudget = recentHistoryTokenBudget;
        this.overlapEvents = overlapEvents;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
        this.shadowPrompt = shadowPrompt;
        this.tokenCountEstimator = tokenCountEstimator;
    }

    @Override
    public CompactionResult compact(CompactionRequest request) {
        var events = request.events();
        var syntheticEvents = events.stream().filter(SessionEvent::isSynthetic).toList();
        var realEvents = events.stream().filter(event -> !event.isSynthetic()).toList();
        var cutIndex = findTokenBudgetCutIndex(realEvents);
        if (cutIndex <= 0) {
            return new CompactionResult(events, List.of(), 0);
        }

        var toArchive = realEvents.subList(0, cutIndex);
        var activeWindow = realEvents.subList(cutIndex, realEvents.size());
        var overlap = activeWindow.subList(0, Math.min(overlapEvents, activeWindow.size()));
        var summary = chatClient.prompt()
                .system(systemPrompt)
                .user(buildSummarizationPrompt(syntheticEvents, toArchive, overlap))
                .call()
                .content();

        if (summary == null || summary.isBlank()) {
            log.warn(
                "Compaction skipped because summarizer returned an empty summary for session '{}'",
                request.session().id());
            return new CompactionResult(events, List.of(), 0);
        }

        var compacted = new ArrayList<SessionEvent>();
        compacted.addAll(summaryTurn(request.session().id(), summary));
        compacted.addAll(activeWindow);

        return new CompactionResult(compacted, new ArrayList<>(toArchive), archivedTokens(toArchive));
    }

    private int findTokenBudgetCutIndex(List<SessionEvent> realEvents) {
        if (realEvents.isEmpty()) {
            return 0;
        }
        var rawCutIndex = realEvents.size();
        var tokens = 0;
        for (var index = realEvents.size() - 1; index >= 0; index--) {
            tokens += tokenCountEstimator.estimate(formatEvent(realEvents.get(index)));
            rawCutIndex = index;
            if (tokens >= recentHistoryTokenBudget) {
                break;
            }
        }
        return snapBackwardToTurnStart(realEvents, rawCutIndex);
    }

    private int snapBackwardToTurnStart(List<SessionEvent> realEvents, int rawCutIndex) {
        var index = rawCutIndex;
        while (index > 0
                && !(realEvents.get(index).isRootEvent()
                        && realEvents.get(index).getMessageType() == MessageType.USER)) {
            index--;
        }
        if (realEvents.get(index).isRootEvent() && realEvents.get(index).getMessageType() == MessageType.USER) {
            return index;
        }
        return 0;
    }

    private String buildSummarizationPrompt(
            List<SessionEvent> priorSummaries,
            List<SessionEvent> eventsToSummarize,
            List<SessionEvent> overlap) {
        return PromptUtil.render(
            userPromptTemplate,
            Map.of(
                "priorSummary",
                priorSummary(priorSummaries),
                "conversationToSummarize",
                formatEvents(eventsToSummarize),
                "upcomingContext",
                formatEvents(overlap)));
    }

    private String priorSummary(List<SessionEvent> priorSummaries) {
        return priorSummaries.stream()
                .filter(event -> event.getMessageType() != MessageType.USER)
                .map(event -> event.getMessage().getText())
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String formatEvents(List<SessionEvent> events) {
        return events.stream()
                .map(TokenBudgetRecursiveSummarizationCompactionStrategy::formatEvent)
                .collect(Collectors.joining("\n"));
    }

    private List<SessionEvent> summaryTurn(String sessionId, String summary) {
        var timestamp = Instant.now();
        return List.of(
            SessionEvent.builder()
                    .sessionId(sessionId)
                    .timestamp(timestamp)
                    .message(new UserMessage(shadowPrompt))
                    .metadata(SessionEvent.METADATA_SYNTHETIC, true)
                    .metadata(SessionEvent.METADATA_COMPACTION_SOURCE, STRATEGY_NAME)
                    .build(),
            SessionEvent.builder()
                    .sessionId(sessionId)
                    .timestamp(timestamp)
                    .message(new AssistantMessage(summary))
                    .metadata(SessionEvent.METADATA_SYNTHETIC, true)
                    .metadata(SessionEvent.METADATA_COMPACTION_SOURCE, STRATEGY_NAME)
                    .build());
    }

    private int archivedTokens(List<SessionEvent> archived) {
        return archived.stream().mapToInt(event -> tokenCountEstimator.estimate(formatEvent(event))).sum();
    }

    private static String formatEvent(SessionEvent event) {
        var role = switch (event.getMessageType()) {
            case USER -> "User";
            case ASSISTANT -> "Assistant";
            case SYSTEM -> "System";
            case TOOL -> "Tool";
        };

        if (event.getMessage() instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            var calls = assistantMessage.getToolCalls()
                    .stream()
                    .map(toolCall -> toolCall.name() + "(" + truncate(toolCall.arguments()) + ")")
                    .collect(Collectors.joining(", "));
            var text = assistantMessage.getText();
            return truncate(
                text != null && !text.isBlank()
                        ? role + ": " + text + " [tool calls: " + calls + "]"
                        : role + " [tool calls: " + calls + "]");
        }

        if (event.getMessage() instanceof ToolResponseMessage toolResponseMessage) {
            var responses = toolResponseMessage.getResponses()
                    .stream()
                    .map(response -> response.name() + " -> " + truncate(response.responseData()))
                    .collect(Collectors.joining(", "));
            return truncate(role + " [responses: " + responses + "]");
        }

        var text = event.getMessage().getText();
        return truncate(role + ": " + (text == null ? "[no text content]" : text));
    }

    private static String truncate(@Nullable String value) {
        if (value == null || value.length() <= MAX_FORMATTED_EVENT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_FORMATTED_EVENT_CHARS) + "… [truncated "
                + (value.length() - MAX_FORMATTED_EVENT_CHARS) + " chars]";
    }

    public static Builder builder(ChatClient chatClient) {
        return new Builder(chatClient);
    }

    public static final class Builder {

        private final ChatClient chatClient;
        private int recentHistoryTokenBudget;
        private int overlapEvents = DEFAULT_OVERLAP_EVENTS;
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        private String userPromptTemplate = DEFAULT_USER_PROMPT_TEMPLATE;
        private String shadowPrompt = DEFAULT_SHADOW_PROMPT;
        private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

        private Builder(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        public Builder recentHistoryTokenBudget(int recentHistoryTokenBudget) {
            this.recentHistoryTokenBudget = recentHistoryTokenBudget;
            return this;
        }

        public Builder overlapEvents(int overlapEvents) {
            this.overlapEvents = overlapEvents;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPromptTemplate(String userPromptTemplate) {
            this.userPromptTemplate = userPromptTemplate;
            return this;
        }

        public Builder shadowPrompt(String shadowPrompt) {
            this.shadowPrompt = shadowPrompt;
            return this;
        }

        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        public TokenBudgetRecursiveSummarizationCompactionStrategy build() {
            return new TokenBudgetRecursiveSummarizationCompactionStrategy(chatClient,
                    recentHistoryTokenBudget,
                    overlapEvents,
                    systemPrompt,
                    userPromptTemplate,
                    shadowPrompt,
                    tokenCountEstimator);
        }
    }
}
