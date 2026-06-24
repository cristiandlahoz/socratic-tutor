package com.wornux.ai.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class JdbcSessionRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void officialSchemaPersistsAndArchivesSessionEvents() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        var sessionService = DefaultSessionService.builder()
                .sessionRepository(JdbcSessionRepository.builder().dataSource(dataSource).build())
                .build();
        var conversationId = UUID.randomUUID().toString();
        sessionService
                .create(CreateSessionRequest.builder().id(conversationId).userId(UUID.randomUUID().toString()).build());
        sessionService.appendMessage(conversationId, new UserMessage("first question"));
        sessionService.appendMessage(conversationId, new AssistantMessage("first answer"));
        sessionService.appendMessage(conversationId, new UserMessage("second question"));
        sessionService.appendMessage(conversationId, new AssistantMessage("second answer"));

        var result = sessionService
                .compact(conversationId, _ -> true, SlidingWindowCompactionStrategy.builder().maxEvents(2).build());

        assertThat(result.archivedEvents()).hasSize(2);
        assertThat(sessionService.getEvents(conversationId, EventFilter.active())).hasSize(2);
        assertThat(sessionService.getEvents(conversationId, EventFilter.all())).hasSize(4);
    }
}
