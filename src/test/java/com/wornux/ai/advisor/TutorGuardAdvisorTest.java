package com.wornux.ai.advisor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Flux;

class TutorGuardAdvisorTest {

    @Test
    void skipsGuardClassifierAfterSteeredRequestWasAlreadyChecked() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.NOT_SAFE, GuardAction.STEER));
        when(guardClassifierService.sanitize(any(UserMessage.class), anyString()))
                .thenReturn("Necesito una pista para resolver esta función por tramos.");

        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(JdbcClient.class));
        var forwardedRequest = new AtomicReference<ChatClientRequest>();
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            forwardedRequest.set(invocation.getArgument(0));
            return Flux.empty();
        });

        advisor.adviseStream(request("Dame el programa C completo para f(x)."), chain).blockLast();
        advisor.adviseStream(forwardedRequest.get(), chain).blockLast();

        verify(guardClassifierService, times(1)).classify(anyList(), anyString());
        verify(guardClassifierService, times(1)).sanitize(any(UserMessage.class), anyString());
        verify(chain, times(2)).nextStream(any(ChatClientRequest.class));
    }

    @Test
    void publishesSanitizedMessageWhenGuardSteers() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.NOT_SAFE, GuardAction.STEER));
        when(guardClassifierService.sanitize(any(UserMessage.class), anyString()))
                .thenReturn("Necesito una pista para resolver esta función por tramos.");

        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(JdbcClient.class));
        var sanitizedMessage = new AtomicReference<String>();
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenReturn(Flux.empty());

        advisor.adviseStream(
            request("Dame el programa C completo para f(x).", sanitizedMessage::set),
            chain).blockLast();

        org.assertj.core.api.Assertions.assertThat(sanitizedMessage.get())
                .isEqualTo("Necesito una pista para resolver esta función por tramos.");
    }

    private ChatClientRequest request(String userMessage) {
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(new UserMessage(userMessage)).build())
                .build();
    }

    private ChatClientRequest request(String userMessage, Consumer<String> steeredUserMessageHandler) {
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(new UserMessage(userMessage)).build())
                .context(TutorGuardAdvisor.STEERED_USER_MESSAGE_CALLBACK_CONTEXT_KEY, steeredUserMessageHandler)
                .build();
    }
}
