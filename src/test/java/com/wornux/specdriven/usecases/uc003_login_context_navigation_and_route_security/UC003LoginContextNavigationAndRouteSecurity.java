package com.wornux.specdriven.usecases.uc003_login_context_navigation_and_route_security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AccessSnapshotService;
import com.wornux.services.context.ContextDiscoveryService;
import com.wornux.services.context.ContextSelectionResult;
import com.wornux.services.context.ContextSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@SpringBootTest(classes = UC003LoginContextNavigationAndRouteSecurity.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.docker.compose.enabled=false",
        "spring.ai.ollama.embedding.enabled=false",
        "spring.ai.vectorstore.pgvector.enabled=false",
        "spring.flyway.locations=classpath:db/migration/prod,classpath:db/migration/dev"
})
class UC003LoginContextNavigationAndRouteSecurity {

    private static final UUID TENANT_ID = UUID.fromString("034daffd-5907-48f7-bce6-b2c0e71f4015");
    private static final UUID ALGORITHMS_CLASS_ID = UUID.fromString("c63c4824-8ec7-4f62-9417-efd48b9adc62");
    private static final UUID DISCRETE_MATH_CLASS_ID = UUID.fromString("61e0d5a3-de6f-4607-a8a7-fd6847c623cb");
    private static final UUID STUDENT_ACCOUNT_ID = UUID.fromString("32b92c98-3b76-49bb-9fcf-3b12a7f17b2c");
    private static final UUID PROFESSOR_ACCOUNT_ID = UUID.fromString("b17d0169-e8f3-4392-8a42-4f629ae2d7a6");
    private static final UUID TENANT_ADMIN_ACCOUNT_ID = UUID.fromString("aa875f81-98c8-444d-8b32-3bce9e0467b5");
    private static final UUID TENANT_ADMIN_TENANT_ACCOUNT_ID = UUID.fromString("d1a532a0-0247-41b9-a1c5-5a6275dd61e2");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AccountRepository accountRepository;
    @Autowired ContextDiscoveryService contextDiscoveryService;
    @Autowired ContextSelectionService contextSelectionService;
    @Autowired ActiveContextHolder activeContextHolder;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        jdbc.update("delete from group_class_member where id in (?, ?)",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"));
        activeContextHolder.clear();
    }

    @Test
    void mainFlow_noContextReturnsNoAccessDecision() {
        var account = accountRepository.findByEmail("unassigned@example.test").orElseGet(() -> {
            jdbc.update("insert into account (id, email, first_name, last_name, password_hash, locked) values (?, ?, ?, ?, ?, false)",
                    UUID.fromString("11111111-1111-1111-1111-111111111111"), "unassigned@example.test", "No", "Access", "noop");
            return accountRepository.findByEmail("unassigned@example.test").orElseThrow();
        });

        assertThat(contextSelectionService.resolveLoginContext(account)).isInstanceOf(ContextSelectionResult.NoAccess.class);
    }

    @Test
    void mainFlow_oneClassMembershipAutoSelectsGroupClassContext() {
        var result = contextSelectionService.resolveLoginContext(accountRepository.findById(STUDENT_ACCOUNT_ID).orElseThrow());

        assertThat(result).isInstanceOf(ContextSelectionResult.Selected.class);
        assertThat(((ContextSelectionResult.Selected) result).option().level()).isEqualTo(ContextLevel.GROUP_CLASS);
        assertThat(activeContextHolder.current()).get().extracting("groupClassId").isEqualTo(ALGORITHMS_CLASS_ID);
    }

    @Test
    void mainFlow_multipleClassMembershipsRequireSelection() {
        jdbc.update("insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked) values (?, ?, ?, 'PROFESSOR', false) on conflict do nothing",
                UUID.fromString("22222222-2222-2222-2222-222222222222"), DISCRETE_MATH_CLASS_ID, UUID.fromString("c754e015-2113-403a-96a8-292d1aa137ae"));
        jdbc.update("delete from account_context_preference where account_id = ?", PROFESSOR_ACCOUNT_ID);

        var result = contextSelectionService.resolveLoginContext(accountRepository.findById(PROFESSOR_ACCOUNT_ID).orElseThrow());

        assertThat(result).isInstanceOf(ContextSelectionResult.SelectionRequired.class);
        assertThat(((ContextSelectionResult.SelectionRequired) result).options())
                .filteredOn(option -> option.level() == ContextLevel.GROUP_CLASS)
                .hasSize(2);
    }

    @Test
    void mainFlow_tenantAdminGetsTenantContextOnlyFromAdministrativeReach() {
        var account = accountRepository.findById(TENANT_ADMIN_ACCOUNT_ID).orElseThrow();

        var result = contextSelectionService.resolveLoginContext(account);

        assertThat(result).isInstanceOf(ContextSelectionResult.Selected.class);
        assertThat(((ContextSelectionResult.Selected) result).option().level()).isEqualTo(ContextLevel.TENANT);
        assertThat(contextDiscoveryService.discover(account))
                .filteredOn(option -> option.level() == ContextLevel.GROUP_CLASS)
                .isEmpty();
    }

    @Test
    void mainFlow_tenantAdminProfessorClassSwitcherContainsOnlyRealMembershipClasses() {
        jdbc.update("insert into group_class_member (id, group_class_id, tenant_account_id, member_kind, locked) values (?, ?, ?, 'PROFESSOR', false) on conflict do nothing",
                UUID.fromString("33333333-3333-3333-3333-333333333333"), ALGORITHMS_CLASS_ID, TENANT_ADMIN_TENANT_ACCOUNT_ID);
        var account = accountRepository.findById(TENANT_ADMIN_ACCOUNT_ID).orElseThrow();

        assertThat(contextDiscoveryService.discover(account))
                .filteredOn(option -> option.level() == ContextLevel.GROUP_CLASS)
                .extracting("classId")
                .containsExactly(ALGORITHMS_CLASS_ID);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "com.vaadin.flow.spring.SpringBootAutoConfiguration",
            "com.vaadin.flow.spring.SpringSecurityAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration",
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
            "io.arconia.dev.services.docling.DoclingDevServicesAutoConfiguration",
            "io.arconia.docling.autoconfigure.DoclingAutoConfiguration",
            "io.arconia.docling.autoconfigure.actuate.DoclingServeHealthContributorAutoConfiguration"
    })
    @EnableJpaRepositories(basePackages = "com.wornux.data.repositories")
    @EntityScan(basePackages = "com.wornux.data.entities")
    @Import({ContextDiscoveryService.class, ContextSelectionService.class, ActiveContextHolder.class, AccessSnapshotService.class})
    static class TestApp {}
}
