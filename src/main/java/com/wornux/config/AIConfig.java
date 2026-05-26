package com.wornux.config;

import com.wornux.ai.advisor.DocumentCatalogAdvisor;
import com.wornux.ai.advisor.SubjectContextAdvisor;
import com.wornux.ai.advisor.TutorGuardAdvisor;
import com.wornux.ai.document.DocumentCatalogPromptService;
import com.wornux.ai.guard.*;
import com.wornux.ai.profile.ProfileAwareResponseAdvisor;
import com.wornux.ai.profile.StudentProfilePromptMapper;
import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.routing.PedagogicalRoutingAdvisor;
import com.wornux.ai.routing.PedagogicalRoutingService;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.services.profile.StudentProfileService;
import com.wornux.services.subject.SubjectConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

  private static final int CHAT_MEMORY_ADVISOR_ORDER = 100;
  private static final int SUBJECT_CONTEXT_ADVISOR_ORDER = 140;
  private static final int PROFILE_ADVISOR_ORDER = 150;
  private static final int DOCUMENT_CATALOG_ADVISOR_ORDER = 160;
  private static final int PEDAGOGICAL_ROUTING_ADVISOR_ORDER = 175;
  private static final int TUTOR_GUARD_ADVISOR_ORDER = 200;
  private static final int TOOL_CALL_ADVISOR_ORDER = 300;
  private static final int LOGGER_ADVISOR_ORDER = 1000;

  @Bean
  public ChatClient chatClient(
      ChatClient.Builder builder,
      ChatMemory chatMemory,
      GuardClassifierService guardClassifierService,
      StudentProfileService studentProfileService,
      StudentProfilePromptMapper studentProfilePromptMapper,
      SubjectConfigService subjectConfigService,
      ProfileProperties profileProperties,
      RetrieveInformationTool retrieveInformationTool,
      DocumentCatalogPromptService documentCatalogPromptService,
      PedagogicalRoutingService pedagogicalRoutingService,
      TutorPromptResources promptResources) {

    var chatMemoryAdvisor =
        MessageChatMemoryAdvisor.builder(chatMemory).order(CHAT_MEMORY_ADVISOR_ORDER).build();
    var profileAwareResponseAdvisor =
        new ProfileAwareResponseAdvisor(
            PROFILE_ADVISOR_ORDER,
            studentProfileService,
            profileProperties,
            studentProfilePromptMapper);
    var subjectContextAdvisor =
        new SubjectContextAdvisor(SUBJECT_CONTEXT_ADVISOR_ORDER, subjectConfigService);
    var pedagogicalRoutingAdvisor =
        new PedagogicalRoutingAdvisor(
            PEDAGOGICAL_ROUTING_ADVISOR_ORDER, pedagogicalRoutingService, promptResources);
    var documentCatalogAdvisor =
        new DocumentCatalogAdvisor(DOCUMENT_CATALOG_ADVISOR_ORDER, documentCatalogPromptService);
    var tutorGuardAdvisor =
        new TutorGuardAdvisor(TUTOR_GUARD_ADVISOR_ORDER, guardClassifierService, promptResources);
    var toolCallAdvisor =
        ToolCallAdvisor.builder()
            .advisorOrder(TOOL_CALL_ADVISOR_ORDER)
            .disableInternalConversationHistory()
            .build();
    var simpleLoggerAdvisor = new SimpleLoggerAdvisor(LOGGER_ADVISOR_ORDER);

    List<Advisor> advisors =
        new ArrayList<>(
            List.of(
                chatMemoryAdvisor,
                subjectContextAdvisor,
                profileAwareResponseAdvisor,
                documentCatalogAdvisor,
                pedagogicalRoutingAdvisor,
                tutorGuardAdvisor,
                toolCallAdvisor,
                simpleLoggerAdvisor));

    return builder
        .defaultSystem(promptResources.baseIdentitySystemResource())
        .defaultOptions(OllamaChatOptions.builder().disableThinking().build())
        .defaultAdvisors(advisors)
        .defaultTools(retrieveInformationTool)
        .build();
  }
}
