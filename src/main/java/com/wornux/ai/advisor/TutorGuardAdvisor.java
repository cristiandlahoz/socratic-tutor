package com.wornux.ai.advisor;

import java.util.List;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.enums.GuardDecision;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class TutorGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TutorGuardAdvisor.class);

    private static final int GUARD_MESSAGE_WINDOW = 4;

    private final int order;
    private final GuardClassifierService guardClassifierService;
    private final PromptResources promptResources;

    public TutorGuardAdvisor(
            int order,
            GuardClassifierService guardClassifierService,
            PromptResources promptResources) {
        this.order = order;
        this.guardClassifierService = guardClassifierService;
        this.promptResources = promptResources;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        var userMessages = lastUserMessages(request.prompt());
        return chain.nextCall(applyGuardDecision(request, guardDecisionFor(userMessages)));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        var userMessages = lastUserMessages(request.prompt());
        return chain.nextStream(applyGuardDecision(request, guardDecisionFor(userMessages)));
    }

    GuardDecision guardDecisionFor(List<UserMessage> userMessages) {
        try {
            return guardClassifierService.classify(userMessages);
        }
        catch (RuntimeException ex) {
            log.warn("Guard classifier failed, defaulting to the not-safe guard policy", ex);
            return GuardDecision.NOT_SAFE;
        }
    }

    private List<UserMessage> lastUserMessages(Prompt prompt) {
        List<UserMessage> userMessages = prompt.getInstructions()
                .stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .filter(message -> message.getText() != null && !message.getText().isBlank())
                .toList();

        if (userMessages.isEmpty()) {
            return List.of(prompt.getUserMessage());
        }

        int fromIndex = Math.max(0, userMessages.size() - GUARD_MESSAGE_WINDOW);
        return userMessages.subList(fromIndex, userMessages.size());
    }

    ChatClientRequest applyGuardDecision(ChatClientRequest request, GuardDecision decision) {
        return switch (decision) {
            case SAFE -> request;
            case NOT_SAFE -> applyGuardPolicy(request, promptResources.guardNotSafe(), "not_safe_guard");
            case IMPERSONATION ->
                    applyGuardPolicy(request, promptResources.guardImpersonation(), "impersonation_handling_mode");
            case OUT_OF_SCOPE ->
                    applyGuardPolicy(request, promptResources.guardOutOfScope(), "out_of_scope_handling_mode");
        };
    }

    private ChatClientRequest applyGuardPolicy(ChatClientRequest request, String policyText, String policyMode) {
        Prompt guardedPrompt = request.prompt().augmentSystemMessage(system -> {
            String existing = system.getText();

            String updatedText = existing == null || existing.isBlank()
                    ? """
                      <guard-policy mode="%s">
                      %s
                      </guard-policy>
                      """.formatted(policyMode, policyText)
                    : """
                      %s

                      <guard-policy mode="%s">
                      %s
                      </guard-policy>
                      """.formatted(existing, policyMode, policyText);

            return system.mutate().text(updatedText).build();
        });

        return request.mutate().prompt(guardedPrompt).build();
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
