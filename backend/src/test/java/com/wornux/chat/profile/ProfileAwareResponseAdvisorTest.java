package com.wornux.chat.profile;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileAwareResponseAdvisorTest {

    private final StudentProfileService studentProfileService = mock(StudentProfileService.class);
    private final ProfileProperties profileProperties = new ProfileProperties();
    private final ProfileAwareResponseAdvisor advisor = new ProfileAwareResponseAdvisor(150, studentProfileService, profileProperties);

    @Test
    void injects_profile_system_message_when_enabled() {
        UUID clientId = UUID.randomUUID();
        when(studentProfileService.load(clientId)).thenReturn(new StudentProfileSnapshot(
                "es",
                StudentOverallLevel.BEGINNER,
                HelpMode.GUIDED,
                true,
                List.of(TopicKey.LOOPS),
                List.of("counter_vs_accumulator"),
                new BigDecimal("0.420"),
                3L
        ));

        var patched = invoke(clientId);

        assertThat(patched.prompt().getInstructions()).hasSize(2);
        assertThat(patched.prompt().getInstructions().getLast().getText()).contains("Priority weak topics: [loops]");
        assertThat(patched.context()).containsEntry(ProfileAwareResponseAdvisor.PROFILE_VERSION_CONTEXT_KEY, 3L);
    }

    @Test
    void leaves_request_unchanged_in_shadow_mode() {
        UUID clientId = UUID.randomUUID();
        profileProperties.setShadowMode(true);
        when(studentProfileService.load(clientId)).thenReturn(StudentProfileSnapshot.anonymous());

        var patched = invoke(clientId);

        assertThat(patched.prompt().getInstructions()).hasSize(1);
        assertThat(patched.context()).containsEntry(ProfileAwareResponseAdvisor.PROFILE_VERSION_CONTEXT_KEY, 0L);
    }

    private ChatClientRequest invoke(UUID clientId) {
        final ChatClientRequest[] captured = new ChatClientRequest[1];
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        });
        advisor.adviseCall(request(clientId), chain);
        return captured[0];
    }

    private ChatClientRequest request(UUID clientId) {
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(new UserMessage("Explicame un for")).build())
                .context(Map.of(ProfileAwareResponseAdvisor.CLIENT_ID_CONTEXT_KEY, clientId))
                .build();
    }
}
