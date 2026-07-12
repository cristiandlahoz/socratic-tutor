package com.wornux.services.chat;

import java.io.InterruptedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.openai.errors.OpenAIIoException;
import com.wornux.ai.advisor.TutorGuardAdvisor;
import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.tools.InterrogateUserTool;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.dtos.chat.*;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ToolUsageAuditService toolUsageAuditService;
    private final ActiveAcademicContextResolver contextResolver;
    private final GuardClassifierService guardClassifierService;

    public ChatService(
            ChatClient chatClient,
            ConversationService conversationService,
            ToolUsageAuditService toolUsageAuditService,
            ActiveAcademicContextResolver contextResolver,
            GuardClassifierService guardClassifierService) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.toolUsageAuditService = toolUsageAuditService;
        this.contextResolver = contextResolver;
        this.guardClassifierService = guardClassifierService;
    }

    public Flux<String> chatStream(
            UUID turnId,
            String userInput,
            UUID conversationId,
            InterrogateUserTool.QuestionHandler questionHandler,
            Consumer<String> steeredUserMessageHandler) {
        var academicCtx = contextResolver.requireCurrent();
        conversationService.requireOwnedConversation(conversationId);
        var sessionContext = buildSessionContext(academicCtx, conversationId);
        var approvedUserMessage = new AtomicReference<>(userInput);
        var clientRequestSpec = chatClient.prompt()
                .advisors(
                    advisorSpec -> {
                        advisorSpec.param(
                                SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY,
                                sessionContext.get(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY))
                                .param(
                                SessionMemoryAdvisor.USER_ID_CONTEXT_KEY,
                                sessionContext.get(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY))
                                .param(ToolContextKeys.GROUP_CLASS_ID, academicCtx.groupClassId().toString());
                        if (steeredUserMessageHandler != null || questionHandler != null) {
                            advisorSpec.param(
                                TutorGuardAdvisor.STEERED_USER_MESSAGE_CALLBACK_CONTEXT_KEY,
                                (Consumer<String>) steered -> {
                                    approvedUserMessage.set(steered);
                                    if (steeredUserMessageHandler != null) {
                                        steeredUserMessageHandler.accept(steered);
                                    }
                                });
                        }
                    })
                .toolContext(buildToolContext(Optional.of(academicCtx), conversationId, turnId))
                .user(userInput);
        if (questionHandler != null) {
            clientRequestSpec = clientRequestSpec.tools(new InterrogateUserTool(
                questionHandler,
                (questionSet, response) -> guardInteractiveResponse(
                    approvedUserMessage.get(), questionSet, response, academicCtx.groupClassId())));
        }

        return clientRequestSpec.stream()
                .chatResponse()
                .map(this::extractContentChunk)
                .filter(token -> !token.isEmpty())
                .doOnCancel(() -> clearTurnState(turnId))
                .doOnError(_ -> clearTurnState(turnId))
                .onErrorMap(this::wrapStreamException);
    }

    private GuardCheck guardInteractiveResponse(
            String approvedUserMessage,
            StudentQuestionSet questionSet,
            StudentQuestionResponse response,
            UUID groupClassId) {
        try {
            var subjectContext = guardClassifierService.subjectContextFor(groupClassId).orElse("");
            return guardClassifierService.classifyInteractiveResponse(
                approvedUserMessage, questionSet, response, subjectContext);
        }
        catch (RuntimeException exception) {
            log.warn("Interactive response guard failed, rejecting the response", exception);
            return GuardClassifierService.technicalFailure();
        }
    }

    public void finalizeTurn(UUID turnId, UUID conversationId) {
        clearTurnState(turnId);
    }

    private void clearTurnState(UUID turnId) {
        toolUsageAuditService.drainTurnAudits(turnId);
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
