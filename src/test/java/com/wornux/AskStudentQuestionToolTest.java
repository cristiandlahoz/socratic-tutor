package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.ai.tools.AskStudentQuestionTool;
import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.dtos.chat.questions.StudentQuestion;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.services.document.DocumentRetrievalService;
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
@SpringBootTest(classes = AiConfigToolTestSupport.class,
        properties = {
                "spring.ai.ollama.chat.model=${CHAT_MODEL:qwen3:4b-instruct}",
                "spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}",
                "spring.ai.tools.throw-exception-on-error=true",
                "test.ollama.transcript-name=ask-student-question-tool-test" })
class AskStudentQuestionToolTest {

    private static final Logger log = LoggerFactory.getLogger(AskStudentQuestionToolTest.class);

    @Autowired
    ChatClient chatClient;

    @MockitoBean
    DocumentRetrievalService documentRetrievalService;

    @Test
    @Timeout(90)
    void chatClientConvertsModelToolCallToStudentQuestionSet() {
        var capturedQuestionSet = new AtomicReference<StudentQuestionSet>();
        var groupClassMemberId = UUID.randomUUID();
        var conversationId = UUID.randomUUID();
        var response = chatClient.prompt()
                .advisors(
                    advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId.toString())
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
        assertThat(question.options()).hasSizeBetween(1, 4);
        assertThat(question.options()).allSatisfy(option -> {
            assertThat(option.label()).isNotBlank();
            assertThat(option.description()).isNotBlank();
        });
    }

}
