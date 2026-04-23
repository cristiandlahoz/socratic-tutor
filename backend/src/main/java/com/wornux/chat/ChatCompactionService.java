package com.wornux.chat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatCompactionService {

  private static final String COMPACTION_SYSTEM_PROMPT =
      """
      You compress a tutoring conversation into a continuation memory.
      Return JSON only.

      Rules:
      - Preserve the same language used by the student.
      - Focus on what is needed to continue the tutoring session smoothly.
      - Include the student's current goal, what they already understand, what they still find confusing,
        and the next best teaching step.
      - Be concrete and concise.
      - Do not mention that this is a summary.
      - Do not use markdown.
      """;

  private final ChatJpaRepository chatRepository;
  private final ChatTranscriptJpaRepository chatTranscriptRepository;
  private final ChatMessageJpaRepository chatMessageRepository;
  private final ChatUsageService chatUsageService;
  private final ChatModel chatModel;
  private final BeanOutputConverter<CompactedMemory> outputConverter =
      new BeanOutputConverter<>(CompactedMemory.class);

  @Value("${spring.ai.ollama.chat.model}")
  private String compactionModel;

  public ChatCompactionService(
      ChatJpaRepository chatRepository,
      ChatTranscriptJpaRepository chatTranscriptRepository,
      ChatMessageJpaRepository chatMessageRepository,
      ChatUsageService chatUsageService,
      ChatModel chatModel) {
    this.chatRepository = chatRepository;
    this.chatTranscriptRepository = chatTranscriptRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.chatUsageService = chatUsageService;
    this.chatModel = chatModel;
  }

  @Transactional
  public ChatCompactionStatus compactIfNeeded(UUID chatId) {
    if (!chatUsageService.exceedsCompactionThreshold(chatId)) {
      return ChatCompactionStatus.none();
    }

    var chat =
        chatRepository
            .findById(chatId)
            .orElseThrow(() -> new IllegalStateException("Chat not found: " + chatId));
    var activeTranscript = chat.getCurrentTranscript();
    if (activeTranscript == null) {
      return ChatCompactionStatus.none();
    }

    var transcriptMessages =
        chatMessageRepository.findByTranscript_IdOrderByIdAsc(activeTranscript.getId());
    if (transcriptMessages.isEmpty() && activeTranscript.memoryText().isBlank()) {
      return ChatCompactionStatus.none();
    }

    var compactedMemory = summarize(activeTranscript, transcriptMessages);
    if (compactedMemory == null || compactedMemory.isBlank()) {
      return ChatCompactionStatus.none();
    }

    var compactedTranscript =
        ChatTranscriptEntity.createFromCompaction(chat, activeTranscript, compactedMemory);
    compactedTranscript.setInputTokens(null);
    compactedTranscript = chatTranscriptRepository.save(compactedTranscript);
    chat.activateTranscript(compactedTranscript);
    chatRepository.save(chat);
    return new ChatCompactionStatus(
        true,
        compactedTranscript.getCompactionLevel(),
        compactedTranscript.getCompactedFromTranscriptId());
  }

  private String summarize(
      ChatTranscriptEntity transcript, List<ChatMessageEntity> transcriptMessages) {
    var prompt =
        Prompt.builder()
            .messages(
                new SystemMessage(COMPACTION_SYSTEM_PROMPT),
                new UserMessage(buildCompactionInput(transcript, transcriptMessages)))
            .chatOptions(
                OllamaChatOptions.builder()
                    .model(compactionModel)
                    .temperature(0.0)
                    .format(outputConverter.getJsonSchemaMap())
                    .build())
            .build();

    var response = chatModel.call(prompt);
    var rawOutput =
        Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
    var compactedMemory = outputConverter.convert(rawOutput);
    return compactedMemory == null ? null : compactedMemory.text();
  }

  private String buildCompactionInput(
      ChatTranscriptEntity transcript, List<ChatMessageEntity> transcriptMessages) {
    String existingMemory = transcript.memoryText().isBlank() ? "(none)" : transcript.memoryText();
    String transcriptBody =
        transcriptMessages.stream()
            .map(
                message ->
                    "%s: %s".formatted(message.getRole().toUpperCase(), message.getContent()))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("(empty)");

    return """
    Existing memory:
    %s

    Full active transcript:
    %s

    Return JSON only with the fields required by this schema.
    """
        .formatted(existingMemory, transcriptBody);
  }

  private record CompactedMemory(String text) {}
}
