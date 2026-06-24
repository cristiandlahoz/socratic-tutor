package com.wornux;

import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.tools.RetrieveInformationTool;
import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.config.AIConfig;
import com.wornux.config.TutorAiProperties;
import com.wornux.services.document.DocumentRetrievalService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
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
@EnableConfigurationProperties(TutorAiProperties.class)
@Import({ AIConfig.class })
@ImportAutoConfiguration({
        OllamaApiAutoConfiguration.class,
        OllamaChatAutoConfiguration.class,
        ToolCallingAutoConfiguration.class,
        ChatClientAutoConfiguration.class,
        ChatMemoryAutoConfiguration.class })
class AiConfigToolTestSupport {

    @Bean
    TutorPromptResources tutorPromptResources(ResourceLoader resourceLoader) {
        return new TutorPromptResources(resourceLoader);
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
        tutorAiProperties.getToolObservability().setCapturePayloads(true);
        tutorAiProperties.getToolObservability().setMaxPayloadChars(1_000);
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
    RestClient.Builder restClientBuilder(
            @Value("${test.ollama.transcript-name:ollama-tool-test}") String transcriptName) {
        return OllamaHttpLogging.restClientBuilder(transcriptName);
    }
}
