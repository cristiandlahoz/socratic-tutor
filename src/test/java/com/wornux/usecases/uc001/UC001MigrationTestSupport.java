package com.wornux.usecases.uc001;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;

abstract class UC001MigrationTestSupport {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetSchema() {
        flyway().clean();
        flyway().migrate();
    }

    protected Flyway flyway() {
        return Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    protected Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    protected boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement =
                connection.prepareStatement("""
                                            select exists (
                                                select 1
                                                from information_schema.tables
                                                where table_schema = 'public' and table_name = ?
                                            )
                                            """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    protected boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                                                                          select exists (
                                                                              select 1
                                                                              from information_schema.columns
                                                                              where table_schema = 'public'
                                                                                and table_name = ?
                                                                                and column_name = ?
                                                                          )
                                                                          """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    protected List<String> singleColumnList(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }

    protected record Fixture(UUID tenantId, UUID otherTenantId, UUID adminAccountId, UUID tenantAdminAccountId,
            UUID professorAccountId, UUID outsiderProfessorAccountId, UUID studentOneAccountId,
            UUID studentTwoAccountId, UUID tenantAdminTenantAccountId, UUID professorTenantAccountId,
            UUID outsiderProfessorTenantAccountId, UUID studentOneTenantAccountId, UUID studentTwoTenantAccountId,
            UUID groupClassId, UUID otherGroupClassId, UUID professorMemberId, UUID outsiderProfessorMemberId,
            UUID studentOneMemberId, UUID studentTwoMemberId, UUID evaluationId, UUID studentOneAssignmentId,
            UUID studentTwoAssignmentId, UUID studentOneConversationId, UUID studentTwoConversationId) {}

    protected Fixture insertAuthorizationFixture() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID adminAccountId = account(UUID.randomUUID(), "sys-admin@test.local", "sysadmin", true);
        UUID tenantAdminAccountId = account(UUID.randomUUID(), "tenant-admin@test.local", "tenantadmin", false);
        UUID professorAccountId = account(UUID.randomUUID(), "prof@test.local", "professor", false);
        UUID outsiderProfessorAccountId = account(UUID.randomUUID(), "prof-2@test.local", "professor2", false);
        UUID studentOneAccountId = account(UUID.randomUUID(), "student-1@test.local", "student1", false);
        UUID studentTwoAccountId = account(UUID.randomUUID(), "student-2@test.local", "student2", false);

        tenant(tenantId, null, "Tenant One");
        tenant(otherTenantId, null, "Tenant Two");

        UUID tenantAdminTenantAccountId = tenantAccount(UUID.randomUUID(), tenantId, tenantAdminAccountId);
        UUID professorTenantAccountId = tenantAccount(UUID.randomUUID(), tenantId, professorAccountId);
        UUID outsiderProfessorTenantAccountId =
                tenantAccount(UUID.randomUUID(), otherTenantId, outsiderProfessorAccountId);
        UUID studentOneTenantAccountId = tenantAccount(UUID.randomUUID(), tenantId, studentOneAccountId);
        UUID studentTwoTenantAccountId = tenantAccount(UUID.randomUUID(), tenantId, studentTwoAccountId);

        updateTenantOwner(tenantId, tenantAdminTenantAccountId);
        updateTenantOwner(otherTenantId, outsiderProfessorTenantAccountId);

        assignRole(tenantAdminTenantAccountId, "TENANT_ADMIN");
        assignRole(professorTenantAccountId, "PROFESSOR");
        assignRole(outsiderProfessorTenantAccountId, "PROFESSOR");
        assignRole(studentOneTenantAccountId, "STUDENT");
        assignRole(studentTwoTenantAccountId, "STUDENT");

        UUID subjectId = subject(UUID.randomUUID(), tenantId, "ICC-101", "Algorithms");
        UUID periodId = academicPeriod(UUID.randomUUID(), tenantId, "2026-Q3", "2026 Q3");
        UUID otherSubjectId = subject(UUID.randomUUID(), otherTenantId, "MTH-101", "Math");
        UUID otherPeriodId = academicPeriod(UUID.randomUUID(), otherTenantId, "2026-Q3", "2026 Q3");

        UUID groupClassId =
                groupClass(UUID.randomUUID(), tenantId, subjectId, periodId, tenantAdminTenantAccountId, "ICC-101-A");
        UUID otherGroupClassId = groupClass(
            UUID.randomUUID(),
            otherTenantId,
            otherSubjectId,
            otherPeriodId,
            outsiderProfessorTenantAccountId,
            "MTH-101-A");

        UUID professorMemberId =
                groupClassMember(UUID.randomUUID(), groupClassId, professorTenantAccountId, "PROFESSOR");
        UUID outsiderProfessorMemberId =
                groupClassMember(UUID.randomUUID(), otherGroupClassId, outsiderProfessorTenantAccountId, "PROFESSOR");
        UUID studentOneMemberId =
                groupClassMember(UUID.randomUUID(), groupClassId, studentOneTenantAccountId, "STUDENT");
        UUID studentTwoMemberId =
                groupClassMember(UUID.randomUUID(), groupClassId, studentTwoTenantAccountId, "STUDENT");

        UUID evaluationId = evaluation(UUID.randomUUID(), groupClassId, professorMemberId, "Midterm");
        UUID studentOneAssignmentId =
                evaluationAssignment(UUID.randomUUID(), evaluationId, studentOneMemberId, "ASSIGNED");
        UUID studentTwoAssignmentId =
                evaluationAssignment(UUID.randomUUID(), evaluationId, studentTwoMemberId, "ASSIGNED");
        UUID studentOneConversationId = conversation(UUID.randomUUID(), studentOneMemberId, "Student One Conversation");
        UUID studentTwoConversationId = conversation(UUID.randomUUID(), studentTwoMemberId, "Student Two Conversation");

        return new Fixture(tenantId,
                otherTenantId,
                adminAccountId,
                tenantAdminAccountId,
                professorAccountId,
                outsiderProfessorAccountId,
                studentOneAccountId,
                studentTwoAccountId,
                tenantAdminTenantAccountId,
                professorTenantAccountId,
                outsiderProfessorTenantAccountId,
                studentOneTenantAccountId,
                studentTwoTenantAccountId,
                groupClassId,
                otherGroupClassId,
                professorMemberId,
                outsiderProfessorMemberId,
                studentOneMemberId,
                studentTwoMemberId,
                evaluationId,
                studentOneAssignmentId,
                studentTwoAssignmentId,
                studentOneConversationId,
                studentTwoConversationId);
    }

    protected AuthorizationIntentPolicy policy() {
        return new AuthorizationIntentPolicy();
    }

    protected final class AuthorizationIntentPolicy {

        boolean canCreateTenant(UUID accountId) throws SQLException {
            return isSystemAdmin(accountId) && hasRolePermission("SYSTEM_ADMIN", "TENANT:CREATE");
        }

        boolean canCreateSubject(UUID accountId, UUID tenantId) throws SQLException {
            return canActAsTenantAdmin(accountId, tenantId, "SUBJECT:CREATE");
        }

        boolean canCreateAcademicPeriod(UUID accountId, UUID tenantId) throws SQLException {
            return canActAsTenantAdmin(accountId, tenantId, "ACADEMIC_PERIOD:CREATE");
        }

        boolean canCreateGroupClass(UUID accountId, UUID tenantId) throws SQLException {
            return canActAsTenantAdmin(accountId, tenantId, "GROUP_CLASS:CREATE");
        }

        boolean canInviteProfessor(UUID accountId, UUID groupClassId) throws SQLException {
            UUID tenantId = tenantIdForGroupClass(groupClassId);
            return canActAsTenantAdmin(accountId, tenantId, "GROUP_CLASS_MEMBER:INVITE");
        }

        boolean canInviteStudent(UUID accountId, UUID groupClassId) throws SQLException {
            return isProfessorMember(accountId, groupClassId)
                    && hasRolePermission("PROFESSOR", "GROUP_CLASS_MEMBER:INVITE");
        }

        boolean canUpdateGroupClass(UUID accountId, UUID groupClassId) throws SQLException {
            return isProfessorMember(accountId, groupClassId) && hasRolePermission("PROFESSOR", "GROUP_CLASS:UPDATE");
        }

        boolean canManageGroupClassMembers(UUID accountId, UUID groupClassId) throws SQLException {
            return isProfessorMember(accountId, groupClassId)
                    && hasRolePermission("PROFESSOR", "GROUP_CLASS_MEMBER:UPDATE");
        }

        boolean canCreateGrounding(UUID accountId, UUID groupClassId) throws SQLException {
            return isProfessorMember(accountId, groupClassId) && hasRolePermission("PROFESSOR", "GROUNDING:CREATE");
        }

        boolean canCreateEvaluation(UUID accountId, UUID groupClassId) throws SQLException {
            return isProfessorMember(accountId, groupClassId)
                    && hasRolePermission("PROFESSOR", "TRAINING_ACTIVITY:CREATE");
        }

        boolean canViewAssignment(UUID accountId, UUID assignmentId) throws SQLException {
            if (isSystemAdmin(accountId)) {
                return hasRolePermission("SYSTEM_ADMIN", "TRAINING_ACTIVITY_ASSIGNMENT:VIEW");
            }
            return ownsAssignment(accountId, assignmentId)
                    && hasRolePermission("STUDENT", "TRAINING_ACTIVITY_ASSIGNMENT:VIEW");
        }

        boolean canUpdateAssignment(UUID accountId, UUID assignmentId, String targetStatus) throws SQLException {
            if (!List.of("STARTED", "SUBMITTED").contains(targetStatus)) {
                return false;
            }
            if (!ownsAssignment(accountId, assignmentId)
                    || !hasRolePermission("STUDENT", "TRAINING_ACTIVITY_ASSIGNMENT:UPDATE")) {
                return false;
            }
            String currentStatus = assignmentStatus(assignmentId);
            return (currentStatus.equals("ASSIGNED") && targetStatus.equals("STARTED"))
                    || (currentStatus.equals("STARTED") && targetStatus.equals("SUBMITTED"));
        }

        boolean canCreateConversation(UUID accountId, UUID groupClassId) throws SQLException {
            return isStudentMember(accountId, groupClassId) && hasRolePermission("STUDENT", "CONVERSATION:CREATE");
        }

        boolean canViewConversation(UUID accountId, UUID conversationId) throws SQLException {
            if (isSystemAdmin(accountId)) {
                return hasRolePermission("SYSTEM_ADMIN", "CONVERSATION:VIEW");
            }
            return ownsConversation(accountId, conversationId) && hasRolePermission("STUDENT", "CONVERSATION:VIEW");
        }

        private boolean canActAsTenantAdmin(UUID accountId, UUID tenantId, String permissionCode) throws SQLException {
            if (isSystemAdmin(accountId)) {
                return true;
            }
            return hasTenantRole(accountId, tenantId, "TENANT_ADMIN")
                    && hasRolePermission("TENANT_ADMIN", permissionCode);
        }

        private boolean isSystemAdmin(UUID accountId) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement =
                    connection.prepareStatement("select system_admin from account where id = ?")) {
                statement.setObject(1, accountId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private boolean hasRolePermission(String roleCode, String permissionCode) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement =
                    connection.prepareStatement("""
                                                select exists (
                                                    select 1
                                                    from role_permission rp
                                                    join role r on r.id = rp.role_id
                                                    join permission p on p.id = rp.permission_id
                                                    where r.code = ? and p.code = ?
                                                )
                                                """)) {
                statement.setString(1, roleCode);
                statement.setString(2, permissionCode);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private boolean hasTenantRole(UUID accountId, UUID tenantId, String roleCode) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from tenant_account ta
                    join tenant_account_role tar on tar.tenant_account_id = ta.id
                    join role r on r.id = tar.role_id
                    where ta.account_id = ? and ta.tenant_id = ? and r.code = ? and ta.locked = false
                )
                """)) {
                statement.setObject(1, accountId);
                statement.setObject(2, tenantId);
                statement.setString(3, roleCode);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private boolean isProfessorMember(UUID accountId, UUID groupClassId) throws SQLException {
            return hasGroupClassMembership(accountId, groupClassId, "PROFESSOR")
                    && hasTenantRole(accountId, tenantIdForGroupClass(groupClassId), "PROFESSOR");
        }

        private boolean isStudentMember(UUID accountId, UUID groupClassId) throws SQLException {
            return hasGroupClassMembership(accountId, groupClassId, "STUDENT")
                    && hasTenantRole(accountId, tenantIdForGroupClass(groupClassId), "STUDENT");
        }

        private boolean hasGroupClassMembership(UUID accountId, UUID groupClassId, String membershipRole)
                throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from group_class_member gcm
                    join tenant_account ta on ta.id = gcm.tenant_account_id
                    where ta.account_id = ? and gcm.group_class_id = ? and gcm.role = ? and gcm.locked = false
                )
                """)) {
                statement.setObject(1, accountId);
                statement.setObject(2, groupClassId);
                statement.setString(3, membershipRole);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private boolean ownsAssignment(UUID accountId, UUID assignmentId) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from training_activity_assignment ea
                    join group_class_member gcm on gcm.id = ea.group_class_member_id
                    join tenant_account ta on ta.id = gcm.tenant_account_id
                    where ea.id = ? and ta.account_id = ? and gcm.role = 'STUDENT' and gcm.locked = false
                )
                """)) {
                statement.setObject(1, assignmentId);
                statement.setObject(2, accountId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private String assignmentStatus(UUID assignmentId) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement =
                    connection.prepareStatement("select status from training_activity_assignment where id = ?")) {
                statement.setObject(1, assignmentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getString(1);
                }
            }
        }

        private boolean ownsConversation(UUID accountId, UUID conversationId) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from conversation c
                    join group_class_member gcm on gcm.id = c.group_class_member_id
                    join tenant_account ta on ta.id = gcm.tenant_account_id
                    where c.id = ? and ta.account_id = ? and gcm.role = 'STUDENT' and gcm.locked = false
                )
                """)) {
                statement.setObject(1, conversationId);
                statement.setObject(2, accountId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        }

        private UUID tenantIdForGroupClass(UUID groupClassId) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement =
                    connection.prepareStatement("select tenant_id from group_class where id = ?")) {
                statement.setObject(1, groupClassId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getObject(1, UUID.class);
                }
            }
        }
    }

    private UUID account(UUID id, String email, String username, boolean systemAdmin) throws SQLException {
        executeInsert(
            "insert into account (id, email, username, password_hash, system_admin, locked) values (?, ?, ?, ?, ?, false)",
            statement -> {
                statement.setObject(1, id);
                statement.setString(2, email);
                statement.setString(3, username);
                statement.setString(4, "fixture-hash");
                statement.setBoolean(5, systemAdmin);
            });
        return id;
    }

    private void tenant(UUID id, UUID ownerTenantAccountId, String name) throws SQLException {
        executeInsert(
            "insert into tenant (id, owner_tenant_account_id, name, locked) values (?, ?, ?, false)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, ownerTenantAccountId);
                statement.setString(3, name);
            });
    }

    private void updateTenantOwner(UUID tenantId, UUID ownerTenantAccountId) throws SQLException {
        executeInsert("update tenant set owner_tenant_account_id = ? where id = ?", statement -> {
            statement.setObject(1, ownerTenantAccountId);
            statement.setObject(2, tenantId);
        });
    }

    private UUID tenantAccount(UUID id, UUID tenantId, UUID accountId) throws SQLException {
        executeInsert(
            "insert into tenant_account (id, tenant_id, account_id, locked) values (?, ?, ?, false)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setObject(3, accountId);
            });
        return id;
    }

    private void assignRole(UUID tenantAccountId, String roleCode) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement =
                connection.prepareStatement("""
                                            insert into tenant_account_role (tenant_account_id, role_id)
                                            select ?, r.id
                                            from role r
                                            where r.code = ?
                                            """)) {
            statement.setObject(1, tenantAccountId);
            statement.setString(2, roleCode);
            statement.executeUpdate();
        }
    }

    private UUID subject(UUID id, UUID tenantId, String code, String name) throws SQLException {
        executeInsert(
            "insert into subject (id, tenant_id, code, name, active) values (?, ?, ?, ?, true)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setString(3, code);
                statement.setString(4, name);
            });
        return id;
    }

    private UUID academicPeriod(UUID id, UUID tenantId, String code, String name) throws SQLException {
        executeInsert(
            "insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active) values (?, ?, ?, ?, ?, ?, true)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setString(3, code);
                statement.setString(4, name);
                statement.setDate(5, Date.valueOf(LocalDate.of(2026, 8, 1)));
                statement.setDate(6, Date.valueOf(LocalDate.of(2026, 12, 1)));
            });
        return id;
    }

    private UUID groupClass(
            UUID id,
            UUID tenantId,
            UUID subjectId,
            UUID periodId,
            UUID creatorTenantAccountId,
            String code) throws SQLException {
        executeInsert(
            """
            insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active)
            values (?, ?, ?, ?, ?, ?, ?, true)
            """,
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setObject(3, subjectId);
                statement.setObject(4, periodId);
                statement.setObject(5, creatorTenantAccountId);
                statement.setString(6, code);
                statement.setString(7, code);
            });
        return id;
    }

    private UUID groupClassMember(UUID id, UUID groupClassId, UUID tenantAccountId, String role) throws SQLException {
        executeInsert(
            "insert into group_class_member (id, group_class_id, tenant_account_id, role, locked) values (?, ?, ?, ?, false)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, groupClassId);
                statement.setObject(3, tenantAccountId);
                statement.setString(4, role);
            });
        return id;
    }

    private UUID evaluation(UUID id, UUID groupClassId, UUID professorMemberId, String title) throws SQLException {
        executeInsert(
            """
            insert into training_activity (id, group_class_id, created_by_group_class_member_id, title, instructions, status)
            values (?, ?, ?, ?, ?, 'DRAFT')
            """,
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, groupClassId);
                statement.setObject(3, professorMemberId);
                statement.setString(4, title);
                statement.setString(5, title + " instructions");
            });
        return id;
    }

    private UUID evaluationAssignment(UUID id, UUID evaluationId, UUID studentMemberId, String status)
            throws SQLException {
        executeInsert(
            "insert into training_activity_assignment (id, training_activity_id, group_class_member_id, status) values (?, ?, ?, ?)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, evaluationId);
                statement.setObject(3, studentMemberId);
                statement.setString(4, status);
            });
        return id;
    }

    private UUID conversation(UUID id, UUID groupClassMemberId, String title) throws SQLException {
        executeInsert(
            "insert into conversation (id, group_class_member_id, title, version) values (?, ?, ?, 0)",
            statement -> {
                statement.setObject(1, id);
                statement.setObject(2, groupClassMemberId);
                statement.setString(3, title);
            });
        return id;
    }

    private void executeInsert(String sql, SqlConsumer<PreparedStatement> binder) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }
}
