package com.wornux.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationPropertiesConfiguration {

    @Bean
    ApplicationProperties.Security applicationSecurityProperties(ApplicationProperties properties) {
        return properties.getSecurity();
    }

    @Bean
    ApplicationProperties.Ai.Conversation conversationProperties(ApplicationProperties properties) {
        return properties.getAi().getConversation();
    }

    @Bean
    ApplicationProperties.Ai.SwitzerlandKnife switzerlandKnifeProperties(ApplicationProperties properties) {
        return properties.getAi().getSwitzerlandKnife();
    }

    @Bean
    ApplicationProperties.Ai.ModelAvailability modelAvailabilityProperties(ApplicationProperties properties) {
        return properties.getAi().getModelAvailability();
    }

    @Bean
    ApplicationProperties.Ai.ToolAudit toolAuditProperties(ApplicationProperties properties) {
        return properties.getAi().getToolAudit();
    }

    @Bean
    ApplicationProperties.DocumentIngest documentIngestProperties(ApplicationProperties properties) {
        return properties.getDocumentIngest();
    }

    @Bean
    ApplicationProperties.CRunner cRunnerProperties(ApplicationProperties properties) {
        return properties.getCRunner();
    }

    @Bean
    ApplicationProperties.Email emailProperties(ApplicationProperties properties) {
        return properties.getEmail();
    }
}
