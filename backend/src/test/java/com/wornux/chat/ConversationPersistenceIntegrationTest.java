package com.wornux.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.flywaydb.core.Flyway;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(ConversationPersistenceIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ConversationPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatJpaRepository chatRepository;

    @Autowired
    private ChatTranscriptJpaRepository chatTranscriptRepository;

    @Autowired
    private ChatUsageService chatUsageService;

    @Test
    void flyway_creates_chat_tables() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('chat', 'chat_transcript', 'chat_message')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void createConversation_creates_initial_transcript_and_sets_it_as_current() {
        var clientId = UUID.randomUUID();

        var conversation = conversationService.createConversation(clientId, "Como funciona un ciclo for?");

        var chat = chatRepository.findById(conversation.id()).orElseThrow();
        var transcripts = chatTranscriptRepository.findByChat_IdOrderByCreatedAtAsc(chat.getId());

        assertThat(transcripts).hasSize(1);
        assertThat(chat.getCurrentTranscript()).isNotNull();
        assertThat(chat.getCurrentTranscript().getId()).isEqualTo(transcripts.getFirst().getId());
    }

    @Test
    void chatMemory_appends_messages_to_active_transcript_and_reads_them_in_order() {
        var clientId = UUID.randomUUID();
        var conversation = conversationService.createConversation(clientId, "Como funciona un ciclo for?");

        chatMemory.add(conversation.id().toString(), List.of(
                new UserMessage("primer mensaje"),
                AssistantMessage.builder().content("primera respuesta").build(),
                new UserMessage("segundo mensaje"),
                AssistantMessage.builder().content("segunda respuesta").build()
        ));

        var transcript = conversationService.loadConversation(clientId, conversation.id());
        var memoryWindow = chatMemory.get(conversation.id().toString());

        assertThat(transcript)
                .extracting(StoredChatMessage::content)
                .containsExactly("primer mensaje", "primera respuesta", "segundo mensaje", "segunda respuesta");
        assertThat(memoryWindow)
                .extracting(Message::getText)
                .containsExactly("primer mensaje", "primera respuesta", "segundo mensaje", "segunda respuesta");
    }

    @Test
    void loadConversation_reads_full_history_but_memory_reads_only_active_transcript_and_memory_text() {
        var clientId = UUID.randomUUID();
        var conversation = conversationService.createConversation(clientId, "Necesito ayuda con ciclos");
        chatMemory.add(conversation.id().toString(), List.of(
                new UserMessage("mensaje anterior"),
                AssistantMessage.builder().content("respuesta anterior").build()
        ));

        var chat = chatRepository.findById(conversation.id()).orElseThrow();
        var compactedTranscript = ChatTranscriptEntity.create(chat);
        compactedTranscript.setMemoryText("Resumen: el estudiante viene trabajando ciclos y necesita una transicion suave.");
        compactedTranscript = chatTranscriptRepository.save(compactedTranscript);
        chat.activateTranscript(compactedTranscript);
        chatRepository.save(chat);

        chatMemory.add(conversation.id().toString(), List.of(
                new UserMessage("mensaje actual"),
                AssistantMessage.builder().content("respuesta actual").build()
        ));

        var transcript = conversationService.loadConversation(clientId, conversation.id());
        var memoryWindow = chatMemory.get(conversation.id().toString());

        assertThat(transcript)
                .extracting(StoredChatMessage::content)
                .containsExactly("mensaje anterior", "respuesta anterior", "mensaje actual", "respuesta actual");
        assertThat(memoryWindow)
                .extracting(Message::getText)
                .containsExactly(
                        "Resumen: el estudiante viene trabajando ciclos y necesita una transicion suave.",
                        "mensaje actual",
                        "respuesta actual"
                );
    }

    @Test
    void chatUsageService_returns_input_tokens_and_percent_for_active_transcript() {
        var clientId = UUID.randomUUID();
        var conversation = conversationService.createConversation(clientId, "Necesito ayuda con ciclos");

        chatUsageService.updateActiveTranscriptInputTokens(conversation.id(), 18_432);

        var usage = chatUsageService.getActiveTranscriptUsage(clientId, conversation.id());

        assertThat(usage.inputTokens()).isEqualTo(18_432);
        assertThat(usage.usagePercent()).isEqualTo(50);
    }

    @Test
    void resolveActiveConversation_returns_latest_conversation_when_query_is_missing() {
        var clientId = UUID.randomUUID();
        var olderConversation = conversationService.createConversation(clientId, "Pregunta anterior");
        chatMemory.add(olderConversation.id().toString(), List.of(new UserMessage("anterior")));
        var newerConversation = conversationService.createConversation(clientId, "Pregunta mas reciente");

        var resolvedConversation = conversationService.resolveActiveConversation(clientId, null);

        assertThat(resolvedConversation.activeConversationId()).isEqualTo(newerConversation.id());
        assertThat(resolvedConversation.messages()).isEmpty();
        assertThat(resolvedConversation.conversations())
                .extracting(ConversationSummary::id)
                .containsExactly(newerConversation.id(), olderConversation.id());
    }

    @Test
    void resolveActiveConversation_falls_back_when_requested_conversation_belongs_to_other_client() {
        var clientOne = UUID.randomUUID();
        var clientTwo = UUID.randomUUID();
        var visibleConversation = conversationService.createConversation(clientOne, "Visible para client one");
        var hiddenConversation = conversationService.createConversation(clientTwo, "Visible para client two");
        chatMemory.add(visibleConversation.id().toString(), List.of(new UserMessage("mensaje visible")));
        chatMemory.add(hiddenConversation.id().toString(), List.of(new UserMessage("mensaje oculto")));

        var resolvedConversation = conversationService.resolveActiveConversation(clientOne, hiddenConversation.id());

        assertThat(resolvedConversation.activeConversationId()).isEqualTo(visibleConversation.id());
        assertThat(resolvedConversation.messages())
                .extracting(StoredChatMessage::content)
                .containsExactly("mensaje visible");
        assertThat(resolvedConversation.conversations())
                .extracting(ConversationSummary::id)
                .containsExactly(visibleConversation.id());
    }

    @TestConfiguration
    @Import({
            ConversationService.class,
            PostgresChatMemory.class,
            ChatUsageService.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            ChatJpaRepository.class,
            ChatTranscriptJpaRepository.class,
            ChatMessageJpaRepository.class
    })
    @EnableTransactionManagement
    static class TestConfig {

        @Bean(initMethod = "migrate")
        Flyway flyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
        }

        @Bean
        DataSource dataSource() {
            var dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(POSTGRES.getJdbcUrl());
            dataSource.setUsername(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource, Flyway flyway) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ChatProperties chatProperties() {
            var chatProperties = new ChatProperties();
            chatProperties.setClientIdCookieName("st_client_id");
            chatProperties.setContextWindowTokens(40_960);
            chatProperties.setReservedOutputTokens(4_096);
            return chatProperties;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Flyway flyway) {
            var factoryBean = new LocalContainerEntityManagerFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setPackagesToScan(ChatEntity.class.getPackageName());
            factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factoryBean.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "none",
                    "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"
            ));
            return factoryBean;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

    }
}
