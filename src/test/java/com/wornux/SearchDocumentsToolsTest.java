package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.ai.document.DocumentCatalogPromptService;
import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.profile.ProfileAwareResponseAdvisor;
import com.wornux.ai.routing.PedagogicalRoutingMode;
import com.wornux.ai.routing.PedagogicalRoutingService;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.dtos.document.DocumentSearchHit;
import com.wornux.dtos.profile.StudentProfileSnapshot;
import com.wornux.services.document.DocumentRetrievalService;
import com.wornux.services.profile.StudentProfileService;
import com.wornux.services.subject.SubjectConfig;
import com.wornux.services.subject.SubjectConfigService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
      "test.ollama.transcript-name=search-documents-tools-test"
    })
class SearchDocumentsToolsTest {

  private static final Logger log = LoggerFactory.getLogger(SearchDocumentsToolsTest.class);
  private static final String TOOL_SENTINEL = "THE_TOOL_SENTINEL_42";

  @Autowired ChatClient chatClient;

  @Autowired ToolUsageAuditService toolUsageAuditService;

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
    when(documentCatalogPromptService.buildInventoryPrompt(any()))
        .thenReturn(
            """
            Indexed uploaded documents available for this student:
            Use searchUploadedDocuments when the user asks about the integration test sentinel.
            - integration-tools.pdf | Integration Tools Fixture | topic: testing | tags: integration, tools | can answer: integration test sentinel
            """);
  }

  @Test
  @Timeout(90)
  void chatClientUsesUploadedDocumentSearchTool() {
    var clientId = UUID.randomUUID();
    var conversationId = UUID.randomUUID();
    var turnId = UUID.randomUUID();
    when(documentRetrievalService.search(eq(clientId), any(), any(), any(), any(), any()))
        .thenReturn(new DocumentContextResult(List.of(searchHit()), true));

    log.info(
        "Search document tool context clientId={} conversationId={} turnId={}",
        clientId,
        conversationId,
        turnId);

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
                    turnId,
                    ToolUsageAuditService.PROFILE_VERSION,
                    7L))
            .user(
                """
                You must call the searchUploadedDocuments tool before answering.
                Search uploaded documents for the exact fact named integration test sentinel.
                After the tool returns, answer with only the sentinel value from the retrieved excerpt.
                Do not answer from memory and do not add any extra words.
                """)
            .call()
            .content();

    log.info("Search document final model content:\n{}", response);

    assertThat(response).isNotBlank().contains(TOOL_SENTINEL);
    verify(documentRetrievalService).search(eq(clientId), any(), any(), any(), any(), any());
    assertThat(toolUsageAuditService.drainTurnAudits(turnId))
        .singleElement()
        .satisfies(
            audit -> {
              assertThat(audit.toolName()).isEqualTo("searchUploadedDocuments");
              assertThat(audit.status()).isEqualTo("success");
              assertThat(audit.clientId()).isEqualTo(clientId);
              assertThat(audit.conversationId()).isEqualTo(conversationId);
              assertThat(audit.turnId()).isEqualTo(turnId);
              assertThat(audit.modelRequested()).isTrue();
              assertThat(audit.profileSnapshotVersion()).isEqualTo(7L);
              assertThat(audit.outputSummary()).contains("hits=1", "context_found=true");
            });
  }

  private DocumentSearchHit searchHit() {
    return new DocumentSearchHit(
        "segment-1",
        UUID.randomUUID(),
        "integration-tools.pdf",
        "Integration Tools Fixture",
        "testing",
        List.of("integration", "tools"),
        "Tool Calling",
        "The exact integration test sentinel value is %s.".formatted(TOOL_SENTINEL),
        0.98,
        1,
        3,
        List.of(3),
        List.of());
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
