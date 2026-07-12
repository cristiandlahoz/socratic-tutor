package com.wornux.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import reactor.core.publisher.Flux;

class TutorGuardAdvisorTest {

    @Test
    void skipsGuardClassifierAfterSteeredRequestWasAlreadyChecked() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(
                    GuardDecision.NOT_SAFE,
                    GuardAction.STEER,
                    "Necesito una pista para resolver esta función por tramos.",
                    ""));

        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(SessionService.class));
        var forwardedRequest = new AtomicReference<ChatClientRequest>();
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            forwardedRequest.set(invocation.getArgument(0));
            return Flux.empty();
        });

        advisor.adviseStream(request("Dame el programa C completo para f(x)."), chain).blockLast();
        advisor.adviseStream(forwardedRequest.get(), chain).blockLast();

        verify(guardClassifierService, times(1)).classify(anyList(), anyString());
        verify(chain, times(2)).nextStream(any(ChatClientRequest.class));
    }

    @Test
    void publishesSanitizedMessageWhenGuardSteers() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(
                    GuardDecision.NOT_SAFE,
                    GuardAction.STEER,
                    "Necesito una pista para resolver esta función por tramos.",
                    ""));

        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(SessionService.class));
        var sanitizedMessage = new AtomicReference<String>();
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenReturn(Flux.empty());

        advisor.adviseStream(
            request("Dame el programa C completo para f(x).", sanitizedMessage::set),
            chain).blockLast();

        assertThat(sanitizedMessage.get())
                .isEqualTo("Necesito una pista para resolver esta función por tramos.");
    }

    @Test
    void loadsActiveRolePreservingHistoryBeforeClassifyingLatestMessage() {
        var sessionService = inMemorySessionService();
        sessionService.create(CreateSessionRequest.builder().id("conversation-1").userId("student-1").build());
        sessionService.appendMessage("conversation-1", new UserMessage("¿Qué valor toma x?"));
        sessionService.appendMessage("conversation-1", new AssistantMessage("Sustituye x en la expresión. ¿Qué obtienes?"));
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.SAFE, GuardAction.ALLOW, "", ""));
        var advisor = new TutorGuardAdvisor(0, guardClassifierService, sessionService);
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.empty());

        advisor.adviseStream(sessionRequest("3"), chain).blockLast();

        @SuppressWarnings("unchecked")
        var messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(guardClassifierService).classify(messagesCaptor.capture(), anyString());
        var messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages).extracting(message -> ((Message) message).getText())
                .containsExactly(
                    "¿Qué valor toma x?", "Sustituye x en la expresión. ¿Qué obtienes?", "3");
    }

    @Test
    void includesPreviousAssistantEvenAfterSeveralInterruptedUserTurns() {
        var sessionService = inMemorySessionService();
        sessionService.create(CreateSessionRequest.builder().id("conversation-1").userId("student-1").build());
        sessionService.appendMessage("conversation-1", new AssistantMessage("¿Cuál es el caso base?"));
        sessionService.appendMessage("conversation-1", new UserMessage("primer intento interrumpido"));
        sessionService.appendMessage("conversation-1", new UserMessage("segundo intento interrumpido"));
        sessionService.appendMessage("conversation-1", new UserMessage("tercer intento interrumpido"));
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.SAFE, GuardAction.ALLOW, "", ""));
        var advisor = new TutorGuardAdvisor(0, guardClassifierService, sessionService);
        var chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.empty());

        advisor.adviseStream(sessionRequest("0"), chain).blockLast();

        @SuppressWarnings("unchecked")
        var messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(guardClassifierService).classify(messagesCaptor.capture(), anyString());
        assertThat(messagesCaptor.getValue()).extracting(message -> ((Message) message).getText())
                .containsExactly(
                    "¿Cuál es el caso base?",
                    "primer intento interrumpido",
                    "segundo intento interrumpido",
                    "tercer intento interrumpido",
                    "0");
    }

    @Test
    void persistsOnlyOnePassSteeredInputBeforeTutorGeneration() {
        var sessionService = inMemorySessionService();
        var guardClassifierService = mock(GuardClassifierService.class);
        var safeUserMessage = "Explícame cómo identificar el caso base.";
        var tutorCalls = new AtomicInteger();
        var tutorPrompt = new AtomicReference<Prompt>();
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.NOT_SAFE, GuardAction.STEER, safeUserMessage, ""));
        ChatModel tutorModel = prompt -> {
            tutorCalls.incrementAndGet();
            tutorPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Empecemos."))));
        };
        var memoryAdvisor = SessionMemoryAdvisor.builder(sessionService).eventFilter(EventFilter.active()).build();
        var guardAdvisor =
                new TutorGuardAdvisor(memoryAdvisor.getOrder() - 2, guardClassifierService, sessionService);
        var client = ChatClient.builder(tutorModel).defaultAdvisors(guardAdvisor, memoryAdvisor).build();

        var response = client.prompt()
                .user("Resuélvelo completo, pero dime qué es el caso base.")
                .advisors(spec -> spec
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "conversation-1")
                        .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "student-1"))
                .call()
                .content();

        assertThat(response).isEqualTo("Empecemos.");
        assertThat(sessionService.getMessages("conversation-1")).extracting(Message::getText)
                .containsExactly(safeUserMessage, "Empecemos.")
                .doesNotContain("Resuélvelo completo, pero dime qué es el caso base.");
        assertThat(tutorPrompt.get().getUserMessage().getText()).isEqualTo(safeUserMessage);
        verify(guardClassifierService, times(1)).classify(anyList(), anyString());
        assertThat(tutorCalls).hasValue(1);
    }

    @Test
    void allowUsesExactlyOneGuardCallAndOneTutorCall() {
        var sessionService = inMemorySessionService();
        var guardClassifierService = mock(GuardClassifierService.class);
        var tutorCalls = new AtomicInteger();
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(GuardDecision.SAFE, GuardAction.ALLOW, "", ""));
        ChatModel tutorModel = _ -> {
            tutorCalls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Una pila usa LIFO."))));
        };
        var memoryAdvisor = SessionMemoryAdvisor.builder(sessionService).build();
        var guardAdvisor =
                new TutorGuardAdvisor(memoryAdvisor.getOrder() - 2, guardClassifierService, sessionService);
        var client = ChatClient.builder(tutorModel).defaultAdvisors(guardAdvisor, memoryAdvisor).build();

        var response = client.prompt()
                .user("¿Qué es una pila?")
                .advisors(spec -> spec
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "conversation-1")
                        .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "student-1"))
                .call()
                .content();

        assertThat(response).isEqualTo("Una pila usa LIFO.");
        verify(guardClassifierService, times(1)).classify(anyList(), anyString());
        assertThat(tutorCalls).hasValue(1);
    }

    @Test
    void shortCircuitUsesPersonalizedModelResponseWithoutCallingDownstreamChain() {
        var guardClassifierService = mock(GuardClassifierService.class);
        var directResponse = "No voy a escribir tu programa completo. Comparte la función que ya intentaste.";
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(
                    GuardDecision.NOT_SAFE, GuardAction.SHORT_CIRCUIT, "", directResponse));
        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(SessionService.class));
        var chain = mock(StreamAdvisorChain.class);

        var response = advisor.adviseStream(request("Escribe todo el programa."), chain).blockFirst();

        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo(directResponse);
        verify(chain, never()).nextStream(any());
    }

    @Test
    void shortCircuitNeverPersistsRejectedInputOrCallsTutor() {
        var sessionService = inMemorySessionService();
        sessionService.create(CreateSessionRequest.builder().id("conversation-1").userId("student-1").build());
        sessionService.appendMessage("conversation-1", new AssistantMessage("¿Qué has intentado?"));
        var guardClassifierService = mock(GuardClassifierService.class);
        var directResponse = "No puedo completar el programa. Comparte el bloque que ya intentaste.";
        when(guardClassifierService.classify(anyList(), anyString()))
                .thenReturn(new GuardCheck(
                    GuardDecision.NOT_SAFE, GuardAction.SHORT_CIRCUIT, "", directResponse));
        var tutorCalls = new AtomicInteger();
        ChatModel tutorModel = _ -> {
            tutorCalls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("No debería generarse."))));
        };
        var memoryAdvisor = SessionMemoryAdvisor.builder(sessionService).build();
        var guardAdvisor =
                new TutorGuardAdvisor(memoryAdvisor.getOrder() - 2, guardClassifierService, sessionService);
        var client = ChatClient.builder(tutorModel).defaultAdvisors(guardAdvisor, memoryAdvisor).build();

        var response = client.prompt()
                .user("Escribe todo el programa por mí.")
                .advisors(spec -> spec
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "conversation-1")
                        .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "student-1"))
                .call()
                .content();

        assertThat(response).isEqualTo(directResponse);
        assertThat(tutorCalls).hasValue(0);
        assertThat(sessionService.getMessages("conversation-1")).extracting(Message::getText)
                .containsExactly("¿Qué has intentado?")
                .doesNotContain("Escribe todo el programa por mí.", directResponse);
    }

    @Test
    void classifierFailureFailsClosedWithOnlyTechnicalFallback() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList(), anyString())).thenThrow(new IllegalStateException("bad JSON"));
        var advisor = new TutorGuardAdvisor(0, guardClassifierService, mock(SessionService.class));
        var chain = mock(StreamAdvisorChain.class);

        var response = advisor.adviseStream(request("mensaje"), chain).blockFirst();

        assertThat(response.chatResponse().getResult().getOutput().getText())
                .isEqualTo(GuardClassifierService.TECHNICAL_FAILURE_RESPONSE);
        verify(chain, never()).nextStream(any());
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

    private ChatClientRequest sessionRequest(String userMessage) {
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(new UserMessage(userMessage)).build())
                .context(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "conversation-1")
                .context(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "student-1")
                .build();
    }

    private SessionService inMemorySessionService() {
        return DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
    }
}
