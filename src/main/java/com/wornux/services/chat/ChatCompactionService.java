package com.wornux.services.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.data.repositories.conversation.ConversationSnapshotRepository;
import com.wornux.dtos.chat.ChatCompactionStatus;
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

    private static final int RETAINED_MESSAGE_COUNT = 4;
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

    private final ConversationService conversationService;
    private final ConversationSnapshotRepository snapshotRepository;
    private final ChatUsageService chatUsageService;
    private final ChatModel chatModel;
    private final BeanOutputConverter<CompactedMemory> outputConverter =
            new BeanOutputConverter<>(CompactedMemory.class);

    @Value("${spring.ai.ollama.chat.model}")
    private String compactionModel;

    public ChatCompactionService(
            ConversationService conversationService,
            ConversationSnapshotRepository snapshotRepository,
            ChatUsageService chatUsageService,
            ChatModel chatModel) {
        this.conversationService = conversationService;
        this.snapshotRepository = snapshotRepository;
        this.chatUsageService = chatUsageService;
        this.chatModel = chatModel;
    }

    @Transactional
    public ChatCompactionStatus compactIfNeeded(UUID conversationId) {
        if (!chatUsageService.exceedsCompactionThreshold(conversationId)) {
            return ChatCompactionStatus.none();
        }

        var conversation = conversationService.requireOwnedConversation(conversationId);
        var activeSnapshot = conversation.getCurrentSnapshot();
        if (activeSnapshot == null || activeSnapshot.getMessages().isEmpty()) {
            return ChatCompactionStatus.none();
        }

        var compactedMemory = summarize(activeSnapshot);
        if (compactedMemory == null || compactedMemory.isBlank()) {
            return ChatCompactionStatus.none();
        }

        var retainedMessages = retainRecentMessages(activeSnapshot.getMessages());
        var compactedSnapshot = new ConversationSnapshot();
        compactedSnapshot.setConversation(conversation);
        compactedSnapshot.setPreviousSnapshot(activeSnapshot);
        compactedSnapshot.setSnapshotNo(activeSnapshot.getSnapshotNo() + 1L);
        compactedSnapshot.setCarryContext(new LinkedHashMap<>(Map.of("text", compactedMemory)));
        compactedSnapshot.setMessages(retainedMessages);
        compactedSnapshot.setMessageCount(retainedMessages.size());
        compactedSnapshot.setTokenCount(Math.max(1, compactedMemory.split("\\s+").length));
        compactedSnapshot.setVersion(activeSnapshot.getVersion() + 1L);
        compactedSnapshot.setCreatedAt(Instant.now());
        compactedSnapshot.setCompactedAt(Instant.now());

        var persistedSnapshot = snapshotRepository.save(compactedSnapshot);
        conversation.setCurrentSnapshot(persistedSnapshot);
        conversation.setVersion(conversation.getVersion() + 1L);
        conversation.setUpdatedAt(Instant.now());
        conversation.setCurrentSnapshot(persistedSnapshot);
        return new ChatCompactionStatus(true,
                Math.toIntExact(persistedSnapshot.getSnapshotNo()),
                activeSnapshot.getId());
    }

    private String summarize(ConversationSnapshot snapshot) {
        var prompt = Prompt.builder()
                .messages(new SystemMessage(COMPACTION_SYSTEM_PROMPT), new UserMessage(buildCompactionInput(snapshot)))
                .chatOptions(
                    OllamaChatOptions.builder()
                            .model(compactionModel)
                            .temperature(0.0)
                            .format(outputConverter.getJsonSchemaMap())
                            .build())
                .build();

        var response = chatModel.call(prompt);
        var rawOutput = Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
        var compactedMemory = outputConverter.convert(rawOutput);
        return compactedMemory.text();
    }

    private String buildCompactionInput(ConversationSnapshot snapshot) {
        String existingMemory = String.valueOf(snapshot.getCarryContext().getOrDefault("text", "(none)"));
        String transcriptBody = snapshot.getMessages()
                .stream()
                .map(message -> "%s: %s".formatted(message.get("role"), message.get("content")))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(empty)");

        return """
               Existing memory:
               %s

               Full active transcript:
               %s

               Return JSON only with the fields required by this schema.
               """.formatted(existingMemory, transcriptBody);
    }

    private List<Map<String, Object>> retainRecentMessages(List<Map<String, Object>> messages) {
        if (messages.size() <= RETAINED_MESSAGE_COUNT) {
            return List.copyOf(messages);
        }
        return List
                .copyOf(new ArrayList<>(messages.subList(messages.size() - RETAINED_MESSAGE_COUNT, messages.size())));
    }

    private record CompactedMemory(String text) {}
}
