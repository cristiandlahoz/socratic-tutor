package com.wornux.services.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.repositories.conversation.ConversationRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-06-24T12:00:00Z");

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ActiveAcademicContextResolver contextResolver;

    @Mock
    private SessionService sessionService;

    @Mock
    private ConversationService self;

    private ConversationService conversationService;
    private ActiveAcademicContext context;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, contextResolver, sessionService, self);
        context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberKind.STUDENT);
    }

    @Test
    void createsDomainConversationForTheActiveMembership() {
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = conversationService.createConversation("  explain   recursion  ");

        var conversation = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversation.capture());
        assertThat(conversation.getValue().getCreatedByGroupClassMember().getId())
                .isEqualTo(context.groupClassMemberId());
        assertThat(conversation.getValue().getCreatedByTenantAccount().getId()).isEqualTo(context.tenantAccountId());
        assertThat(conversation.getValue().getGroupClass().getId()).isEqualTo(context.groupClassId());
        assertThat(conversation.getValue().getTitle()).isEqualTo("explain recursion");
        assertThat(summary.id()).isEqualTo(conversation.getValue().getId());
    }

    @Test
    void loadsOnlyDisplayableEventsFromTheOwnedSessionHistory() {
        var conversationId = UUID.randomUUID();
        var conversation = ownedConversation(conversationId);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(
            conversationRepository
                    .findByIdAndCreatedByGroupClassMember_Id(conversationId, context.groupClassMemberId()))
                .thenReturn(Optional.of(conversation));
        when(sessionService.findById(conversationId.toString())).thenReturn(
            Session.builder().id(conversationId.toString()).userId(context.groupClassMemberId().toString()).build());
        when(sessionService.getEvents(any(String.class), any(EventFilter.class))).thenReturn(
            List.of(
                event(conversationId, new UserMessage("visible question")),
                event(conversationId, new AssistantMessage("visible answer")),
                syntheticEvent(conversationId, new AssistantMessage("internal summary")),
                branchedEvent(conversationId, new UserMessage("agent prompt")),
                event(conversationId, new SystemMessage("internal system policy")),
                event(
                    conversationId,
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "search", "{}")))
                            .build()),
                event(conversationId, new AssistantMessage(" "))));

        var history = conversationService.loadConversation(conversationId);

        assertThat(history).extracting(message -> message.content())
                .containsExactly("visible question", "visible answer");
        var filter = ArgumentCaptor.forClass(EventFilter.class);
        verify(sessionService).getEvents(org.mockito.ArgumentMatchers.eq(conversationId.toString()), filter.capture());
        assertThat(filter.getValue().excludeSynthetic()).isTrue();
        assertThat(filter.getValue().excludeArchived()).isFalse();
    }

    @Test
    void rejectsHistoryBeforeReadingSessionEventsWhenConversationIsNotOwned() {
        var conversationId = UUID.randomUUID();
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(
            conversationRepository
                    .findByIdAndCreatedByGroupClassMember_Id(conversationId, context.groupClassMemberId()))
                .thenReturn(Optional.empty());

        assertThat(conversationService.loadConversation(conversationId)).isEmpty();

        verify(sessionService, never()).findById(any());
        verify(sessionService, never()).getEvents(any(), any());
    }

    private Conversation ownedConversation(UUID conversationId) {
        var conversation = new Conversation();
        conversation.setId(conversationId);
        return conversation;
    }

    private SessionEvent event(UUID conversationId, org.springframework.ai.chat.messages.Message message) {
        return SessionEvent.builder()
                .sessionId(conversationId.toString())
                .timestamp(EVENT_TIME)
                .message(message)
                .build();
    }

    private SessionEvent syntheticEvent(UUID conversationId, org.springframework.ai.chat.messages.Message message) {
        return SessionEvent.builder()
                .sessionId(conversationId.toString())
                .timestamp(EVENT_TIME)
                .message(message)
                .metadata(SessionEvent.METADATA_SYNTHETIC, true)
                .build();
    }

    private SessionEvent branchedEvent(UUID conversationId, org.springframework.ai.chat.messages.Message message) {
        return SessionEvent.builder()
                .sessionId(conversationId.toString())
                .timestamp(EVENT_TIME)
                .message(message)
                .branch("internal.agent")
                .build();
    }
}
