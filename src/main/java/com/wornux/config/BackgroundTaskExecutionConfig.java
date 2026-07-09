package com.wornux.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides bounded execution for blocking background jobs.
 *
 * @author cristiandlahoz
 */
@Configuration
public class BackgroundTaskExecutionConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService cRunnerExecutor() {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform().name("c-runner-", 0).factory());
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService documentIngestionExecutor() {
        return Executors.newFixedThreadPool(2, Thread.ofPlatform().name("document-ingest-", 0).factory());
    }
}
