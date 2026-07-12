package com.wornux.ai.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.data.enums.GuardAction;
import com.wornux.dtos.chat.GuardCheck;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import reactor.core.publisher.Flux;

public class TutorGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TutorGuardAdvisor.class);

    public static final String STEERED_USER_MESSAGE_CALLBACK_CONTEXT_KEY = "tutor.guard.steeredUserMessageCallback";

    private static final int GUARD_HISTORY_WINDOW = 3;
    private static final String GUARD_CHECKED_CONTEXT_KEY = "tutor.guard.checked";
    private static final EventFilter GUARD_HISTORY_FILTER = EventFilter.builder()
            .messageTypes(Set.of(MessageType.USER, MessageType.ASSISTANT))
            .lastN(GUARD_HISTORY_WINDOW)
            .excludeArchived(true)
            .build();
    private static final EventFilter PREVIOUS_ASSISTANT_FILTER = EventFilter.builder()
            .messageTypes(Set.of(MessageType.ASSISTANT))
            .lastN(1)
            .excludeArchived(true)
            .build();

    private final int order;
    private final GuardClassifierService guardClassifierService;
    private final SessionService sessionService;

    public TutorGuardAdvisor(
            int order,
            GuardClassifierService guardClassifierService,
            SessionService sessionService) {
        this.order = order;
        this.guardClassifierService = guardClassifierService;
        this.sessionService = sessionService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        if (guardAlreadyChecked(request)) {
            return chain.nextCall(request);
        }
        var guardCheck = guardCheckFor(request);
        if (guardCheck.action() == GuardAction.SHORT_CIRCUIT) {
            return shortCircuitResponse(request, guardCheck.directResponse());
        }
        return chain.nextCall(applyGuardAction(request, guardCheck));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        if (guardAlreadyChecked(request)) {
            return chain.nextStream(request);
        }
        var guardCheck = guardCheckFor(request);
        if (guardCheck.action() == GuardAction.SHORT_CIRCUIT) {
            return Flux.just(shortCircuitResponse(request, guardCheck.directResponse()));
        }
        return chain.nextStream(applyGuardAction(request, guardCheck));
    }

    GuardCheck guardCheckFor(ChatClientRequest request) {
        try {
            return guardClassifierService.classify(guardConversation(request), subjectContext(request));
        }
        catch (RuntimeException ex) {
            log.warn("Guard classifier failed, short-circuiting the turn", ex);
            return GuardClassifierService.technicalFailure();
        }
    }

    private List<Message> guardConversation(ChatClientRequest request) {
        var messages = new ArrayList<Message>(GUARD_HISTORY_WINDOW + 1);
        sessionId(request).ifPresent(sessionId -> messages.addAll(activeHistory(request, sessionId)));
        messages.add(request.prompt().getUserMessage());
        return messages;
    }

    private List<Message> activeHistory(ChatClientRequest request, String sessionId) {
        var session = sessionService.findById(sessionId);
        if (session == null) {
            return List.of();
        }
        var expectedUserId = request.context().get(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY);
        if (expectedUserId instanceof String userId && !userId.isBlank() && !userId.equals(session.userId())) {
            throw new IllegalStateException("Guard history session ownership mismatch");
        }
        var history = messages(sessionId, GUARD_HISTORY_FILTER);
        if (history.stream().anyMatch(AssistantMessage.class::isInstance)) {
            return history;
        }
        var previousAssistant = messages(sessionId, PREVIOUS_ASSISTANT_FILTER);
        if (previousAssistant.isEmpty()) {
            return history;
        }
        var context = new ArrayList<Message>(previousAssistant.size() + history.size());
        context.addAll(previousAssistant);
        context.addAll(history);
        return context;
    }

    private List<Message> messages(String sessionId, EventFilter filter) {
        return sessionService.getEvents(sessionId, filter)
                .stream()
                .filter(SessionEvent::isRootEvent)
                .map(SessionEvent::getMessage)
                .filter(this::hasText)
                .toList();
    }

    ChatClientRequest applyGuardAction(ChatClientRequest request, GuardCheck guardCheck) {
        return switch (guardCheck.action()) {
            case ALLOW -> markGuardChecked(request);
            case STEER -> steerUserMessage(request, guardCheck.safeUserMessage());
            case SHORT_CIRCUIT -> throw new IllegalStateException("SHORT_CIRCUIT must be handled before chain.next");
        };
    }

    private ChatClientResponse shortCircuitResponse(ChatClientRequest request, String directResponse) {
        var response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(directResponse))))
                .build();
        return new ChatClientResponse(response, request.context());
    }

    private ChatClientRequest steerUserMessage(ChatClientRequest request, String safeUserMessage) {
        notifySteeredUserMessage(request, safeUserMessage);
        Prompt sanitizedPrompt = request.prompt()
                .augmentUserMessage(user -> user.mutate().text(safeUserMessage).build());
        return markGuardChecked(request.mutate().prompt(sanitizedPrompt).build());
    }

    @SuppressWarnings("unchecked")
    private void notifySteeredUserMessage(ChatClientRequest request, String sanitized) {
        Object callback = request.context().get(STEERED_USER_MESSAGE_CALLBACK_CONTEXT_KEY);
        if (!(callback instanceof Consumer<?> consumer)) {
            return;
        }
        try {
            ((Consumer<String>) consumer).accept(sanitized);
        }
        catch (RuntimeException ex) {
            log.debug("Guard steered-message callback failed", ex);
        }
    }

    private boolean guardAlreadyChecked(ChatClientRequest request) {
        return Boolean.TRUE.equals(request.context().get(GUARD_CHECKED_CONTEXT_KEY));
    }

    private ChatClientRequest markGuardChecked(ChatClientRequest request) {
        return request.mutate().context(GUARD_CHECKED_CONTEXT_KEY, true).build();
    }


    private String subjectContext(ChatClientRequest request) {
        return groupClassId(request).flatMap(guardClassifierService::subjectContextFor).orElse("");
    }

    private Optional<UUID> groupClassId(ChatClientRequest request) {
        Object value = request.context().get(ToolContextKeys.GROUP_CLASS_ID);
        if (value instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(UUID.fromString(text));
            }
            catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> sessionId(ChatClientRequest request) {
        Object value = request.context().get(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY);
        return value instanceof String sessionId && !sessionId.isBlank() ? Optional.of(sessionId) : Optional.empty();
    }

    private boolean hasText(Message message) {
        var text = message.getText();
        return text != null && !text.isBlank();
    }

    @Override
    public String getName() {
        return "tutor-guard-advisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}
