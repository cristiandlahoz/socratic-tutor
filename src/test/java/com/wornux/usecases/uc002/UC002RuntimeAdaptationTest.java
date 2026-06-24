package com.wornux.usecases.uc002;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import ai.docling.serve.api.DoclingServeApi;
import com.wornux.config.ChatProperties;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.conversation.ConversationRepository;
import com.wornux.data.repositories.conversation.ConversationSnapshotRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.chat.ChatCompactionService;
import com.wornux.services.chat.ChatUsageService;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.document.DocumentRetrievalService;
import com.wornux.services.document.DocumentVectorIndexingService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UC002RuntimeAdaptationTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationSnapshotRepository snapshotRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TenantAccountRepository tenantAccountRepository;

    @Mock
    private GroupClassMemberRepository groupClassMemberRepository;

    @Mock
    private TrainingActivityRepository trainingActivityRepository;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private DoclingServeApi doclingServeApi;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mainFlow_conversationCreationUsesValidatedGroupClassMemberOwnership() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var summary = service.createConversation("How do I analyze this algorithm?");

        assertNotNull(summary.id());
        var captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals(context.groupClassMemberId(), captor.getValue().getGroupClassMember().getId());
    }

    @Test
    void af1_missingActiveAcademicContextReturnsEmptyConversationList() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.empty());
        var service = new ConversationService(conversationRepository, snapshotRepository, contextResolver);

        assertEquals(List.of(), service.listConversations());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void af10_chatUsageRejectsConversationOutsideActiveOwnership() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var usageService = new ChatUsageService(conversationService, chatProperties());

        assertThrows(
            SecurityException.class,
            () -> usageService.updateActiveTranscriptInputTokens(UUID.randomUUID(), 120));
    }

    @Test
    void af10_postgresChatMemoryRejectsConversationOutsideActiveOwnership() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var memory = new com.wornux.ai.memory.PostgresChatMemory(snapshotRepository, conversationService);

        assertThrows(
            SecurityException.class,
            () -> memory.add(UUID.randomUUID().toString(), List.of(new UserMessage("hi"))));
    }

    @Test
    void af10_chatCompactionSkipsUnauthorizedConversationWithoutTouchingModel() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var usageService = new ChatUsageService(conversationService, chatProperties());
        var compactionService =
                new ChatCompactionService(conversationService, snapshotRepository, usageService, chatModel);

        assertFalse(compactionService.compactIfNeeded(UUID.randomUUID()).compacted());
        verifyNoInteractions(chatModel, snapshotRepository);
    }

    @Test
    void br06_activeAcademicContextResolverRejectsMismatchedLastLinks() {
        var resolver = new ActiveAcademicContextResolver(accountRepository,
                tenantAccountRepository,
                groupClassMemberRepository);
        var account = linkedAccount();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(account.getEmail(), "ignored", List.of()));
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(tenantAccountRepository.findByIdAndAccount_Id(account.getLastTenantAccount().getId(), account.getId()))
                .thenReturn(Optional.of(account.getLastTenantAccount()));
        when(
            groupClassMemberRepository.findByIdAndTenantAccount_Id(
                account.getLastGroupClassMember().getId(),
                account.getLastTenantAccount().getId())).thenReturn(Optional.empty());

        assertTrue(resolver.resolveCurrent().isEmpty());
    }

    @Test
    void br16_documentRetrievalRequiresGroupClassContext() {
        var properties = new DocumentIngestionProperties();
        var service = new DocumentRetrievalService(vectorStore, properties);

        var result = service.search(null, "binary search", null, null, null, null);

        assertFalse(result.contextFound());
        assertTrue(result.hits().isEmpty());
        verifyNoInteractions(vectorStore);
    }

    @Test
    void br16_documentRetrievalUsesVectorStoreMetadataFilter() {
        var groupClassId = UUID.randomUUID();
        var properties = new DocumentIngestionProperties();
        var document = Document.builder()
                .id(UUID.randomUUID().toString())
                .text("Binary search halves the remaining interval.")
                .metadata(
                    Map.of("ingestionId", UUID.randomUUID().toString(), "title", "algorithms.pdf", "chunkIndex", 2))
                .score(0.91)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
        var service = new DocumentRetrievalService(vectorStore, properties);

        var result = service.search(groupClassId, "binary search", null, "algorithms.pdf", null, null);

        assertTrue(result.contextFound());
        assertEquals(document.getId(), result.hits().getFirst().segmentId());
        var request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertTrue(request.getValue().getFilterExpression().toString().contains("groupClassId"));
        assertTrue(request.getValue().getFilterExpression().toString().contains("status"));
        assertTrue(request.getValue().getFilterExpression().toString().contains("title"));
    }

    @Test
    void br43_documentIndexingDelegatesEmbeddingsAndDeletionToVectorStore() {
        var groupClassId = UUID.randomUUID();
        var professorMemberId = UUID.randomUUID();
        var ingestionId = UUID.randomUUID();
        var segment = new EditableSegmentViewModel(UUID.randomUUID().toString(),
                3,
                "Algorithms",
                "Binary search halves the remaining interval.",
                true,
                false,
                45,
                7,
                2,
                List.of(2),
                List.of(),
                List.of(),
                "Binary search halves the remaining interval.",
                "docling");
        var service = new DocumentVectorIndexingService(vectorStore);

        var ids = service.index(groupClassId, professorMemberId, ingestionId, "algorithms.pdf", List.of(segment));

        var documents = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documents.capture());
        var indexed = (Document) documents.getValue().getFirst();
        assertEquals(ids.getFirst(), indexed.getId());
        assertEquals(segment.content(), indexed.getText());
        assertEquals(groupClassId.toString(), indexed.getMetadata().get("groupClassId"));
        assertEquals(professorMemberId.toString(), indexed.getMetadata().get("createdByGroupClassMemberId"));
        assertEquals(ingestionId.toString(), indexed.getMetadata().get("ingestionId"));
        assertEquals("READY", indexed.getMetadata().get("status"));
        assertEquals(3, indexed.getMetadata().get("chunkIndex"));

        service.delete(ids);
        verify(vectorStore).delete(ids);
    }

    @Test
    void mainFlow_professorContextCreatesTargetEvaluationDefinition() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.PROFESSOR);
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(trainingActivityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new TrainingActivityService(trainingActivityRepository, contextResolver);

        TrainingActivity activity = service.createPending("Quiz 1", "Assess algorithm tracing");

        assertEquals("Quiz 1", activity.getTitle());
        assertEquals(context.groupClassId(), activity.getGroupClass().getId());
        assertEquals(context.groupClassMemberId(), activity.getCreatedByGroupClassMember().getId());
    }

    @Test
    void br23_studentContextCannotCreateEvaluationDefinition() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID
                .randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.requireCurrent()).thenReturn(context);
        var service = new TrainingActivityService(trainingActivityRepository, contextResolver);

        assertThrows(SetupRequiredException.class, () -> service.createPending("Quiz 1", "Assess tracing"));
        verifyNoInteractions(trainingActivityRepository);
    }

    @Test
    void br26_legacyEvaluationRunPathIsNoLongerInActivePackages() {
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/services/evaluation/EvaluationRunService.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/services/evaluation/EvaluationChatService.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/services/evaluation/EvaluationMemory.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/wornux/ui/evaluation/EvaluationChatView.java")));
    }

    @Test
    void br43_activeVectorStoreAndDoclingWiringLoadsWhenDependenciesArePresent() {
        new ApplicationContextRunner().withUserConfiguration(DocumentWiringTestConfiguration.class)
                .withBean(VectorStore.class, () -> vectorStore)
                .withBean(DoclingServeApi.class, () -> doclingServeApi)
                .run(context -> {
                    assertNotNull(
                        context.getBean(com.wornux.infrastructure.external.docling.DoclingClientService.class));
                    assertNotNull(context.getBean(com.wornux.services.document.DocumentVectorIndexingService.class));
                    assertNotNull(context.getBean(com.wornux.services.document.DocumentRetrievalService.class));
                });
    }

    private static ChatProperties chatProperties() {
        var properties = new ChatProperties();
        properties.setContextWindowTokens(1000);
        properties.setCompactionThresholdRatio(0.5);
        return properties;
    }

    private static Account linkedAccount() {
        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        var account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("student@test.local");

        var tenantAccount = new TenantAccount();
        tenantAccount.setId(UUID.randomUUID());
        tenantAccount.setAccount(account);
        tenantAccount.setTenant(tenant);
        tenantAccount.setLocked(false);

        var groupClass = new GroupClass();
        groupClass.setId(UUID.randomUUID());
        groupClass.setTenant(tenant);

        var groupClassMember = new GroupClassMember();
        groupClassMember.setId(UUID.randomUUID());
        groupClassMember.setTenantAccount(tenantAccount);
        groupClassMember.setGroupClass(groupClass);
        groupClassMember.setRole(GroupClassMemberRole.STUDENT);
        groupClassMember.setLocked(false);
        groupClassMember.setJoinedAt(Instant.now());
        groupClassMember.setUpdatedAt(Instant.now());

        account.setLastTenantAccount(tenantAccount);
        account.setLastGroupClassMember(groupClassMember);
        return account;
    }

    @Configuration
    static class DocumentWiringTestConfiguration {

        @Bean
        DocumentIngestionProperties documentIngestionProperties() {
            return new DocumentIngestionProperties();
        }

        @Bean
        com.wornux.infrastructure.external.docling.DoclingClientService doclingClientService(
                DoclingServeApi doclingServeApi,
                DocumentIngestionProperties properties) {
            return new com.wornux.infrastructure.external.docling.DoclingClientService(doclingServeApi, properties);
        }

        @Bean
        com.wornux.services.document.DocumentVectorIndexingService documentVectorIndexingService(
                VectorStore vectorStore) {
            return new com.wornux.services.document.DocumentVectorIndexingService(vectorStore);
        }

        @Bean
        com.wornux.services.document.DocumentRetrievalService documentRetrievalService(
                VectorStore vectorStore,
                DocumentIngestionProperties properties) {
            return new com.wornux.services.document.DocumentRetrievalService(vectorStore, properties);
        }
    }
}
