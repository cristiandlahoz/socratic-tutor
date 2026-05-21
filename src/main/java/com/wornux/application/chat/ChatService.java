package com.wornux.application.chat;

import com.wornux.ai.profile.ProfileAwareResponseAdvisor;
import com.wornux.ai.profile.TurnProfileInferenceService;
import com.wornux.ai.config.ProfileProperties;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.application.profile.StudentProfileService;
import com.wornux.domain.chat.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class ChatService {

  private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

  private final ChatClient chatClient;
  private final ConversationService conversationService;
  private final ChatUsageService chatUsageService;
  private final ChatCompactionService chatCompactionService;
  private final StudentProfileService studentProfileService;
  private final TurnProfileInferenceService turnProfileInferenceService;
  private final ToolUsageAuditService toolUsageAuditService;
  private final ProfileProperties profileProperties;
  private final Map<UUID, Integer> turnPromptTokens = new ConcurrentHashMap<>();

  public ChatService(
      ChatClient chatClient,
      ConversationService conversationService,
      ChatUsageService chatUsageService,
      ChatCompactionService chatCompactionService,
      StudentProfileService studentProfileService,
      TurnProfileInferenceService turnProfileInferenceService,
      ToolUsageAuditService toolUsageAuditService,
      ProfileProperties profileProperties) {
    this.chatClient = chatClient;
    this.conversationService = conversationService;
    this.chatUsageService = chatUsageService;
    this.chatCompactionService = chatCompactionService;
    this.studentProfileService = studentProfileService;
    this.turnProfileInferenceService = turnProfileInferenceService;
    this.toolUsageAuditService = toolUsageAuditService;
    this.profileProperties = profileProperties;
  }

  public Flux<String> chatStream(
      UUID turnId,
      String userInput,
      UUID clientId,
      UUID conversationId,
      AskStudentQuestionTool.QuestionHandler questionHandler) {
    var profileSnapshot = studentProfileService.load(clientId);
    var promptTokens = new AtomicReference<Integer>();
    var promptSpec =
        chatClient
            .prompt()
            .advisors(
                advisorSpec ->
                    advisorSpec
                        .param(ChatMemory.CONVERSATION_ID, conversationId.toString())
                        .param(ToolUsageAuditService.CLIENT_ID, clientId)
                        .param(ProfileAwareResponseAdvisor.CLIENT_ID_CONTEXT_KEY, clientId))
            .toolContext(
                Map.of(
                    ToolUsageAuditService.CLIENT_ID,
                    clientId,
                    ToolUsageAuditService.CONVERSATION_ID,
                    conversationId,
                    ToolUsageAuditService.TURN_ID,
                    turnId,
                    ToolUsageAuditService.PROFILE_VERSION,
                    profileSnapshot.profileVersion()))
            .user(userInput);
    if (questionHandler != null) {
      promptSpec = promptSpec.tools(new AskStudentQuestionTool(questionHandler));
    }
    return promptSpec.stream()
        .chatResponse()
        .doOnNext(response -> capturePromptTokens(response, promptTokens))
        .map(this::extractContentChunk)
        .filter(token -> !token.isEmpty())
        .doOnComplete(() -> storePromptTokens(turnId, promptTokens.get()))
        .doOnError(_ -> clearTurnState(turnId));
  }

  public TurnFinalizationResult finalizeTurn(
      UUID turnId, UUID clientId, UUID conversationId, String userInput, String assistantResponse) {
    try {
      chatUsageService.updateActiveTranscriptInputTokens(
          conversationId, turnPromptTokens.remove(turnId));
      persistProfileSignals(clientId, conversationId, turnId, userInput, assistantResponse);
      return new TurnFinalizationResult(compactConversationIfNeeded(conversationId));
    } finally {
      clearTurnState(turnId);
    }
  }

  private ChatCompactionStatus compactConversationIfNeeded(UUID conversationId) {
    try {
      return chatCompactionService.compactIfNeeded(conversationId);
    } catch (RuntimeException exception) {
      logger.warn("Failed to compact conversation {}", conversationId, exception);
      return ChatCompactionStatus.none();
    }
  }

  private void storePromptTokens(UUID turnId, Integer promptTokens) {
    if (promptTokens != null) {
      turnPromptTokens.put(turnId, promptTokens);
    }
  }

  private void clearTurnState(UUID turnId) {
    turnPromptTokens.remove(turnId);
    toolUsageAuditService.drainTurnAudits(turnId);
  }

  private void capturePromptTokens(ChatResponse response, AtomicReference<Integer> promptTokens) {
    if (response == null) {
      return;
    }
    Usage usage = response.getMetadata().getUsage();
    promptTokens.set(usage.getPromptTokens());
  }

  private String extractContentChunk(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return "";
    } else {
      response.getResult();
    }
    var text = response.getResult().getOutput().getText();
    return text == null ? "" : text;
  }

  private void persistProfileSignals(
      UUID clientId, UUID conversationId, UUID turnId, String userInput, String assistantResponse) {
    var audits = toolUsageAuditService.drainTurnAudits(turnId);
    if (!profileProperties.isChatTurnSignalsEnabled()) {
      return;
    }
    var memoryWindow = conversationService.loadConversation(clientId, conversationId);
    var update =
        turnProfileInferenceService.infer(
            conversationId, turnId, userInput, assistantResponse, memoryWindow, audits);
    studentProfileService.applyTurnSignals(clientId, update);
  }
}
