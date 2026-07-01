package com.wornux.ai.tools;

import java.util.UUID;

import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.dtos.document.DocumentPageResult;
import com.wornux.services.document.DocumentRetrievalService;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RetrieveInformationTool {

    public static final String RETRIEVE_INFORMATION = "retrieveInformation";
    public static final String READ_RETRIEVED_INFORMATION = "readRetrievedInformation";

    private final DocumentRetrievalService documentRetrievalService;
    private final ToolUsageAuditService toolUsageAuditService;

    public RetrieveInformationTool(
            DocumentRetrievalService documentRetrievalService,
            ToolUsageAuditService toolUsageAuditService) {
        this.documentRetrievalService = documentRetrievalService;
        this.toolUsageAuditService = toolUsageAuditService;
    }

    @Tool(name = RETRIEVE_INFORMATION,
            description = "Search approved uploaded PDFs for factual course context. Returns short previews and read cursors. Use "
                    + READ_RETRIEVED_INFORMATION + " when a preview is not enough to answer.")
    public DocumentContextResult retrieveInformation(
            @ToolParam(description = "The specific question or fact to search for in uploaded PDFs.") String query,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
            RETRIEVE_INFORMATION,
            toolContext,
            "query_len=%d".formatted(query == null ? 0 : query.length()),
            () -> {
                var result = documentRetrievalService.search(groupClassId(toolContext), query);
                return new ToolUsageAuditService.ToolResult<>(result,
                        "hits=%d context_found=%s".formatted(result.hits().size(), result.contextFound()));
            });
    }

    @Tool(name = READ_RETRIEVED_INFORMATION,
            description = "Read uploaded PDF content from a cursor returned by " + RETRIEVE_INFORMATION
                    + ". Use nextCursor or previousCursor to continue reading nearby chunks.")
    public DocumentPageResult readRetrievedInformation(
            @ToolParam(description = "A readCursor, nextCursor, or previousCursor returned by a document tool.") String cursor,
            @ToolParam(required = false,
                    description = "Number of chunks to read. Default is 1; maximum is 3.") Integer pageSize,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
            READ_RETRIEVED_INFORMATION,
            toolContext,
            "cursor_present=%s page_size=%s".formatted(cursor != null && !cursor.isBlank(), pageSize),
            () -> {
                var result = documentRetrievalService.readPage(groupClassId(toolContext), cursor, pageSize);
                return new ToolUsageAuditService.ToolResult<>(result,
                        "content_found=%s has_previous=%s has_next=%s".formatted(
                            !result.content().isBlank(), result.hasPrevious(), result.hasNext()));
            });
    }

    private UUID groupClassId(ToolContext toolContext) {
        var rawGroupClassId = toolContext.getContext().get(ToolContextKeys.GROUP_CLASS_ID);
        return rawGroupClassId == null || String.valueOf(rawGroupClassId).isBlank()
                ? null
                : UUID.fromString(String.valueOf(rawGroupClassId));
    }
}
