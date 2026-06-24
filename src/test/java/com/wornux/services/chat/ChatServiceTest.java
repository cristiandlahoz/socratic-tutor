package com.wornux.services.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.wornux.ai.tools.ToolContextKeys;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.services.context.ActiveAcademicContext;
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

        var context = new ActiveAcademicContext(accountId,
                tenantAccountId,
                groupClassMemberId,
                groupClassId,
                GroupClassMemberRole.PROFESSOR);

        var toolContext = ChatService.buildToolContext(Optional.of(context), conversationId, turnId);

        assertThat(toolContext).hasSize(4)
                .containsEntry(ToolContextKeys.GROUP_CLASS_MEMBER_ID, groupClassMemberId.toString())
                .containsEntry(ToolContextKeys.GROUP_CLASS_ID, groupClassId.toString())
                .containsEntry(ToolContextKeys.CONVERSATION_ID, conversationId)
                .containsEntry(ToolContextKeys.TURN_ID, turnId);
    }
}
