package com.wornux.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor assignmentStartExecutor() {
        return boundedExecutor(2, 32, "assignment-start-");
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor instructionReviewWorkerExecutor() {
        return boundedExecutor(2, 8, "instruction-review-worker-");
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor instructionReviewModelExecutor() {
        return boundedExecutor(2, 4, "instruction-review-model-");
    }

    private ThreadPoolExecutor boundedExecutor(int threads, int queueCapacity, String name) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name(name, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
