package com.wornux.usecases.uc001;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.wornux.data.entities.academic.AcademicPeriod;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassJoinCode;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.Subject;
import com.wornux.data.entities.authorization.Action;
import com.wornux.data.entities.authorization.Permission;
import com.wornux.data.entities.authorization.Resource;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.RolePermission;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.data.entities.evaluation.Evaluation;
import com.wornux.data.entities.evaluation.EvaluationAssignment;
import com.wornux.data.entities.grounding.GroundingChunk;
import com.wornux.data.entities.grounding.GroundingCollection;
import com.wornux.data.entities.grounding.GroundingDocument;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;

import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class UC001JpaSchemaValidationTest extends UC001MigrationTestSupport {

    @Test
    void mainFlow_jpaMappingsValidateAgainstFlywaySchema() {
        assertDoesNotThrow(() -> {
            ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySettings(Map.of(
                            AvailableSettings.JAKARTA_JDBC_URL, POSTGRES.getJdbcUrl(),
                            AvailableSettings.JAKARTA_JDBC_USER, POSTGRES.getUsername(),
                            AvailableSettings.JAKARTA_JDBC_PASSWORD, POSTGRES.getPassword(),
                            AvailableSettings.HBM2DDL_AUTO, "validate",
                            AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect",
                            AvailableSettings.SHOW_SQL, false,
                            Environment.FORMAT_SQL, false))
                    .build();

            try (SessionFactory sessionFactory = metadataSources(serviceRegistry).buildMetadata().buildSessionFactory()) {
                sessionFactory.inTransaction(session -> {});
            }
            finally {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
        });
    }

    private MetadataSources metadataSources(ServiceRegistry serviceRegistry) {
        return new MetadataSources(serviceRegistry)
                .addAnnotatedClass(Account.class)
                .addAnnotatedClass(Tenant.class)
                .addAnnotatedClass(TenantAccount.class)
                .addAnnotatedClass(Role.class)
                .addAnnotatedClass(Resource.class)
                .addAnnotatedClass(Action.class)
                .addAnnotatedClass(Permission.class)
                .addAnnotatedClass(RolePermission.class)
                .addAnnotatedClass(TenantAccountRole.class)
                .addAnnotatedClass(Subject.class)
                .addAnnotatedClass(AcademicPeriod.class)
                .addAnnotatedClass(GroupClass.class)
                .addAnnotatedClass(GroupClassMember.class)
                .addAnnotatedClass(GroupClassJoinCode.class)
                .addAnnotatedClass(Conversation.class)
                .addAnnotatedClass(ConversationSnapshot.class)
                .addAnnotatedClass(GroundingCollection.class)
                .addAnnotatedClass(GroundingDocument.class)
                .addAnnotatedClass(GroundingChunk.class)
                .addAnnotatedClass(Evaluation.class)
                .addAnnotatedClass(EvaluationAssignment.class);
    }
}
