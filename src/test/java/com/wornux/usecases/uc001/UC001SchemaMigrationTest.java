package com.wornux.usecases.uc001;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UC001SchemaMigrationTest extends UC001MigrationTestSupport {

    @Test
    void mainFlow_createsTargetSchemaWithoutLegacyTablesAndSeedsAuthorizationCatalog() throws Exception {
        assertEquals(List.of("1"), appliedFlywayVersions());

        for (String table : List.of(
                "account",
                "tenant",
                "tenant_account",
                "role",
                "resource",
                "action",
                "permission",
                "role_permission",
                "tenant_account_role",
                "subject",
                "academic_period",
                "group_class",
                "group_class_member",
                "group_class_join_code",
                "conversation",
                "conversation_snapshot",
                "grounding_collection",
                "grounding_document",
                "grounding_chunk",
                "evaluation",
                "evaluation_assignment")) {
            assertTrue(tableExists(table), () -> "Expected table " + table);
        }

        for (String legacyTable : List.of(
                "chat",
                "chat_transcript",
                "chat_message",
                "student_profile",
                "student_misconception",
                "student_profile_signal",
                "ingested_document",
                "document_ingestion_job",
                "document_segment",
                "vector_store",
                "legacy_subject",
                "legacy_subject_config_revision",
                "legacy_evaluation",
                "legacy_evaluation_run")) {
            assertFalse(tableExists(legacyTable), () -> "Did not expect legacy table " + legacyTable);
        }

        assertFalse(tableExists("conversation_message"));
        assertFalse(tableExists("evaluation_run"));
        assertFalse(tableExists("subject_config_revision"));
        assertTrue(columnExists("conversation", "current_snapshot_id"));
        assertFalse(columnExists("conversation", "active_snapshot_id"));
        assertTrue(columnExists("grounding_chunk", "embedding"));

        assertEquals(
                Set.of("SYSTEM_ADMIN", "TENANT_ADMIN", "PROFESSOR", "STUDENT", "ASSISTANT"),
                Set.copyOf(singleColumnList("select code from role")));
        assertEquals(
                Set.of(
                        "TENANT",
                        "SUBJECT",
                        "ACADEMIC_PERIOD",
                        "GROUP_CLASS",
                        "GROUP_CLASS_MEMBER",
                        "GROUP_CLASS_JOIN_CODE",
                        "GROUNDING",
                        "EVALUATION",
                        "EVALUATION_ASSIGNMENT",
                        "CONVERSATION"),
                Set.copyOf(singleColumnList("select code from resource")));
        assertEquals(Set.of("VIEW", "CREATE", "UPDATE", "DELETE", "INVITE"), Set.copyOf(singleColumnList("select code from action")));

        assertFalse(singleColumnList("select code from action where code in ('LOCK', 'RUN', 'JOIN', 'MANAGE', 'MONITOR')").size() > 0);
        assertTrue(singleColumnList("select code from permission where code = 'EVALUATION_ASSIGNMENT:UPDATE'").size() == 1);
        assertTrue(singleColumnList("select code from permission where code = 'GROUP_CLASS_MEMBER:DELETE'").size() == 1);
        assertTrue(singleColumnList("select code from permission where code = 'TENANT:CREATE'").size() == 1);
        assertTrue(singleColumnList("select code from permission where code = 'CONVERSATION:VIEW'").size() == 1);
        assertTrue(singleColumnList("select code from resource where code in ('CONVERSATION_SNAPSHOT', 'GROUNDING_DOCUMENT', 'GROUNDING_CHUNK')").isEmpty());
        assertTrue(singleColumnList("select id::text from tenant").isEmpty());
        assertTrue(singleColumnList("select id::text from subject").isEmpty());
        assertTrue(singleColumnList("select id::text from academic_period").isEmpty());
        assertTrue(singleColumnList("select id::text from group_class").isEmpty());
    }

    @Test
    void br01_br77_seededSystemAdminUsesSecureHashAndUniqueConstraintsHold() throws Exception {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "select email, username, system_admin, password_hash from account order by email")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("admin@socratic-tutor.com", resultSet.getString("email"));
                assertEquals("admin", resultSet.getString("username"));
                assertTrue(resultSet.getBoolean("system_admin"));
                String passwordHash = resultSet.getString("password_hash");
                assertTrue(passwordHash.startsWith("$2"));
                assertFalse(passwordHash.contains("replace-me-now"));
                assertFalse(resultSet.next());
            }
        }

        assertTrue(uniqueConstraintExists("account", "uk_account_email"));
        assertTrue(uniqueConstraintExists("account", "uk_account_username"));
        assertTrue(uniqueConstraintExists("tenant_account", "uk_tenant_account_tenant_account"));
        assertTrue(uniqueConstraintExists("group_class_join_code", "uk_group_class_join_code_code"));
        assertTrue(uniqueConstraintExists("evaluation_assignment", "uk_evaluation_assignment_evaluation_member"));
    }

    private boolean uniqueConstraintExists(String tableName, String constraintName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        select exists (
                            select 1
                            from information_schema.table_constraints
                            where table_schema = 'public'
                              and table_name = ?
                              and constraint_name = ?
                              and constraint_type = 'UNIQUE'
                        )
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private List<String> appliedFlywayVersions() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "select version from flyway_schema_history where success = true order by installed_rank")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> versions = new ArrayList<>();
                while (resultSet.next()) {
                    versions.add(resultSet.getString(1));
                }
                return versions;
            }
        }
    }
}
