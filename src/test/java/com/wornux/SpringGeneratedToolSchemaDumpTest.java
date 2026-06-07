package com.wornux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wornux.ai.document.DocumentCatalogPromptService;
import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.profile.ProfileAwareResponseAdvisor;
import com.wornux.ai.routing.PedagogicalRoutingMode;
import com.wornux.ai.routing.PedagogicalRoutingService;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.data.enums.GuardDecision;
import com.wornux.domain.chat.questions.StudentQuestion;
import com.wornux.domain.chat.questions.StudentQuestionAnswer;
import com.wornux.domain.chat.questions.StudentQuestionResponse;
import com.wornux.domain.chat.questions.StudentQuestionSet;
import com.wornux.domain.profile.StudentProfileSnapshot;
import com.wornux.services.document.DocumentRetrievalService;
import com.wornux.services.profile.StudentProfileService;
import com.wornux.services.subject.SubjectConfig;
import com.wornux.services.subject.SubjectConfigService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("integration")
@SpringBootTest(
    classes = AiConfigToolTestSupport.class,
    properties = {
      "spring.ai.ollama.chat.model=${CHAT_MODEL:qwen3:4b-instruct}",
      "spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}",
      "spring.ai.tools.throw-exception-on-error=false",
      "test.ollama.transcript-name=spring-generated-tool-schema-dump"
    })
class SpringGeneratedToolSchemaDumpTest {

  private static final Logger log =
      LoggerFactory.getLogger(SpringGeneratedToolSchemaDumpTest.class);

  @Autowired ChatClient chatClient;

  @MockitoBean DocumentRetrievalService documentRetrievalService;
  @MockitoBean GuardClassifierService guardClassifierService;
  @MockitoBean PedagogicalRoutingService pedagogicalRoutingService;
  @MockitoBean SubjectConfigService subjectConfigService;
  @MockitoBean StudentProfileService studentProfileService;
  @MockitoBean DocumentCatalogPromptService documentCatalogPromptService;

  @Test
  @Timeout(90)
  void dumpsSpringGeneratedToolSchemaSentToOllama() {
    when(guardClassifierService.classify(any())).thenReturn(GuardDecision.SAFE);
    when(pedagogicalRoutingService.classify(any())).thenReturn(PedagogicalRoutingMode.EXERCISE_GUIDANCE);
    when(subjectConfigService.defaultSubjectSlug()).thenReturn("c-programming");
    when(subjectConfigService.current(any())).thenReturn(subjectConfig());
    when(studentProfileService.load(any())).thenReturn(StudentProfileSnapshot.anonymous());
    when(documentCatalogPromptService.buildInventoryPrompt(any())).thenReturn("");

    var clientId = UUID.randomUUID();
    var conversationId = UUID.randomUUID();
    var response =
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
                    UUID.randomUUID(),
                    ToolUsageAuditService.PROFILE_VERSION,
                    0L))
            .tools(new AskStudentQuestionTool(SpringGeneratedToolSchemaDumpTest::answerFirstOption))
            .user("necesito ayuda para resolver un ejercicio de cajero")
            .call()
            .content();

    log.info("Spring-generated schema dump response: {}", response);
  }

  private static StudentQuestionResponse answerFirstOption(StudentQuestionSet questionSet) {
    var firstQuestion = questionSet.questions().stream().findFirst();
    var answer = firstQuestion.map(SpringGeneratedToolSchemaDumpTest::answerFirstOption);
    return new StudentQuestionResponse(answer.stream().toList());
  }

  private static StudentQuestionAnswer answerFirstOption(StudentQuestion question) {
    var selectedLabel = question.options().stream().findFirst().map(option -> option.label()).orElse("");
    return new StudentQuestionAnswer("q0", List.of(selectedLabel), "schema dump");
  }

  private SubjectConfig subjectConfig() {
    return new SubjectConfig(
        UUID.randomUUID(),
        "c-programming",
        "C Programming",
        1L,
        UUID.randomUUID(),
        Map.of("scope", "C pointers, memory, and control flow"),
        Map.of("defaultHelpMode", "guided"),
        Map.of("askBeforeAnswering", true));
  }
}
