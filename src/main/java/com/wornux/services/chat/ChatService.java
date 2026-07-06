package com.wornux.services.chat;

import java.io.InterruptedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.openai.errors.OpenAIIoException;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.dtos.chat.*;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ChatUsageService chatUsageService;
    private final ToolUsageAuditService toolUsageAuditService;
    private final ActiveAcademicContextResolver contextResolver;
    private final Map<UUID, Integer> turnPromptTokens = new ConcurrentHashMap<>();

    public ChatService(
            ChatClient chatClient,
            ConversationService conversationService,
            ChatUsageService chatUsageService,
            ToolUsageAuditService toolUsageAuditService,
            ActiveAcademicContextResolver contextResolver) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.chatUsageService = chatUsageService;
        this.toolUsageAuditService = toolUsageAuditService;
        this.contextResolver = contextResolver;
    }

    public Flux<String> chatStream(
            UUID turnId,
            String userInput,
            UUID conversationId,
            AskStudentQuestionTool.QuestionHandler questionHandler) {
        var promptTokens = new AtomicReference<Integer>();
        var academicCtx = contextResolver.requireCurrent();
        conversationService.requireOwnedConversation(conversationId);
        var sessionContext = buildSessionContext(academicCtx, conversationId);
        var clientRequestSpec = chatClient.prompt()
                .advisors(
                    advisorSpec -> advisorSpec
                            .param(
                                SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY,
                                sessionContext.get(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY))
                            .param(
                                SessionMemoryAdvisor.USER_ID_CONTEXT_KEY,
                                sessionContext.get(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY))
                            .param(ToolContextKeys.GROUP_CLASS_ID, academicCtx.groupClassId().toString()))
                .toolContext(buildToolContext(Optional.of(academicCtx), conversationId, turnId))
                .user(userInput);
        if (questionHandler != null) {
            clientRequestSpec = clientRequestSpec.tools(new AskStudentQuestionTool(questionHandler));
        }

        return clientRequestSpec.stream()
                .chatResponse()
                .doOnNext(response -> capturePromptTokens(response, promptTokens))
                .map(this::extractContentChunk)
                .filter(token -> !token.isEmpty())
                .doOnComplete(() -> storePromptTokens(turnId, promptTokens.get()))
                .doOnCancel(() -> clearTurnState(turnId))
                .doOnError(_ -> clearTurnState(turnId))
                .onErrorMap(this::wrapStreamException);
    }

    public void finalizeTurn(UUID turnId, UUID conversationId) {
        try {
            chatUsageService.updateConversationInputTokens(conversationId, turnPromptTokens.remove(turnId));
        }
        finally {
            clearTurnState(turnId);
        }
    }

    private void storePromptTokens(UUID turnId, Integer promptTokens) {
        if (promptTokens != null) {
            turnPromptTokens.put(turnId, promptTokens);
        }
    }

    private void clearTurnState(UUID turnId) {
        turnPromptTokens.remove(turnId);
        toolUsageAuditService.drainTurnAudits(turnId);
    }

    private void capturePromptTokens(ChatResponse response, AtomicReference<Integer> promptTokens) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }

        Integer value = response.getMetadata().getUsage().getPromptTokens();
        if (value != null) {
            promptTokens.set(value);
        }
    }

    private String extractContentChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }

        var text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private Throwable wrapStreamException(Throwable exception) {
        if (isOpenAiStreamTimeout(exception)) {
            return new ChatStreamTimeoutException("OpenAI-compatible chat stream timed out", exception);
        }
        return exception;
    }

    private boolean isOpenAiStreamTimeout(Throwable exception) {
        var hasOpenAiIoException = false;
        var hasTimeout = false;
        for (Throwable cursor = exception; cursor != null; cursor = cursor.getCause()) {
            hasOpenAiIoException = hasOpenAiIoException || cursor instanceof OpenAIIoException;
            hasTimeout = hasTimeout
                    || cursor instanceof InterruptedIOException
                    || cursor.getMessage() != null
                            && cursor.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timeout");
        }
        return hasOpenAiIoException && hasTimeout;
    }

    static Map<String, Object> buildToolContext(
            Optional<ActiveAcademicContext> academicCtx,
            UUID conversationId,
            UUID turnId) {
        return Map.of(
            ToolContextKeys.GROUP_CLASS_MEMBER_ID,
            academicCtx.map(context -> context.groupClassMemberId().toString()).orElse(""),
            ToolContextKeys.GROUP_CLASS_ID,
            academicCtx.map(context -> context.groupClassId().toString()).orElse(""),
            ToolContextKeys.CONVERSATION_ID,
            conversationId,
            ToolContextKeys.TURN_ID,
            turnId);
    }

    static Map<String, Object> buildSessionContext(ActiveAcademicContext academicCtx, UUID conversationId) {
        return Map.of(
            SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY,
            conversationId.toString(),
            SessionMemoryAdvisor.USER_ID_CONTEXT_KEY,
            academicCtx.groupClassMemberId().toString());
    }

}
