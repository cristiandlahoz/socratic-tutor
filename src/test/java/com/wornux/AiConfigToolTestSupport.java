package com.wornux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.guard.GuardClassifierService;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.config.AIConfig;
import com.wornux.config.ApplicationProperties;
import com.wornux.config.ApplicationPropertiesConfiguration;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
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
@EnableConfigurationProperties({ ApplicationProperties.class })
@Import({ AIConfig.class, ApplicationPropertiesConfiguration.class })
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
            ApplicationProperties.Ai.ToolAudit toolAuditProperties) {
        return new ToolUsageAuditService(meterRegistry, observationRegistry, objectMapper, toolAuditProperties);
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
        var safe = new GuardCheck(GuardDecision.SAFE, GuardAction.ALLOW, "", "");
        when(guardClassifierService.classify(anyList())).thenReturn(safe);
        when(guardClassifierService.classify(anyList(), anyString())).thenReturn(safe);
        when(guardClassifierService.subjectContextFor(any())).thenReturn(java.util.Optional.empty());
        return guardClassifierService;
    }

    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${test.openai.transcript-name:openai-tool-test}") String transcriptName) {
        return OllamaHttpLogging.restClientBuilder(transcriptName);
    }
}
