package com.wornux.ai.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.PromptMessageUtils;
import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.data.enums.GuardDecision;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class TutorGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TutorGuardAdvisor.class);

    private final int order;
    private final GuardClassifierService guardClassifierService;
    private final TutorPromptResources promptResources;

    public TutorGuardAdvisor(
            int order,
            GuardClassifierService guardClassifierService,
            TutorPromptResources promptResources) {
        this.order = order;
        this.guardClassifierService = guardClassifierService;
        this.promptResources = promptResources;
    }

    @Override
    public org.springframework.ai.chat.client.ChatClientResponse adviseCall(
            ChatClientRequest request,
            @NonNull CallAdvisorChain chain) {
        String userQuery = PromptMessageUtils.extractLastUserText(request.prompt());

        return chain.nextCall(applyGuardDecision(request, guardDecisionFor(userQuery)));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        String userQuery = PromptMessageUtils.extractLastUserText(request.prompt());

        return chain.nextStream(applyGuardDecision(request, guardDecisionFor(userQuery)));
    }

    GuardDecision guardDecisionFor(String userQuery) {
        return classifyGuardDecision(userQuery);
    }

    GuardDecision classifyGuardDecision(String userQuery) {
        try {
            return guardClassifierService.classify(userQuery);
        }
        catch (RuntimeException ex) {
            log.warn("Guard classifier failed, defaulting to the not-safe guard policy", ex);
            return GuardDecision.NOT_SAFE;
        }
    }

    ChatClientRequest applyGuardDecision(ChatClientRequest request, GuardDecision decision) {
        return switch (decision) {
            case SAFE -> request;
            case NOT_SAFE -> appendSystemMessage(request, promptResources.guardNotSafe(), "not_safe_guard");
            case IMPERSONATION ->
                    appendSystemMessage(request, promptResources.guardImpersonation(), "impersonation_handling_mode");
            case OUT_OF_SCOPE ->
                    appendSystemMessage(request, promptResources.guardOutOfScope(), "out_of_scope_handling_mode");
        };
    }

    private ChatClientRequest appendSystemMessage(ChatClientRequest request, String systemMessage, String policyMode) {
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(new SystemMessage(systemMessage));

        var promptBuilder = Prompt.builder().messages(messages);
        var options = request.prompt().getOptions();
        if (!Objects.isNull(options)) {
            promptBuilder.chatOptions(options);
        }

        return request.mutate().prompt(promptBuilder.build()).context("policy_mode", policyMode).build();
    }

    @Override
    public @NullUnmarked String getName() {
        return "tutor-guard-advisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    enum RuleDecision {
        NOT_SAFE, IMPERSONATION, OUT_OF_SCOPE, NEEDS_CLASSIFICATION
    }
}
