package com.wornux.specdriven.usecases.uc001_rbac_schema_and_domain_model;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class UC001RbacSchemaAndDomainModel {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void mainFlow_schemaAndDomainPersistenceSupportsNamespacedRolesAndClassroomIdentity() throws Exception {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/prod").load().migrate();

        try (var connection = dataSource.getConnection()) {
            var tenantNamespaceId = UUID.randomUUID();
            var tenantId = UUID.randomUUID();
            var tenantAdminAccountId = UUID.randomUUID();
            var professorAccountId = UUID.randomUUID();
            var tenantAdminTenantAccountId = UUID.randomUUID();
            var professorTenantAccountId = UUID.randomUUID();
            var tenantAdminRoleId = UUID.randomUUID();
            var professorRoleId = UUID.randomUUID();
            var subjectId = UUID.randomUUID();
            var periodId = UUID.randomUUID();
            var groupClassId = UUID.randomUUID();
            var groupClassMemberId = UUID.randomUUID();

            execute(connection,
                    "insert into role_namespace (id, code, created_at, updated_at) values (?, ?, current_timestamp, current_timestamp)",
                    tenantNamespaceId, "tenant:%s".formatted(tenantId));
            execute(connection,
                    "insert into tenant (id, role_namespace_id, name, locked) values (?, ?, ?, false)",
                    tenantId, tenantNamespaceId, "UC001 Academy");

            assertThat(count(connection, "select count(*) from tenant where id = ? and role_namespace_id = ?", tenantId,
                    tenantNamespaceId)).isEqualTo(1);

            execute(connection,
                    "insert into account (id, email, password_hash) values (?, ?, ?), (?, ?, ?)",
                    tenantAdminAccountId, "tenant-admin-uc001@example.test", "hash", professorAccountId,
                    "professor-uc001@example.test", "hash");
            execute(connection,
                    "insert into tenant_account (id, tenant_id, account_id) values (?, ?, ?), (?, ?, ?)",
                    tenantAdminTenantAccountId, tenantId, tenantAdminAccountId, professorTenantAccountId, tenantId,
                    professorAccountId);

            Array tenantAdminPermissions = connection.createArrayOf("text",
                    new String[] {"role:assign", "group-class:create", "group-class-member:invite"});
            Array professorPermissions = connection.createArrayOf("text",
                    new String[] {"group-class:view", "conversation:view"});
            execute(connection,
                    "insert into role (id, role_namespace_id, code, name, assignment_level, permissions, priority, system_defined) values (?, ?, ?, ?, 'TENANT', ?, 80, true), (?, ?, ?, ?, 'GROUP_CLASS', ?, 60, true)",
                    tenantAdminRoleId, tenantNamespaceId, "TENANT_ADMIN", "Tenant Admin", tenantAdminPermissions,
                    professorRoleId, tenantNamespaceId, "PROFESSOR", "Professor", professorPermissions);

            assertThat(textArray(connection, "select permissions from role where id = ?", tenantAdminRoleId))
                    .containsExactly("role:assign", "group-class:create", "group-class-member:invite");

            execute(connection,
                    "insert into tenant_account_role (tenant_account_id, role_id) values (?, ?)",
                    tenantAdminTenantAccountId, tenantAdminRoleId);

            execute(connection,
                    "insert into subject (id, tenant_id, code, name) values (?, ?, ?, ?)",
                    subjectId, tenantId, "UC001", "UC001 Subject");
            execute(connection,
                    "insert into academic_period (id, tenant_id, code, name, starts_at, ends_at) values (?, ?, ?, ?, current_date, current_date + 10)",
                    periodId, tenantId, "UC001", "UC001 Period");
            execute(connection,
                    "insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name) values (?, ?, ?, ?, ?, ?, ?)",
                    groupClassId, tenantId, subjectId, periodId, tenantAdminTenantAccountId, "UC001-01",
                    "UC001 Class");
            execute(connection,
                    "insert into group_class_member (id, group_class_id, tenant_account_id, member_kind) values (?, ?, ?, 'PROFESSOR')",
                    groupClassMemberId, groupClassId, professorTenantAccountId);
            execute(connection,
                    "insert into group_class_member_role (group_class_member_id, role_id) values (?, ?)",
                    groupClassMemberId, professorRoleId);

            assertThat(count(connection,
                    "select count(*) from group_class_member where id = ? and member_kind = 'PROFESSOR'",
                    groupClassMemberId)).isEqualTo(1);
            assertThat(count(connection,
                    "select count(*) from group_class_member_role where group_class_member_id = ? and role_id = ?",
                    groupClassMemberId, professorRoleId)).isEqualTo(1);
            assertThat(count(connection,
                    "select count(*) from group_class_member where tenant_account_id = ?",
                    tenantAdminTenantAccountId)).isZero();
        }
    }

    private static void execute(java.sql.Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private static long count(java.sql.Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String[] textArray(java.sql.Connection connection, String sql, Object value) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return (String[]) resultSet.getArray(1).getArray();
            }
        }
    }
}
