package com.wornux.chat.routing;

import com.wornux.chat.prompt.PromptMessageUtils;
import com.wornux.chat.prompt.TutorPromptResources;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PedagogicalRoutingAdvisor implements CallAdvisor, StreamAdvisor {

    private final int order;
    private final PedagogicalRoutingService routingService;
    private final TutorPromptResources promptResources;

    public PedagogicalRoutingAdvisor(int order, PedagogicalRoutingService routingService, TutorPromptResources promptResources) {
        this.order = order;
        this.routingService = routingService;
        this.promptResources = promptResources;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        return chain.nextCall(applyRouting(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        return chain.nextStream(applyRouting(request));
    }

    ChatClientRequest applyRouting(ChatClientRequest request) {
        String userText = PromptMessageUtils.extractLastUserText(request.prompt());
        PedagogicalRoutingMode mode = routingService.classify(userText);

        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(new SystemMessage(instructionFor(mode)));

        var promptBuilder = Prompt.builder()
                .messages(messages);
        var options = request.prompt().getOptions();
        if (!Objects.isNull(options)) {
            promptBuilder.chatOptions(options);
        }

        return request.mutate()
                .prompt(promptBuilder.build())
                .context("teaching_mode", mode.name().toLowerCase(Locale.ROOT))
                .build();
    }

    private String instructionFor(PedagogicalRoutingMode mode) {
        return switch (mode) {
            case DIRECT_REFERENCE -> promptResources.routingDirectReference()
                    + "\n\n"
                    + promptResources.directReferenceExamples();
            case EXERCISE_GUIDANCE -> promptResources.routingExerciseGuidance()
                    + "\n\n"
                    + promptResources.exerciseGuidanceExamples();
            case DEBUG_MY_ATTEMPT -> promptResources.routingDebugMyAttempt()
                    + "\n\n"
                    + promptResources.exerciseGuidanceExamples();
            case CONCEPT_EXPLANATION -> promptResources.routingConceptExplanation();
        };
    }

    @Override
    public @NullUnmarked String getName() {
        return "pedagogical-routing-advisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
