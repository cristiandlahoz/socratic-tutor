package com.wornux.ai.profile;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
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
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
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

@RequiredArgsConstructor
public class ProfileAwareResponseAdvisor implements CallAdvisor, StreamAdvisor {

  public static final String CLIENT_ID_CONTEXT_KEY = "client_id";
  public static final String PROFILE_VERSION_CONTEXT_KEY = "profile_version";

  private final int order;
  private final StudentProfileService studentProfileService;
  private final ProfileProperties profileProperties;

  @Override
  public @NullMarked ChatClientResponse adviseCall(
      ChatClientRequest request, CallAdvisorChain chain) {
    return chain.nextCall(applyProfileContext(request));
  }

  @Override
  public @NullMarked Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, StreamAdvisorChain chain) {
    return chain.nextStream(applyProfileContext(request));
  }

  private ChatClientRequest applyProfileContext(ChatClientRequest request) {
    if (!profileProperties.isEnabled()) {
      return request;
    }

    var clientId = clientIdFrom(request.context());
    if (clientId == null) {
      return request;
    }

    var profile = studentProfileService.load(clientId);
    var mutated = request.mutate().context(PROFILE_VERSION_CONTEXT_KEY, profile.profileVersion());
    if (profileProperties.isShadowMode()) {
      return mutated.build();
    }

    List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
    messages.add(new SystemMessage(buildProfileInstruction(profile)));

    var options = request.prompt().getOptions();
    var promptBuilder = Prompt.builder().messages(messages);

    if (!Objects.isNull(options)) promptBuilder.chatOptions(options);

    return mutated.prompt(promptBuilder.build()).build();
  }

  private String buildProfileInstruction(StudentProfileSnapshot profile) {
    return """
    Student adaptation snapshot:
    - Preferred language: %s
    - Level: %s
    - Help mode: %s
    - Concrete examples needed: %s
    - Priority weak topics: %s
    - Active misconceptions: %s

    Adaptation rules:
    - Match the student's level and keep the response concise.
    - If the level is beginner or help mode is guided, explain in smaller steps.
    - If weak topics are present, slow down there and track state explicitly.
    - If active misconceptions are present, correct them before adding new detail.
    - If concrete examples are needed, include one short example or trace.
    """
        .formatted(
            profile.preferredLanguage(),
            profile.overallLevel().name().toLowerCase(),
            profile.helpMode().name().toLowerCase(),
            profile.needsConcreteExamples(),
            profile.topWeakTopics().stream().map(Enum::name).map(String::toLowerCase).toList(),
            profile.activeMisconceptions());
  }

  private UUID clientIdFrom(Map<String, Object> context) {
    Object rawClientId = context.get(CLIENT_ID_CONTEXT_KEY);
    if (rawClientId instanceof UUID clientId) {
      return clientId;
    }
    if (rawClientId instanceof String clientId) {
      return UUID.fromString(clientId);
    }
    return null;
  }

  @Override
  public @NullMarked String getName() {
    return "profile-aware-response-advisor";
  }

  @Override
  public int getOrder() {
    return order;
  }
}
