package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.questions.StudentQuestion;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.services.chat.ChatSessionActivityBus;
import com.wornux.services.document.DocumentRetrievalService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @author github/cristiandlahoz
 */
@Tag("integration")
@SpringBootTest(classes = AiConfigToolTestSupport.class,
        properties = {
                "spring.ai.model.chat=openai",
                "spring.ai.openai.api-key=${OPENAI_API_KEY:dummy}",
                "spring.ai.openai.base-url=${OPENAI_BASE_URL:http://127.0.0.1:8080/v1}",
                "spring.ai.openai.chat.model=${CHAT_MODEL:AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL}",
                "spring.ai.tools.throw-exception-on-error=true",
                "app.ai.conversation.config.context-window-tokens=8192",
                "app.ai.conversation.config.compaction-threshold-ratio=0.30",
                "test.openai.transcript-name=ask-student-question-tool-test" })
class AskStudentQuestionToolTest {

    private static final Logger log = LoggerFactory.getLogger(AskStudentQuestionToolTest.class);

    @Autowired
    ChatClient chatClient;

    @MockitoBean
    DocumentRetrievalService documentRetrievalService;

    @MockitoBean
    GuardClassifierService guardClassifierService;

    @MockitoBean
    ChatSessionActivityBus chatSessionActivityBus;

    @MockitoBean
    JdbcClient jdbcClient;

    @Test
    @Timeout(180)
    void chatClientConvertsModelToolCallToStudentQuestionSet() {
        when(guardClassifierService.classify(anyList())).thenReturn(GuardDecision.SAFE);
        var capturedQuestionSet = new AtomicReference<StudentQuestionSet>();
        var groupClassMemberId = UUID.randomUUID();
        var conversationId = UUID.randomUUID();
        var response = chatClient.prompt()
                .advisors(
                    advisorSpec -> advisorSpec
                            .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, conversationId.toString())
                            .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, groupClassMemberId.toString())
                            .param(ToolContextKeys.GROUP_CLASS_MEMBER_ID, groupClassMemberId))
                .tools(new AskStudentQuestionTool(questionHandler(capturedQuestionSet)))
                .user("""
                      necesito ayuda para resolver un ejercicio de cajero
                      """)
                .call()
                .content();

        log.info("Converted StudentQuestionSet:\n{}", capturedQuestionSet.get());
        log.info("Ask student question final model content:\n{}", response);

        assertQuestionSetSchema(capturedQuestionSet.get());
        assertThat(response).isNotNull();
    }

    private AskStudentQuestionTool.QuestionHandler questionHandler(
            AtomicReference<StudentQuestionSet> capturedQuestionSet) {
        return questionSet -> {
            capturedQuestionSet.set(questionSet);
            var question = questionSet.questions().get(0);
            var selectedLabel = question.options().get(0).label();
            return new StudentQuestionResponse(
                    List.of(new StudentQuestionAnswer("q0", List.of(selectedLabel), "schema accepted")));
        };
    }

    private void assertQuestionSetSchema(StudentQuestionSet questionSet) {
        assertThat(questionSet).isNotNull();
        assertThat(questionSet.questions()).hasSizeBetween(1, 3).allSatisfy(this::assertQuestionSchema);
    }

    private void assertQuestionSchema(StudentQuestion question) {
        assertThat(question.question()).isNotBlank().endsWith("?");
        assertThat(question.options()).isNotEmpty();
        assertThat(question.options()).allSatisfy(option -> {
            assertThat(option.label()).isNotBlank();
            assertThat(option.description()).isNotBlank();
        });
    }

}
