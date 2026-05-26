package com.wornux.ai.tools;

import com.wornux.data.entities.ChatMessage;
import com.wornux.data.repositories.chat.ChatMessageRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolResponseInspectionService {

  private final ChatMessageRepository chatMessageRepository;

  public ToolResponseInspectionService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional(readOnly = true)
  public List<ToolResponseView> findToolResponses(UUID clientId, UUID conversationId) {
    return chatMessageRepository.findDisplayMessages(conversationId, clientId).stream()
        .flatMap(message -> toolResponses(message).stream())
        .toList();
  }

  private List<ToolResponseView> toolResponses(ChatMessage message) {
    Object rawResponses = message.getMetadata().get("toolResponses");
    if (!(rawResponses instanceof List<?> responses)) {
      return List.of();
    }
    return responses.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(
            response ->
                new ToolResponseView(
                    message.getId(),
                    String.valueOf(response.get("id")),
                    String.valueOf(response.get("name")),
                    String.valueOf(response.get("responseData"))))
        .toList();
  }

  public record ToolResponseView(Long messageId, String id, String name, String responseData) {}
}
