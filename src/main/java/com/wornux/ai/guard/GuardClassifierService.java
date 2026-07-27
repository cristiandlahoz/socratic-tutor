package com.wornux.ai.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.prompt.PromptUtil;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * @author @github/cristiandlahoz
 */
@Service
@Slf4j
public class GuardClassifierService {

    public static final String TECHNICAL_FAILURE_RESPONSE =
            "No pude verificar este mensaje de forma segura. Inténtalo de nuevo en un momento.";

    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final String SUBJECT_CONTEXT_QUERY =
            """
            select s.code, s.name, coalesce(s.syllabus, '') as syllabus
            from group_class gc
            join subject s on s.id = gc.subject_id
            where gc.id = :groupClassId
            """;

    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final JdbcClient jdbcClient;
    private final BeanOutputConverter<GuardCheck> outputConverter = new BeanOutputConverter<>(GuardCheck.class);

    private final String guardModel;

    public GuardClassifierService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            PromptResources promptResources,
            JdbcClient jdbcClient,
            ApplicationProperties.Ai.SwitzerlandKnife switzerlandKnifeProperties) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.jdbcClient = jdbcClient;
        this.guardModel = switzerlandKnifeProperties.getModel();
    }

    public GuardCheck classify(List<? extends Message> messages) {
        return classify(messages, "");
    }

    public GuardCheck classify(List<? extends Message> messages, String subjectContext) {
        Assert.notEmpty(messages, "messages cannot be empty");

        Prompt prompt = Prompt.builder()
                .messages(classifierMessages(messages, subjectContext))
                .chatOptions(options(outputConverter))
                .build();

        GuardCheck guardCheck = outputConverter.convert(callGuardModel(prompt, "classifier"));

        if (guardCheck == null) {
            throw new IllegalStateException("Guard classifier returned an empty result");
        }

        log.info("Guard decision: {}, action: {}", guardCheck.decision(), guardCheck.action());
        return guardCheck;
    }

    public GuardCheck classifyInteractiveResponse(
            String approvedUserMessage,
            StudentQuestionSet questionSet,
            StudentQuestionResponse response,
            String subjectContext) {
        return classify(
            List.of(
                new UserMessage(approvedUserMessage),
                new AssistantMessage(interactiveQuestions(questionSet)),
                new UserMessage(interactiveAnswers(response))),
            subjectContext);
    }

    public Optional<String> subjectContextFor(UUID groupClassId) {
        return jdbcClient.sql(SUBJECT_CONTEXT_QUERY)
                .param("groupClassId", groupClassId)
                .query((rs, _) -> """
                         <active_subject_context>
                         Subject: %s · %s
                         %s
                         </active_subject_context>"""
                        .formatted(rs.getString("code"), rs.getString("name"), rs.getString("syllabus")))
                .optional();
    }

    public static GuardCheck technicalFailure() {
        return new GuardCheck(GuardDecision.NOT_SAFE, GuardAction.SHORT_CIRCUIT, "", TECHNICAL_FAILURE_RESPONSE);
    }

    private OpenAiChatOptions options(BeanOutputConverter<?> converter) {
        return OpenAiChatOptions.builder()
                .model(guardModel)
                .temperature(0.0)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .outputSchema(converter.getJsonSchema())
                .build();
    }

    private String callGuardModel(Prompt prompt, String operation) {
        var generation = chatModel.call(prompt).getResult();
        if (generation == null) {
            throw new IllegalStateException("Guard %s returned no generation".formatted(operation));
        }
        String responseText = generation.getOutput().getText();
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("Guard %s returned an empty response".formatted(operation));
        }
        return responseText;
    }

    private List<Message> classifierMessages(List<? extends Message> conversation, String subjectContext) {
        List<Message> messages = new ArrayList<>(conversation.size() + 1);
        messages.add(new SystemMessage(render(promptResources.guardClassifier(), subjectContext)));
        messages.addAll(conversation);
        return messages;
    }

    private String interactiveQuestions(StudentQuestionSet questionSet) {
        var text = new StringBuilder("<interactive_questions_shown_to_student>\n");
        for (int index = 0; index < questionSet.questions().size(); index++) {
            var question = questionSet.questions().get(index);
            text.append("q").append(index).append(": ").append(question.question()).append('\n');
            question.options().forEach(option -> text.append("- option: ").append(option.label()).append('\n'));
        }
        return text.append("</interactive_questions_shown_to_student>").toString();
    }

    private String interactiveAnswers(StudentQuestionResponse response) {
        var text = new StringBuilder("<latest_interactive_student_response>\n");
        response.answers().forEach(answer -> text.append(answer.questionId())
                .append(" selected=")
                .append(answer.selectedOptionLabels())
                .append(" customText=")
                .append(answer.customText())
                .append('\n'));
        return text.append("</latest_interactive_student_response>").toString();
    }

    private String render(String template, String subjectContext) {
        return PromptUtil.render(template, Map.of("subjectContext", subjectContext == null ? "" : subjectContext));
    }
}
