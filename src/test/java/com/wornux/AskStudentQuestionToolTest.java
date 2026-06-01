package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * @author github/cristiandlahoz
 */
@Tag("integration")
@SpringBootTest(
    classes = AiConfigToolTestSupport.class,
    properties = {
      "spring.ai.ollama.chat.model=${CHAT_MODEL:qwen3:4b-instruct}",
      "spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}",
      "spring.ai.tools.throw-exception-on-error=true",
      "test.ollama.transcript-name=ask-student-question-tool-test"
    })
class AskStudentQuestionToolTest {

  private static final Logger log = LoggerFactory.getLogger(AskStudentQuestionToolTest.class);

  @Autowired ChatClient chatClient;

  @MockitoBean DocumentRetrievalService documentRetrievalService;
  @MockitoBean GuardClassifierService guardClassifierService;
  @MockitoBean PedagogicalRoutingService pedagogicalRoutingService;
  @MockitoBean SubjectConfigService subjectConfigService;
  @MockitoBean StudentProfileService studentProfileService;
  @MockitoBean DocumentCatalogPromptService documentCatalogPromptService;

  @BeforeEach
  void setUpAiConfigCollaborators() {
    when(guardClassifierService.classify(any())).thenReturn(GuardDecision.SAFE);
    when(pedagogicalRoutingService.classify(any())).thenReturn(PedagogicalRoutingMode.CONCEPT_EXPLANATION);
    when(subjectConfigService.defaultSubjectSlug()).thenReturn("c-programming");
    when(subjectConfigService.current(any())).thenReturn(subjectConfig());
    when(studentProfileService.load(any())).thenReturn(StudentProfileSnapshot.anonymous());
    when(documentCatalogPromptService.buildInventoryPrompt(any())).thenReturn("");
  }

  @Test
  @Timeout(90)
  void chatClientConvertsModelToolCallToStudentQuestionSet() {
    var capturedQuestionSet = new AtomicReference<StudentQuestionSet>();
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
            .tools(new AskStudentQuestionTool(questionHandler(capturedQuestionSet)))
            .user(
                """
                Call the askStudentQuestion tool exactly once before answering.
                Create a question set to evaluate whether a student understands pointer basics in C.
                The question set must use profileImpact PEDAGOGICAL, include exactly one question,
                and that question must include exactly two options with non-empty labels and descriptions.
                After the tool returns, briefly acknowledge the student's selected answer.
                """)
            .call()
            .content();

    log.info("Converted StudentQuestionSet:\n{}", capturedQuestionSet.get());
    log.info("Ask student question final model content:\n{}", response);

    assertThat(response).isNotBlank();
    assertQuestionSetSchema(capturedQuestionSet.get());
  }

  private AskStudentQuestionTool.QuestionHandler questionHandler(
      AtomicReference<StudentQuestionSet> capturedQuestionSet) {
    return questionSet -> {
      capturedQuestionSet.set(questionSet);
      var question = questionSet.questions().get(0);
      var selectedLabel = question.options().get(0).label();
      return new StudentQuestionResponse(
          List.of(new StudentQuestionAnswer(question.id(), List.of(selectedLabel), "schema accepted")));
    };
  }

  private void assertQuestionSetSchema(StudentQuestionSet questionSet) {
    assertThat(questionSet).isNotNull();
    assertThat(questionSet.title()).isNotBlank();
    assertThat(questionSet.purpose()).isNotBlank();
    assertThat(questionSet.profileImpact()).isEqualTo(StudentQuestionSet.ProfileImpact.PEDAGOGICAL);
    assertThat(questionSet.questions()).hasSizeBetween(1, 3).allSatisfy(this::assertQuestionSchema);
  }

  private void assertQuestionSchema(StudentQuestion question) {
    assertThat(question.id()).isNotBlank();
    assertThat(question.header()).isNotBlank().hasSizeLessThanOrEqualTo(24);
    assertThat(question.question()).isNotBlank().endsWith("?");
    assertThat(question.options()).hasSizeBetween(2, 4);
    assertThat(question.options().stream().map(option -> option.label()).toList()).doesNotHaveDuplicates();
    assertThat(question.options())
        .allSatisfy(
            option -> {
              assertThat(option.label()).isNotBlank();
              assertThat(option.description()).isNotBlank();
            });
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
