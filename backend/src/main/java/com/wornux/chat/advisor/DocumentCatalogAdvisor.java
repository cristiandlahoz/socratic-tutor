package com.wornux.chat.advisor;

import com.wornux.chat.tools.ToolUsageAuditService;
import com.wornux.documentingest.DocumentCatalogPromptService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
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
  public ChatClientResponse adviseCall(
      ChatClientRequest request, @NonNull CallAdvisorChain chain) {
    return chain.nextCall(appendInventory(request));
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
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
