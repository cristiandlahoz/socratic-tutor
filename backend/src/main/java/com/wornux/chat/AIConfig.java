package com.wornux.chat;

import com.wornux.chat.advisor.TutorGuardAdvisor;
import com.wornux.chat.prompt.TutorPromptResources;
import com.wornux.chat.profile.ProfileAwareResponseAdvisor;
import com.wornux.chat.profile.ProfileProperties;
import com.wornux.chat.profile.StudentProfileService;
import com.wornux.chat.routing.PedagogicalRoutingAdvisor;
import com.wornux.chat.routing.PedagogicalRoutingService;
import com.wornux.chat.tools.AskStudentQuestionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
public class AIConfig {

    private static final int CHAT_MEMORY_ADVISOR_ORDER = 100;
    private static final int PROFILE_ADVISOR_ORDER = 150;
    private static final int PEDAGOGICAL_ROUTING_ADVISOR_ORDER = 175;
    private static final int TUTOR_GUARD_ADVISOR_ORDER = 200;
    private static final int LOGGER_ADVISOR_ORDER = 1000;

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            GuardClassifierService guardClassifierService,
            StudentProfileService studentProfileService,
            ProfileProperties profileProperties,
            AskStudentQuestionTool askStudentQuestionTool,
            PedagogicalRoutingService pedagogicalRoutingService,
            TutorPromptResources promptResources,
            TutorAiProperties tutorAiProperties) {

        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).order(CHAT_MEMORY_ADVISOR_ORDER).build();
        var profileAwareResponseAdvisor = new ProfileAwareResponseAdvisor(PROFILE_ADVISOR_ORDER, studentProfileService, profileProperties);
        var pedagogicalRoutingAdvisor = new PedagogicalRoutingAdvisor(PEDAGOGICAL_ROUTING_ADVISOR_ORDER, pedagogicalRoutingService, promptResources);
        var tutorGuardAdvisor = new TutorGuardAdvisor(TUTOR_GUARD_ADVISOR_ORDER, guardClassifierService, promptResources);

        List<Advisor> advisors = new ArrayList<>(List.of(
                chatMemoryAdvisor,
                profileAwareResponseAdvisor,
                pedagogicalRoutingAdvisor,
                tutorGuardAdvisor
        ));
        if (tutorAiProperties.isPromptLoggingEnabled()) {
            advisors.add(promptLoggerAdvisor());
        }

        return builder.defaultSystem(promptResources.baseIdentitySystemResource())
                .defaultAdvisors(advisors)
                .defaultTools(askStudentQuestionTool)
                .build();
    }

    private SimpleLoggerAdvisor promptLoggerAdvisor() {
        return SimpleLoggerAdvisor.builder()
                .order(LOGGER_ADVISOR_ORDER)
                .requestToString(AIConfig::summarizeRequest)
                .responseToString(AIConfig::summarizeResponse)
                .build();
    }

    private static String summarizeRequest(org.springframework.ai.chat.client.ChatClientRequest request) {
        if (request == null) {
            return "request=null";
        }

        var messages = request.prompt().getInstructions();
        String messageTypes = messages.stream()
                .collect(Collectors.groupingBy(message -> message.getMessageType().name(),
                        Collectors.counting()))
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .collect(Collectors.joining(","));

        return "messages=%d messageTypes=[%s] contextKeys=%s".formatted(
                messages.size(),
                messageTypes,
                request.context().keySet()
        );
    }

    private static String summarizeResponse(ChatResponse response) {
        if (response == null) {
            return "response=null";
        }

        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        return "generations=%d promptTokens=%s completionTokens=%s".formatted(
                response.getResults() == null ? 0 : response.getResults().size(),
                Objects.toString(promptTokens, "unknown"),
                Objects.toString(completionTokens, "unknown")
        );
    }
}
