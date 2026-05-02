package com.wornux.ai.tools;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.ChatMessageEntity;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolResponseInspectionService {

  private final ChatMessageJpaRepository chatMessageRepository;

  public ToolResponseInspectionService(ChatMessageJpaRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional(readOnly = true)
  public List<ToolResponseView> findToolResponses(UUID clientId, UUID conversationId) {
    return chatMessageRepository.findDisplayMessages(conversationId, clientId).stream()
        .flatMap(message -> toolResponses(message).stream())
        .toList();
  }

  private List<ToolResponseView> toolResponses(ChatMessageEntity message) {
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
