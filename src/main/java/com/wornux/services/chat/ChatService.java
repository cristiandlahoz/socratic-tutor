package com.wornux.services.chat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.ai.memory.PostgresChatMemory;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.dtos.chat.*;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ChatUsageService chatUsageService;
    private final ChatCompactionService chatCompactionService;
    private final ToolUsageAuditService toolUsageAuditService;
    private final ActiveAcademicContextResolver contextResolver;
    private final PostgresChatMemory chatMemory;
    private final Map<UUID, Integer> turnPromptTokens = new ConcurrentHashMap<>();

    public ChatService(
            ChatClient chatClient,
            ConversationService conversationService,
            ChatUsageService chatUsageService,
            ChatCompactionService chatCompactionService,
            ToolUsageAuditService toolUsageAuditService,
            ActiveAcademicContextResolver contextResolver,
            PostgresChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatUsageService = chatUsageService;
        this.chatCompactionService = chatCompactionService;
        this.toolUsageAuditService = toolUsageAuditService;
        this.contextResolver = contextResolver;
        this.chatMemory = chatMemory;
    }

    public Flux<String> chatStream(
            UUID turnId,
            String userInput,
            UUID conversationId,
            AskStudentQuestionTool.QuestionHandler questionHandler) {
        var promptTokens = new AtomicReference<Integer>();
        var academicCtx = contextResolver.resolveCurrent();
        var clientRequestSpec = chatClient.prompt()

                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId.toString()))
                .toolContext(buildToolContext(academicCtx, conversationId, turnId))
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
                .doOnError(_ -> clearTurnState(turnId));
    }

    public TurnFinalizationResult finalizeTurn(
            UUID turnId,
            UUID conversationId,
            String userInput,
            String assistantResponse) {
        try {
            chatMemory.add(
                conversationId.toString(),
                java.util.List.of(
                    new org.springframework.ai.chat.messages.UserMessage(userInput),
                    new org.springframework.ai.chat.messages.AssistantMessage(assistantResponse)));
            chatUsageService.updateConversationInputTokens(conversationId, turnPromptTokens.remove(turnId));
            return new TurnFinalizationResult(compactConversationIfNeeded(conversationId));
        }
        finally {
            clearTurnState(turnId);
        }
    }

    private ChatCompactionStatus compactConversationIfNeeded(UUID conversationId) {
        try {
            return chatCompactionService.compactIfNeeded(conversationId);
        }
        catch (RuntimeException exception) {
            logger.warn("Failed to compact conversation {}", conversationId, exception);
            return ChatCompactionStatus.none();
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

}
