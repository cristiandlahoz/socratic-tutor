package com.wornux.ai.advisor;

import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.document.DocumentCatalogPromptService;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class DocumentCatalogAdvisor implements CallAdvisor, StreamAdvisor {

  private final int order;
  private final DocumentCatalogPromptService catalogPromptService;

  public DocumentCatalogAdvisor(int order, DocumentCatalogPromptService catalogPromptService) {
    this.order = order;
    this.catalogPromptService = catalogPromptService;
  }

  @Override
  public @NullMarked ChatClientResponse adviseCall(
      ChatClientRequest request, CallAdvisorChain chain) {
    return chain.nextCall(appendInventory(request));
  }

  @Override
  public @NullMarked Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, StreamAdvisorChain chain) {
    return chain.nextStream(appendInventory(request));
  }

  private ChatClientRequest appendInventory(ChatClientRequest request) {
    var clientId = clientId(request);
    var inventory = catalogPromptService.buildInventoryPrompt(clientId);
    if (inventory.isBlank()) {
      return request;
    }

    List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
    messages.add(new SystemMessage(inventory));

    var promptBuilder = Prompt.builder().messages(messages);
    var options = request.prompt().getOptions();
    if (!Objects.isNull(options)) {
      promptBuilder.chatOptions(options);
    }

    return request
        .mutate()
        .prompt(promptBuilder.build())
        .context("document_inventory_present", true)
        .build();
  }

  private UUID clientId(ChatClientRequest request) {
    Object value = request.context().get(ToolUsageAuditService.CLIENT_ID);
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(String.valueOf(value));
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  @Override
  public @NullUnmarked String getName() {
    return "document-catalog-advisor";
  }

  @Override
  public int getOrder() {
    return order;
  }
}
