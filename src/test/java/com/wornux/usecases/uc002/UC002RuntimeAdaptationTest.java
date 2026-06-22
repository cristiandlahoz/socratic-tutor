package com.wornux.usecases.uc002;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.config.ChatProperties;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.conversation.Conversation;
import com.wornux.data.entities.conversation.ConversationSnapshot;
import com.wornux.data.entities.evaluation.Evaluation;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.conversation.ConversationRepository;
import com.wornux.data.repositories.conversation.ConversationSnapshotRepository;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.chat.ChatCompactionService;
import com.wornux.services.chat.ChatUsageService;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.document.DocumentRetrievalService;
import com.wornux.services.evaluation.EvaluationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ai.docling.serve.api.DoclingServeApi;

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
    private EvaluationRepository evaluationRepository;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EntityManager entityManager;

    @Mock
    private com.wornux.data.repositories.grounding.GroundingChunkRepository groundingChunkRepository;

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
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var summary = service.createConversation(UUID.randomUUID(), "How do I analyze this algorithm?");

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

        assertEquals(List.of(), service.listConversations(UUID.randomUUID()));
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void af10_chatUsageRejectsConversationOutsideActiveOwnership() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var usageService = new ChatUsageService(conversationService, chatProperties());

        assertThrows(SecurityException.class, () -> usageService.updateActiveTranscriptInputTokens(UUID.randomUUID(), 120));
    }

    @Test
    void af10_postgresChatMemoryRejectsConversationOutsideActiveOwnership() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var memory = new com.wornux.ai.memory.PostgresChatMemory(snapshotRepository, conversationService);

        assertThrows(SecurityException.class,
                () -> memory.add(UUID.randomUUID().toString(), List.of(new UserMessage("hi"))));
    }

    @Test
    void af10_chatCompactionSkipsUnauthorizedConversationWithoutTouchingModel() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(context));
        when(conversationRepository.findByIdAndGroupClassMember_Id(any(), any())).thenReturn(Optional.empty());

        var conversationService = new ConversationService(conversationRepository, snapshotRepository, contextResolver);
        var usageService = new ChatUsageService(conversationService, chatProperties());
        var compactionService = new ChatCompactionService(conversationService, snapshotRepository, usageService, chatModel);

        assertFalse(compactionService.compactIfNeeded(UUID.randomUUID()).compacted());
        verifyNoInteractions(chatModel, snapshotRepository);
    }

    @Test
    void br06_activeAcademicContextResolverRejectsMismatchedLastLinks() {
        var resolver = new ActiveAcademicContextResolver(accountRepository, tenantAccountRepository, groupClassMemberRepository);
        var account = linkedAccount();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(account.getEmail(), "ignored", List.of()));
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(tenantAccountRepository.findByIdAndAccount_Id(account.getLastTenantAccount().getId(), account.getId()))
                .thenReturn(Optional.of(account.getLastTenantAccount()));
        when(groupClassMemberRepository.findByIdAndTenantAccount_Id(account.getLastGroupClassMember().getId(),
                account.getLastTenantAccount().getId())).thenReturn(Optional.empty());

        assertTrue(resolver.resolveCurrent().isEmpty());
    }

    @Test
    void br16_documentRetrievalRequiresGroupClassContext() {
        var properties = new DocumentIngestionProperties();
        var service = new DocumentRetrievalService(embeddingModel, entityManager, properties);

        var result = service.search(null, "binary search", null, null, null, null);

        assertFalse(result.contextFound());
        assertTrue(result.hits().isEmpty());
        verifyNoInteractions(embeddingModel, entityManager);
    }

    @Test
    void mainFlow_professorContextCreatesTargetEvaluationDefinition() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.PROFESSOR);
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(evaluationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new EvaluationService(evaluationRepository, contextResolver);

        Evaluation evaluation = service.createPending("Quiz 1", "Assess algorithm tracing");

        assertEquals("Quiz 1", evaluation.getTitle());
        assertEquals(context.groupClassId(), evaluation.getGroupClass().getId());
        assertEquals(context.groupClassMemberId(), evaluation.getCreatedByGroupClassMember().getId());
    }

    @Test
    void br23_studentContextCannotCreateEvaluationDefinition() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), GroupClassMemberRole.STUDENT);
        when(contextResolver.requireCurrent()).thenReturn(context);
        var service = new EvaluationService(evaluationRepository, contextResolver);

        assertThrows(SetupRequiredException.class, () -> service.createPending("Quiz 1", "Assess tracing"));
        verifyNoInteractions(evaluationRepository);
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
        new ApplicationContextRunner()
                .withUserConfiguration(DocumentWiringTestConfiguration.class)
                .withBean(EmbeddingModel.class, () -> embeddingModel)
                .withBean(EntityManager.class, () -> entityManager)
                .withBean(com.wornux.data.repositories.grounding.GroundingChunkRepository.class, () -> groundingChunkRepository)
                .withBean(DoclingServeApi.class, () -> doclingServeApi)
                .run(context -> {
                    assertNotNull(context.getBean(com.wornux.infrastructure.external.docling.DoclingClientService.class));
                    assertNotNull(context.getBean(com.wornux.services.document.DocumentEmbeddingService.class));
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
        com.wornux.services.document.DocumentEmbeddingService documentEmbeddingService(
                EmbeddingModel embeddingModel,
                com.wornux.data.repositories.grounding.GroundingChunkRepository groundingChunkRepository) {
            return new com.wornux.services.document.DocumentEmbeddingService(embeddingModel, groundingChunkRepository);
        }

        @Bean
        com.wornux.services.document.DocumentRetrievalService documentRetrievalService(
                EmbeddingModel embeddingModel,
                EntityManager entityManager,
                DocumentIngestionProperties properties) {
            return new com.wornux.services.document.DocumentRetrievalService(embeddingModel, entityManager, properties);
        }
    }
}
