package com.wornux.chat;

import com.wornux.chat.profile.ProfileAwareResponseAdvisor;
import com.wornux.chat.profile.StudentProfileService;
import com.wornux.chat.profile.TurnProfileInferenceService;
import com.wornux.chat.tools.ToolUsageAuditService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final StudentProfileService studentProfileService;
    private final TurnProfileInferenceService turnProfileInferenceService;
    private final ToolUsageAuditService toolUsageAuditService;

    public ChatService(ChatClient chatClient,
                       ConversationService conversationService,
                       StudentProfileService studentProfileService,
                       TurnProfileInferenceService turnProfileInferenceService,
                       ToolUsageAuditService toolUsageAuditService) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.studentProfileService = studentProfileService;
        this.turnProfileInferenceService = turnProfileInferenceService;
        this.toolUsageAuditService = toolUsageAuditService;
    }

    public Flux<String> chatStream(String userInput, UUID clientId, UUID conversationId) {
        var turnId = UUID.randomUUID();
        var profileSnapshot = studentProfileService.load(clientId);
        var responseBuilder = new StringBuilder();
        return chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, conversationId.toString())
                        .param(ProfileAwareResponseAdvisor.CLIENT_ID_CONTEXT_KEY, clientId))
                .toolContext(Map.of(
                        ToolUsageAuditService.CLIENT_ID, clientId,
                        ToolUsageAuditService.CONVERSATION_ID, conversationId,
                        ToolUsageAuditService.TURN_ID, turnId,
                        ToolUsageAuditService.PROFILE_VERSION, profileSnapshot.profileVersion()
                ))
                .user(userInput)
                .stream()
                .content()
                .doOnNext(responseBuilder::append)
                .doOnComplete(() -> persistProfileSignals(clientId, conversationId, turnId, userInput, responseBuilder.toString()))
                .doOnError(_ -> toolUsageAuditService.drainTurnAudits(turnId));
    }

    private void persistProfileSignals(UUID clientId, UUID conversationId, UUID turnId, String userInput, String assistantResponse) {
        var audits = toolUsageAuditService.drainTurnAudits(turnId);
        var memoryWindow = conversationService.loadConversation(clientId, conversationId);
        var update = turnProfileInferenceService.infer(conversationId, turnId, userInput, assistantResponse, memoryWindow, audits);
        studentProfileService.applyTurnSignals(clientId, update);
    }
}
