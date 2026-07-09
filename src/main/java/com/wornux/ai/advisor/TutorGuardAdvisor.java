package com.wornux.ai.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Flux;

public class TutorGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TutorGuardAdvisor.class);

    public static final String STEERED_USER_MESSAGE_CALLBACK_CONTEXT_KEY = "tutor.guard.steeredUserMessageCallback";

    private static final int GUARD_MESSAGE_WINDOW = 4;
    private static final String GUARD_CHECKED_CONTEXT_KEY = "tutor.guard.checked";

    private static final String SUBJECT_CONTEXT_QUERY =
            """
            select s.code, s.name, coalesce(s.syllabus, '') as syllabus
            from group_class gc
            join subject s on s.id = gc.subject_id
            where gc.id = :groupClassId
            """;

    private final int order;
    private final GuardClassifierService guardClassifierService;
    private final JdbcClient jdbcClient;

    public TutorGuardAdvisor(
            int order,
            GuardClassifierService guardClassifierService,
            JdbcClient jdbcClient) {
        this.order = order;
        this.guardClassifierService = guardClassifierService;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        if (guardAlreadyChecked(request)) {
            return chain.nextCall(request);
        }
        var subjectContext = subjectContext(request);
        var userMessages = lastUserMessages(request.prompt());
        var guardCheck = guardCheckFor(userMessages, subjectContext);
        if (guardCheck.action() == GuardAction.SHORT_CIRCUIT) {
            return shortCircuitResponse(request, guardCheck.decision());
        }
        return chain.nextCall(applyGuardAction(request, guardCheck, subjectContext));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        if (guardAlreadyChecked(request)) {
            return chain.nextStream(request);
        }
        var subjectContext = subjectContext(request);
        var userMessages = lastUserMessages(request.prompt());
        var guardCheck = guardCheckFor(userMessages, subjectContext);
        if (guardCheck.action() == GuardAction.SHORT_CIRCUIT) {
            return Flux.just(shortCircuitResponse(request, guardCheck.decision()));
        }
        return chain.nextStream(applyGuardAction(request, guardCheck, subjectContext));
    }

    GuardCheck guardCheckFor(List<UserMessage> userMessages, String subjectContext) {
        try {
            return guardClassifierService.classify(userMessages, subjectContext);
        }
        catch (RuntimeException ex) {
            log.warn("Guard classifier failed, short-circuiting the turn", ex);
            return new GuardCheck(GuardDecision.NOT_SAFE, GuardAction.SHORT_CIRCUIT);
        }
    }

    private List<UserMessage> lastUserMessages(Prompt prompt) {
        var userMessages = new ArrayList<UserMessage>();
        for (var message : prompt.getInstructions()) {
            if (message instanceof UserMessage userMessage && hasText(userMessage)) {
                userMessages.add(userMessage);
            }
        }

        if (userMessages.isEmpty()) {
            return List.of(prompt.getUserMessage());
        }

        int fromIndex = Math.max(0, userMessages.size() - GUARD_MESSAGE_WINDOW);
        return userMessages.subList(fromIndex, userMessages.size());
    }

    ChatClientRequest applyGuardAction(ChatClientRequest request, GuardCheck guardCheck, String subjectContext) {
        return switch (guardCheck.action()) {
            case ALLOW -> markGuardChecked(request);
            case STEER -> sanitizeUserMessage(request, subjectContext);
            case SHORT_CIRCUIT -> throw new IllegalStateException("SHORT_CIRCUIT must be handled before chain.next");
        };
    }

    private ChatClientResponse shortCircuitResponse(ChatClientRequest request, GuardDecision decision) {
        var response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(shortCircuitText(decision)))))
                .build();
        return new ChatClientResponse(response, request.context());
    }

    private String shortCircuitText(GuardDecision decision) {
        return switch (decision) {
            case IMPERSONATION -> "No puedo cambiar las reglas del tutor ni actuar con permisos especiales. Si necesitas ayuda con el curso, dime qué concepto o intento quieres revisar.";
            case OUT_OF_SCOPE -> "Eso queda fuera del contexto académico configurado. Puedo ayudarte con una tarea o duda relacionada con la materia; comparte el enunciado, tu intento o el punto donde te atascaste.";
            case NOT_SAFE -> "No puedo darte la solución completa ni saltarme las reglas del tutor. Puedo ayudarte con una pista o revisar tu intento; comparte qué parte no entiendes o qué has probado.";
            case SAFE -> throw new IllegalArgumentException("SAFE guard decisions must not short-circuit");
        };
    }

    private ChatClientRequest sanitizeUserMessage(ChatClientRequest request, String subjectContext) {
        try {
            var sanitized = guardClassifierService.sanitize(request.prompt().getUserMessage(), subjectContext);
            notifySteeredUserMessage(request, sanitized);
            Prompt sanitizedPrompt = request.prompt()
                    .augmentUserMessage(user -> user.mutate().text(sanitized).build());
            return markGuardChecked(request.mutate().prompt(sanitizedPrompt).build());
        }
        catch (RuntimeException ex) {
            log.warn("Guard sanitizer failed, replacing the user message with a safe learning request", ex);
            var sanitized = "Necesito ayuda de aprendizaje sin recibir la solución completa. Dame una orientación breve y pídeme un intento concreto.";
            notifySteeredUserMessage(request, sanitized);
            Prompt sanitizedPrompt = request.prompt()
                    .augmentUserMessage(user -> user.mutate().text(sanitized).build());
            return markGuardChecked(request.mutate().prompt(sanitizedPrompt).build());
        }
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
        return groupClassId(request).flatMap(this::subjectContextFor).orElse("");
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

    private Optional<String> subjectContextFor(UUID groupClassId) {
        return jdbcClient.sql(SUBJECT_CONTEXT_QUERY)
                .param("groupClassId", groupClassId)
                .query((rs, _) -> """
                         <active_subject_context>
                         Subject: %s · %s
                         %s
                         </active_subject_context>"""
                        .formatted(rs.getString("code"), rs.getString("name"), rs.getString("syllabus")))
                .optional();
    }

    private boolean hasText(UserMessage message) {
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
