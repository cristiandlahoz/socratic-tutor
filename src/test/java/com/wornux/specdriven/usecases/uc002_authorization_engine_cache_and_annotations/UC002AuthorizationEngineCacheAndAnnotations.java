package com.wornux.specdriven.usecases.uc002_authorization_engine_cache_and_annotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.security.AuthenticatedAccountDetails;
import com.wornux.security.authorization.AccessSnapshotService;
import com.wornux.security.authorization.ActiveContext;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.PermissionMethodAspect;
import com.wornux.security.authorization.RbacUiBroadcaster;
import com.wornux.security.authorization.RbacUiRegistry;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.authorization.RoleManagementService;
import com.wornux.security.permission.AppPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = UC002AuthorizationEngineCacheAndAnnotations.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.docker.compose.enabled=false",
                "spring.ai.ollama.embedding.enabled=false",
                "spring.ai.vectorstore.pgvector.enabled=false",
                "spring.flyway.locations=classpath:db/migration/prod,classpath:db/migration/dev" })
class UC002AuthorizationEngineCacheAndAnnotations {

    private static final UUID TENANT_ID = UUID.fromString("034daffd-5907-48f7-bce6-b2c0e71f4015");
    private static final UUID ALGORITHMS_CLASS_ID = UUID.fromString("c63c4824-8ec7-4f62-9417-efd48b9adc62");
    private static final UUID DISCRETE_MATH_CLASS_ID = UUID.fromString("61e0d5a3-de6f-4607-a8a7-fd6847c623cb");
    private static final UUID STUDENT_ACCOUNT_ID = UUID.fromString("32b92c98-3b76-49bb-9fcf-3b12a7f17b2c");
    private static final UUID PROFESSOR_ACCOUNT_ID = UUID.fromString("b17d0169-e8f3-4392-8a42-4f629ae2d7a6");
    private static final UUID TENANT_ADMIN_ACCOUNT_ID = UUID.fromString("aa875f81-98c8-444d-8b32-3bce9e0467b5");
    private static final UUID PROFESSOR_ROLE_ID = UUID.fromString("e034005c-3565-4fe6-a23b-8fe1ccf85584");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ActiveContextHolder activeContextHolder;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private AnnotatedProbe annotatedProbe;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        activeContextHolder.clear();
    }

    @Test
    void mainFlow_groupClassAndTenantSnapshotsResolveContextualPermissions() {
        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        assertThat(authorizationService.can(AppPermission.CONVERSATION_CREATE)).isTrue();

        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        var tenantAdminSnapshot = authorizationService.snapshot();
        assertThat(tenantAdminSnapshot.permissionCodes())
                .contains("role:update", "group-class:create", "training-activity:create");
        assertThat(tenantAdminSnapshot.groupClassMemberId()).isNull();

        authenticate(PROFESSOR_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        assertThat(authorizationService.can(AppPermission.TRAINING_ACTIVITY_CREATE)).isTrue();
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, DISCRETE_MATH_CLASS_ID));
        assertThat(authorizationService.can(AppPermission.TRAINING_ACTIVITY_CREATE)).isFalse();
    }

    @Test
    void br01_roleUpdateIncrementsNamespaceVersionAndChangesSnapshotKey() {
        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        var before = authorizationService.snapshot();

        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));
        roleManagementService.updatePermissions(
            PROFESSOR_ROLE_ID,
            Set.of(
                "group-class:view",
                "group-class:update",
                "group-class-member:view",
                "group-class-member:invite",
                "group-class-member:update",
                "group-class-join-code:view",
                "group-class-join-code:create",
                "group-class-join-code:update",
                "group-class-join-code:delete",
                "training-activity:view",
                "training-activity:create",
                "training-activity:update",
                "training-activity:delete",
                "training-activity-assignment:view",
                "training-activity-assignment:create",
                "training-activity-assignment:update",
                "training-activity-assignment:delete",
                "conversation:view"));

        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        var after = authorizationService.snapshot();
        assertThat(after.roleNamespaceVersion()).isGreaterThan(before.roleNamespaceVersion());
    }

    @Test
    void br02_actorCannotGrantPermissionOutsideSnapshot() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));

        assertThatThrownBy(
            () -> roleManagementService
                    .updatePermissions(PROFESSOR_ROLE_ID, Set.of("conversation:view", "conversation:create")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("outside the actor snapshot");
    }

    @Test
    void br03_requiresPermissionAnnotationDelegatesToAuthorizationService() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));
        assertThat(annotatedProbe.roleUpdateProtectedMethod()).isEqualTo("ok");

        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        assertThatThrownBy(() -> annotatedProbe.roleUpdateProtectedMethod()).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("role:update");
    }

    private void authenticate(UUID accountId) {
        var account = accountRepository.findById(accountId).orElseThrow();
        var details = new AuthenticatedAccountDetails(account);
        SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(details, details.getPassword(), details.getAuthorities()));
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
            "io.arconia.docling.autoconfigure.actuate.DoclingServeHealthContributorAutoConfiguration" })
    @EnableJpaRepositories(basePackages = "com.wornux.data.repositories")
    @EntityScan(basePackages = "com.wornux.data.entities")
    @EnableAspectJAutoProxy
    @Import({
            ActiveContextHolder.class,
            AccessSnapshotService.class,
            AuthorizationService.class,
            RoleManagementService.class,
            PermissionMethodAspect.class,
            RbacUiRegistry.class,
            RbacUiBroadcaster.class })
    static class TestApp {
        @Bean
        AnnotatedProbe annotatedProbe() {
            return new AnnotatedProbe();
        }
    }

    static class AnnotatedProbe {
        @RequiresPermission(AppPermission.ROLE_UPDATE)
        public String roleUpdateProtectedMethod() {
            return "ok";
        }
    }
}
