package com.wornux.ai.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.wornux.services.subject.SubjectConfig;
import com.wornux.services.subject.SubjectConfigService;
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

public class SubjectContextAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String SUBJECT_SLUG_CONTEXT_KEY = "subject_slug";

    private final int order;
    private final SubjectConfigService subjectConfigService;

    public SubjectContextAdvisor(int order, SubjectConfigService subjectConfigService) {
        this.order = order;
        this.subjectConfigService = subjectConfigService;
    }

    @Override
    public @NullMarked ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(applySubjectContext(request));
    }

    @Override
    public @NullMarked Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(applySubjectContext(request));
    }

    private ChatClientRequest applySubjectContext(ChatClientRequest request) {
        var subject = subjectConfigService.current(subjectSlugFrom(request));
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
        messages.add(new SystemMessage(toPrompt(subject)));

        var promptBuilder = Prompt.builder().messages(messages);
        var options = request.prompt().getOptions();
        if (!Objects.isNull(options)) {
            promptBuilder.chatOptions(options);
        }
        return request.mutate().prompt(promptBuilder.build()).build();
    }

    private String subjectSlugFrom(ChatClientRequest request) {
        Object raw = request.context().get(SUBJECT_SLUG_CONTEXT_KEY);
        if (raw instanceof String slug && !slug.isBlank()) {
            return slug;
        }
        return subjectConfigService.defaultSubjectSlug();
    }

    private String toPrompt(SubjectConfig subject) {
        return """
               Subject context:
               - Subject: %s
               - Config version: %d
               - Scope and policy: %s
               - Rubric defaults: %s

               Use this subject context as the source of course-specific facts. Do not invent course policy.
               """.formatted(subject.displayName(), subject.version(), subject.config(), subject.rubricDefaults());
    }

    @Override
    public @NullUnmarked String getName() {
        return "subject-context-advisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
