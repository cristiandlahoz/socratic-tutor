package com.wornux.specdriven.usecases.uc004_role_matrix_and_assignment_ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.authorization.RoleAssignmentLevel;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.security.AuthenticatedAccountDetails;
import com.wornux.security.authorization.AccessSnapshotService;
import com.wornux.security.authorization.ActiveContext;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.PermissionMethodAspect;
import com.wornux.security.authorization.RbacChangedEvent;
import com.wornux.security.authorization.RbacUiBroadcaster;
import com.wornux.security.authorization.RbacUiRegistry;
import com.wornux.security.authorization.RoleAdministrationService;
import com.wornux.security.authorization.RoleAdministrationService.CreateRoleCommand;
import com.wornux.security.permission.AppPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
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
@SpringBootTest(classes = UC004RoleMatrixAndAssignmentUi.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.docker.compose.enabled=false",
                "spring.ai.ollama.embedding.enabled=false",
                "spring.ai.vectorstore.pgvector.enabled=false",
                "spring.flyway.locations=classpath:db/migration/prod,classpath:db/migration/dev" })
class UC004RoleMatrixAndAssignmentUi {

    private static final UUID TENANT_ID = UUID.fromString("034daffd-5907-48f7-bce6-b2c0e71f4015");
    private static final UUID ALGORITHMS_CLASS_ID = UUID.fromString("c63c4824-8ec7-4f62-9417-efd48b9adc62");
    private static final UUID STUDENT_ACCOUNT_ID = UUID.fromString("32b92c98-3b76-49bb-9fcf-3b12a7f17b2c");
    private static final UUID PROFESSOR_ACCOUNT_ID = UUID.fromString("b17d0169-e8f3-4392-8a42-4f629ae2d7a6");
    private static final UUID TENANT_ADMIN_ACCOUNT_ID = UUID.fromString("aa875f81-98c8-444d-8b32-3bce9e0467b5");
    private static final UUID STUDENT_MEMBER_ID = UUID.fromString("4ead0b6d-c90b-4da0-b838-c42123408e06");

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
    private RoleAdministrationService roleAdministrationService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GroupClassMemberRepository groupClassMemberRepository;

    @Autowired
    private RbacEventProbe eventProbe;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        activeContextHolder.clear();
        eventProbe.clear();
    }

    @Test
    void mainFlow_tenantAdminCreatesGroupClassRoleWithOnlyPermissionsTheyHave() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));

        var role = roleAdministrationService.createRole(
            new CreateRoleCommand("Activity Reviewer",
                    "Can review training activities inside a class.",
                    RoleAssignmentLevel.GROUP_CLASS,
                    10,
                    Set.of("training-activity:view")));

        assertThat(role.getAssignmentLevel()).isEqualTo(RoleAssignmentLevel.GROUP_CLASS);
        assertThat(role.getPermissions()).containsExactly("training-activity:view");
        assertThat(roleRepository.findByRoleNamespace_IdAndCode(role.getRoleNamespace().getId(), "activity-reviewer"))
                .isPresent();
        assertThat(eventProbe.events()).contains(role.getRoleNamespace().getId());
    }

    @Test
    void br01_tenantAdminCannotCreateRoleContainingUnknownPermissionString() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));

        assertThatThrownBy(
            () -> roleAdministrationService.createRole(
                new CreateRoleCommand("Unknown Permission Role",
                        null,
                        RoleAssignmentLevel.GROUP_CLASS,
                        10,
                        Set.of("unknown:permission"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known AppPermission");
    }

    @Test
    void br02_tenantAdminCannotGrantPermissionTheyDoNotHave() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));

        assertThatThrownBy(
            () -> roleAdministrationService.createRole(
                new CreateRoleCommand("Conversation Creator",
                        null,
                        RoleAssignmentLevel.GROUP_CLASS,
                        10,
                        Set.of("conversation:create"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("outside the actor snapshot");
    }

    @Test
    void br03_professorCannotOpenRoleCreationServiceBecauseTheyLackRoleCreate() {
        authenticate(PROFESSOR_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));

        assertThatThrownBy(
            () -> roleAdministrationService.createRole(
                new CreateRoleCommand("Professor Managed Role",
                        null,
                        RoleAssignmentLevel.GROUP_CLASS,
                        10,
                        Set.of("training-activity:view"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(AppPermission.ROLE_CREATE.code());
    }

    @Test
    void br04_assigningGroupClassRoleChangesPermissionsButNotMemberKind() {
        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));
        var role = roleAdministrationService.createRole(
            new CreateRoleCommand("Student Activity Creator",
                    null,
                    RoleAssignmentLevel.GROUP_CLASS,
                    10,
                    Set.of("training-activity:create")));
        var memberKindBefore = groupClassMemberRepository.findById(STUDENT_MEMBER_ID).orElseThrow().getMemberKind();

        roleAdministrationService.setGroupClassRole(STUDENT_MEMBER_ID, role.getId(), true);

        assertThat(groupClassMemberRepository.findById(STUDENT_MEMBER_ID).orElseThrow().getMemberKind())
                .isEqualTo(memberKindBefore)
                .isEqualTo(GroupClassMemberKind.STUDENT);
        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        assertThat(authorizationService.can(AppPermission.TRAINING_ACTIVITY_CREATE)).isTrue();
    }

    @Test
    void br05_roleChangeIncrementsNamespaceVersionAndInvalidatesSnapshotKey() {
        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        var before = authorizationService.snapshot();

        authenticate(TENANT_ADMIN_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.tenant(TENANT_ID));
        roleAdministrationService.createRole(
            new CreateRoleCommand("Version Bump Role",
                    null,
                    RoleAssignmentLevel.GROUP_CLASS,
                    10,
                    Set.of("training-activity:view")));

        authenticate(STUDENT_ACCOUNT_ID);
        activeContextHolder.set(ActiveContext.groupClass(TENANT_ID, ALGORITHMS_CLASS_ID));
        var after = authorizationService.snapshot();
        assertThat(after.roleNamespaceVersion()).isGreaterThan(before.roleNamespaceVersion());
        assertThat(eventProbe.events()).isNotEmpty();
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
            RoleAdministrationService.class,
            PermissionMethodAspect.class,
            RbacUiRegistry.class,
            RbacUiBroadcaster.class,
            RbacEventProbe.class })
    static class TestApp {}

    static class RbacEventProbe {
        private final CopyOnWriteArrayList<UUID> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onRbacChanged(RbacChangedEvent event) {
            events.add(event.roleNamespaceId());
        }

        java.util.List<UUID> events() {
            return events;
        }

        void clear() {
            events.clear();
        }
    }
}
