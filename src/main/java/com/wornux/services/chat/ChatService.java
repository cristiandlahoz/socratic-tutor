package com.wornux.services.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.ai.profile.ProfileAwareResponseAdvisor;
import com.wornux.ai.profile.TurnProfileInferenceService;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.config.ProfileProperties;
import com.wornux.dtos.chat.*;
import com.wornux.services.profile.StudentProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
  private final ChatUsageService chatUsageService;
  private final ChatCompactionService chatCompactionService;
  private final ToolUsageAuditService toolUsageAuditService;
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
    this.chatUsageService = chatUsageService;
    this.chatCompactionService = chatCompactionService;
    this.toolUsageAuditService = toolUsageAuditService;
  }

  public Flux<String> chatStream(
      UUID turnId,
      String userInput,
      UUID clientId,
      UUID conversationId,
      AskStudentQuestionTool.QuestionHandler questionHandler) {
    var promptTokens = new AtomicReference<Integer>();
    var promptSpec = chatClient.prompt()

        .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId.toString()))
        .toolContext(
          Map.of(
            ToolUsageAuditService.CLIENT_ID,
            clientId,
            ToolUsageAuditService.CONVERSATION_ID,
            conversationId,
            ToolUsageAuditService.TURN_ID,
            turnId))
        .user(userInput);
    if (questionHandler != null) {
      promptSpec = promptSpec.tools(new AskStudentQuestionTool(questionHandler));
    }

    return promptSpec.stream()
        .chatResponse()
        .doOnNext(response -> capturePromptTokens(response, promptTokens))
        .map(this::extractContentChunk)
        .filter(token -> !token.isBlank())
        .doOnComplete(() -> storePromptTokens(turnId, promptTokens.get()))
        .doOnCancel(() -> clearTurnState(turnId))
        .doOnError(_ -> clearTurnState(turnId));
  }

  public TurnFinalizationResult finalizeTurn(
      UUID turnId,
      UUID clientId,
      UUID conversationId,
      String userInput,
      String assistantResponse) {
    try {
      chatUsageService.updateActiveTranscriptInputTokens(conversationId, turnPromptTokens.remove(turnId));
      return new TurnFinalizationResult(compactConversationIfNeeded(conversationId));
    }
    finally {
      clearTurnState(turnId);
    }
  }

  private ChatCompactionStatus compactConversationIfNeeded(UUID conversationId) {
    try {
      return chatCompactionService.compactIfNeeded(conversationId);
    }
    catch (RuntimeException exception) {
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
    if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
      return;
    }

    Integer value = response.getMetadata().getUsage().getPromptTokens();
    if (value != null) {
      promptTokens.set(value);
    }
  }

  private String extractContentChunk(ChatResponse response) {
    if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
      return "";
    }

    var text = response.getResult().getOutput().getText();
    return text == null ? "" : text;
  }

}
