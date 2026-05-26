package com.wornux.ai.profile;

import com.wornux.config.*;
import com.wornux.services.profile.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
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

@RequiredArgsConstructor
public class ProfileAwareResponseAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CLIENT_ID_CONTEXT_KEY = "client_id";
    public static final String PROFILE_VERSION_CONTEXT_KEY = "profile_version";

    private final int order;
    private final StudentProfileService studentProfileService;
    private final ProfileProperties profileProperties;
    private final StudentProfilePromptMapper profilePromptMapper;

    @Override
    public @NullMarked ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(applyProfileContext(request));
    }

    @Override
    public @NullMarked Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(applyProfileContext(request));
    }

    private ChatClientRequest applyProfileContext(ChatClientRequest request) {
        if (!profileProperties.isEnabled()) {
            return request;
        }

        var clientId = clientIdFrom(request.context());
        if (clientId == null) {
            return request;
        }

        var profile = studentProfileService.load(clientId);
        var mutated = request.mutate().context(PROFILE_VERSION_CONTEXT_KEY, profile.profileVersion());
        if (profileProperties.isShadowMode()) {
            return mutated.build();
        }

        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(new SystemMessage(profilePromptMapper.toPrompt(profile)));

        var options = request.prompt().getOptions();
        var promptBuilder = Prompt.builder().messages(messages);

        if (!Objects.isNull(options))
            promptBuilder.chatOptions(options);

        return mutated.prompt(promptBuilder.build()).build();
    }

    private UUID clientIdFrom(Map<String, Object> context) {
        Object rawClientId = context.get(CLIENT_ID_CONTEXT_KEY);
        if (rawClientId instanceof UUID clientId) {
            return clientId;
        }
        if (rawClientId instanceof String clientId) {
            return UUID.fromString(clientId);
        }
        return null;
    }

    @Override
    public @NullMarked String getName() {
        return "profile-aware-response-advisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
