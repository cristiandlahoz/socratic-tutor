package com.wornux.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class ReactorContextPropagationConfig {

    @PostConstruct
    void enableAutomaticContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
    }
}
