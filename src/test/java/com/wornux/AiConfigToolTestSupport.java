package com.wornux;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.config.AIConfig;
import com.wornux.config.ChatProperties;
import com.wornux.config.TutorAiProperties;
import com.wornux.data.enums.GuardDecision;
import com.wornux.services.chat.ChatSessionActivityBus;
import com.wornux.services.document.DocumentRetrievalService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableConfigurationProperties({ TutorAiProperties.class, ChatProperties.class })
@Import({ AIConfig.class })
@ImportAutoConfiguration({
        OpenAiChatAutoConfiguration.class,
        ToolCallingAutoConfiguration.class,
        ChatClientAutoConfiguration.class })
class AiConfigToolTestSupport {

    @Bean
    SessionService sessionService() {
        return DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
    }

    @Bean
    PromptResources promptResources(ResourceLoader resourceLoader) {
        return new PromptResources(resourceLoader);
    }

    @Bean
    RetrieveInformationTool retrieveInformationTool(
            DocumentRetrievalService documentRetrievalService,
            ToolUsageAuditService toolUsageAuditService) {
        return new RetrieveInformationTool(documentRetrievalService, toolUsageAuditService);
    }

    @Bean
    ToolUsageAuditService toolUsageAuditService(
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry,
            ObjectMapper objectMapper,
            TutorAiProperties tutorAiProperties) {
        tutorAiProperties.getToolObservability().setCaptureToolReturns(true);
        tutorAiProperties.getToolObservability().setMaxToolReturnChars(1_000);
        return new ToolUsageAuditService(meterRegistry, observationRegistry, objectMapper, tutorAiProperties);
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    ChatSessionActivityBus chatSessionActivityBus() {
        return new ChatSessionActivityBus();
    }

    @Bean
    JdbcClient jdbcClient() {
        return mock(JdbcClient.class);
    }

    @Bean
    GuardClassifierService guardClassifierService() {
        var guardClassifierService = mock(GuardClassifierService.class);
        when(guardClassifierService.classify(anyList())).thenReturn(GuardDecision.SAFE);
        return guardClassifierService;
    }

    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${test.openai.transcript-name:openai-tool-test}") String transcriptName) {
        return OllamaHttpLogging.restClientBuilder(transcriptName);
    }
}
