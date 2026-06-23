package com.wornux.services.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.ai.tools.ToolUsageAuditService;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.services.context.ActiveAcademicContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void buildToolContextUsesDistinctKeysAndExpectedAcademicIds() {
        var accountId = UUID.randomUUID();
        var tenantAccountId = UUID.randomUUID();
        var groupClassMemberId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var conversationId = UUID.randomUUID();
        var turnId = UUID.randomUUID();

        var context = new ActiveAcademicContext(
                accountId,
                tenantAccountId,
                groupClassMemberId,
                groupClassId,
                GroupClassMemberRole.PROFESSOR);

        var toolContext = ChatService.buildToolContext(Optional.of(context), conversationId, turnId);

        assertThat(toolContext)
                .hasSize(4)
                .containsEntry(ToolUsageAuditService.CLIENT_ID, groupClassMemberId.toString())
                .containsEntry(ToolUsageAuditService.GROUP_CLASS_ID, groupClassId.toString())
                .containsEntry(ToolUsageAuditService.CONVERSATION_ID, conversationId)
                .containsEntry(ToolUsageAuditService.TURN_ID, turnId);
    }
}
