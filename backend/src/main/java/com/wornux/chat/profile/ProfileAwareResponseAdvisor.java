package com.wornux.chat.profile;

import org.jspecify.annotations.NonNull;
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
import java.util.Map;
import java.util.UUID;

public class ProfileAwareResponseAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CLIENT_ID_CONTEXT_KEY = "client_id";
    public static final String PROFILE_VERSION_CONTEXT_KEY = "profile_version";

    private final int order;
    private final StudentProfileService studentProfileService;
    private final ProfileProperties profileProperties;

    public ProfileAwareResponseAdvisor(int order, StudentProfileService studentProfileService, ProfileProperties profileProperties) {
        this.order = order;
        this.studentProfileService = studentProfileService;
        this.profileProperties = profileProperties;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        return chain.nextCall(applyProfileContext(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
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
        messages.add(new SystemMessage(buildProfileInstruction(profile)));
        Prompt prompt = Prompt.builder()
                .messages(messages)
                .chatOptions(request.prompt().getOptions())
                .build();

        return mutated.prompt(prompt).build();
    }

    private String buildProfileInstruction(StudentProfileSnapshot profile) {
        return """
                Pedagogical Student Profile:
                - Preferred language: %s
                - Estimated level: %s
                - Help mode: %s
                - Needs concrete examples: %s
                - Weak topics: %s
                - Active misconceptions: %s

                Response rules:
                - Match the student's level without sounding condescending.
                - If weak topics are present, use smaller steps and explicit state tracking there.
                - If active misconceptions are present, correct them clearly before moving on.
                - If concrete examples are needed, use one short example or trace before asking a follow-up.
                """.formatted(
                profile.preferredLanguage(),
                profile.overallLevel().name().toLowerCase(),
                profile.helpMode().name().toLowerCase(),
                profile.needsConcreteExamples(),
                profile.topWeakTopics().stream().map(Enum::name).map(String::toLowerCase).toList(),
                profile.activeMisconceptions()
        );
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
    public String getName() {
        return "profile-aware-response-advisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
