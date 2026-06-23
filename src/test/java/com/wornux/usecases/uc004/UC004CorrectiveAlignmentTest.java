package com.wornux.usecases.uc004;

import static org.junit.jupiter.api.Assertions.*;

import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.dtos.document.DocumentSearchHit;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UC004CorrectiveAlignmentTest {

    @Test
    void br14_br15_br16_conversationUsesSnapshotPointerCollectionAndLongSnapshotIds() throws Exception {
        var currentSnapshotField = Conversation.class.getDeclaredField("currentSnapshot");
        var snapshotsField = Conversation.class.getDeclaredField("snapshots");
        var snapshotConversationField = ConversationSnapshot.class.getDeclaredField("conversation");
        var snapshotIdField = ConversationSnapshot.class.getDeclaredField("id");

        assertNotNull(currentSnapshotField.getAnnotation(OneToOne.class));
        assertNotNull(snapshotsField.getAnnotation(OneToMany.class));
        assertEquals("conversation", snapshotsField.getAnnotation(OneToMany.class).mappedBy());
        assertNotNull(snapshotConversationField.getAnnotation(ManyToOne.class));
        assertEquals(Long.class, snapshotIdField.getType());
    }

    @Test
    void br17_br18_groundingSearchHitUsesLongIds() {
        assertEquals(Long.class, component(DocumentSearchHit.class, "segmentId").getType());
        assertEquals(Long.class, component(DocumentSearchHit.class, "documentId").getType());
    }

    @Test
    void br07_br08_br09_aiConfigKeepsCurrentGuardRoutingAndGroundingWiring() throws Exception {
        var source = Files.readString(Path.of("src/main/java/com/wornux/config/AIConfig.java"));

        assertTrue(source.contains("defaultSystem"));
        assertTrue(source.contains("defaultAdvisors"));
        assertTrue(source.contains("defaultTools"));
        assertTrue(source.contains("TutorGuardAdvisor"));
        assertTrue(source.contains("PedagogicalRoutingAdvisor"));
        assertTrue(source.contains("DocumentCatalogAdvisor"));
        assertTrue(source.contains("RetrieveInformationTool"));
        assertFalse(source.contains("SubjectContextAdvisor"));
        assertFalse(source.contains("ProfileAwareResponseAdvisor"));
    }

    @Test
    void br05_br06_br21_br22_runtimeConfigRemovesCookieIdentityAndLegacyPromptVocabulary() throws Exception {
        var applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        var baseIdentityPrompt = Files.readString(Path.of("src/main/resources/tutor/base-identity-system.st"));
        var guardClassifierPrompt = Files.readString(Path.of("src/main/resources/tutor/policies/guard-classifier.st"));
        var guardOutOfScopePrompt = Files.readString(Path.of("src/main/resources/tutor/policies/guard-out-of-scope.st"));
        var retrievalSource = Files.readString(Path.of("src/main/java/com/wornux/services/document/DocumentRetrievalService.java"));
        var applicationSource = Files.readString(Path.of("src/main/java/com/wornux/Application.java"));

        assertFalse(applicationYaml.contains("app:\n  browser:"));
        assertFalse(applicationYaml.contains("id-cookie-name"));
        assertFalse(baseIdentityPrompt.contains("legacySubject"));
        assertFalse(guardClassifierPrompt.contains("legacySubject"));
        assertFalse(guardOutOfScopePrompt.contains("legacySubject"));
        assertTrue(retrievalSource.contains("grounding_chunk"));
        assertTrue(retrievalSource.contains("CAST(:queryVec AS vector)"));
        assertTrue(applicationSource.contains("PgVectorStoreAutoConfiguration"));
        assertFalse(applicationYaml.contains("vector_store"));
    }

    @Test
    void br19_uiCopyUsesFormativeActivitiesLanguage() throws Exception {
        var evaluationView = Files.readString(Path.of("src/main/java/com/wornux/ui/evaluation/EvaluationView.java"));
        var evaluationDialog = Files.readString(Path.of("src/main/java/com/wornux/ui/evaluation/EvaluationDialog.java"));
        var professorWorkspaceView = Files.readString(Path.of("src/main/java/com/wornux/ui/professor/ProfessorWorkspaceView.java"));
        var studentWorkspaceView = Files.readString(Path.of("src/main/java/com/wornux/ui/student/StudentWorkspaceView.java"));

        assertTrue(evaluationView.contains("Formative Activities"));
        assertTrue(evaluationDialog.contains("Activity"));
        assertTrue(professorWorkspaceView.contains("Formative Activities"));
        assertTrue(studentWorkspaceView.contains("Assigned Activity"));
        assertFalse(evaluationView.contains("Evaluations"));
    }

    @Test
    void br05_br06_browserIdentityRemoved() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/config/BrowserIdentityProperties.java")),
                "BrowserIdentityProperties must not exist");
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/infrastructure/web/BrowserSessionService.java")),
                "BrowserSessionService must not exist because browser identity is not the academic identity model");

        var mainSources = Files.readString(Path.of("src/main/java/com/wornux/services/chat/ChatService.java"))
                + Files.readString(Path.of("src/main/java/com/wornux/ui/chat/ChatViewModel.java"))
                + Files.readString(Path.of("src/main/java/com/wornux/ui/chat/ChatTurnOrchestrator.java"))
                + Files.readString(Path.of("src/main/java/com/wornux/ui/ingestion/DocumentIngestionUiController.java"));
        assertFalse(mainSources.contains("BrowserSessionService"),
                "No active code should reference BrowserSessionService");
        assertFalse(mainSources.contains("browserSessionId"),
                "No active code should reference browserSessionId as academic identity");
    }

    private static RecordComponent component(Class<?> recordType, String name) {
        for (var component : recordType.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        throw new IllegalArgumentException("Missing record component: " + name);
    }
}
