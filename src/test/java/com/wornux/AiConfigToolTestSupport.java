package com.wornux;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.config.AIConfig;
import com.wornux.config.ApplicationProperties;
import com.wornux.config.ApplicationPropertiesConfiguration;
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
            ObjectMapper objectMapper) {
        return new ToolUsageAuditService(meterRegistry, observationRegistry, objectMapper);
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
    RestClient.Builder restClientBuilder(
            @Value("${test.openai.transcript-name:openai-tool-test}") String transcriptName) {
        return OllamaHttpLogging.restClientBuilder(transcriptName);
    }
}
