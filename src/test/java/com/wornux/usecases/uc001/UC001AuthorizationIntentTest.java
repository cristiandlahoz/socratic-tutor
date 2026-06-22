package com.wornux.usecases.uc001;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;

class UC001AuthorizationIntentTest extends UC001MigrationTestSupport {

    @Test
    void mainFlow_businessIntentMatchesTenantProfessorAndStudentBoundaries() throws Exception {
        Fixture fixture = insertAuthorizationFixture();
        AuthorizationIntentPolicy policy = policy();

        assertTrue(policy.canCreateTenant(fixture.adminAccountId()));
        assertFalse(policy.canCreateTenant(fixture.tenantAdminAccountId()));

        assertTrue(policy.canCreateSubject(fixture.tenantAdminAccountId(), fixture.tenantId()));
        assertFalse(policy.canCreateSubject(fixture.professorAccountId(), fixture.tenantId()));
        assertFalse(policy.canCreateSubject(fixture.studentOneAccountId(), fixture.tenantId()));

        assertTrue(policy.canCreateAcademicPeriod(fixture.tenantAdminAccountId(), fixture.tenantId()));
        assertFalse(policy.canCreateAcademicPeriod(fixture.professorAccountId(), fixture.tenantId()));
        assertFalse(policy.canCreateAcademicPeriod(fixture.studentOneAccountId(), fixture.tenantId()));

        assertTrue(policy.canCreateGroupClass(fixture.tenantAdminAccountId(), fixture.tenantId()));
        assertFalse(policy.canCreateGroupClass(fixture.professorAccountId(), fixture.tenantId()));

        assertTrue(policy.canInviteProfessor(fixture.tenantAdminAccountId(), fixture.groupClassId()));
        assertTrue(policy.canInviteStudent(fixture.professorAccountId(), fixture.groupClassId()));
        assertFalse(policy.canInviteStudent(fixture.outsiderProfessorAccountId(), fixture.groupClassId()));

        assertTrue(policy.canUpdateGroupClass(fixture.professorAccountId(), fixture.groupClassId()));
        assertFalse(policy.canUpdateGroupClass(fixture.outsiderProfessorAccountId(), fixture.groupClassId()));
        assertTrue(policy.canManageGroupClassMembers(fixture.professorAccountId(), fixture.groupClassId()));
        assertFalse(policy.canManageGroupClassMembers(fixture.outsiderProfessorAccountId(), fixture.groupClassId()));

        assertTrue(policy.canCreateGrounding(fixture.professorAccountId(), fixture.groupClassId()));
        assertFalse(policy.canCreateGrounding(fixture.outsiderProfessorAccountId(), fixture.groupClassId()));
        assertTrue(policy.canCreateEvaluation(fixture.professorAccountId(), fixture.groupClassId()));
        assertFalse(policy.canCreateEvaluation(fixture.studentOneAccountId(), fixture.groupClassId()));
    }

    @Test
    void br69_studentsCanOnlyOperateOnOwnAssignmentsAndConversations() throws Exception {
        Fixture fixture = insertAuthorizationFixture();
        AuthorizationIntentPolicy policy = policy();

        assertTrue(policy.canViewAssignment(fixture.studentOneAccountId(), fixture.studentOneAssignmentId()));
        assertFalse(policy.canViewAssignment(fixture.studentOneAccountId(), fixture.studentTwoAssignmentId()));
        assertTrue(policy.canUpdateAssignment(fixture.studentOneAccountId(), fixture.studentOneAssignmentId(), "STARTED"));
        transitionAssignment(fixture.studentOneAssignmentId(), "STARTED");
        assertTrue(policy.canUpdateAssignment(fixture.studentOneAccountId(), fixture.studentOneAssignmentId(), "SUBMITTED"));
        assertFalse(policy.canUpdateAssignment(fixture.studentOneAccountId(), fixture.studentTwoAssignmentId(), "STARTED"));

        assertTrue(policy.canCreateConversation(fixture.studentOneAccountId(), fixture.groupClassId()));
        assertTrue(policy.canViewConversation(fixture.studentOneAccountId(), fixture.studentOneConversationId()));
        assertFalse(policy.canViewConversation(fixture.studentOneAccountId(), fixture.studentTwoConversationId()));

        assertTrue(policy.canViewAssignment(fixture.adminAccountId(), fixture.studentTwoAssignmentId()));
        assertTrue(policy.canViewConversation(fixture.adminAccountId(), fixture.studentTwoConversationId()));
    }

    private void transitionAssignment(java.util.UUID assignmentId, String status) throws Exception {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "update evaluation_assignment set status = ? where id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, assignmentId);
            statement.executeUpdate();
        }
    }
}
