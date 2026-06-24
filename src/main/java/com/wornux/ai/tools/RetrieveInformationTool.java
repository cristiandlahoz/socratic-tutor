package com.wornux.ai.tools;

import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.services.document.DocumentRetrievalService;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RetrieveInformationTool {

    private final DocumentRetrievalService documentRetrievalService;
    private final ToolUsageAuditService toolUsageAuditService;

    public RetrieveInformationTool(
            DocumentRetrievalService documentRetrievalService,
            ToolUsageAuditService toolUsageAuditService) {
        this.documentRetrievalService = documentRetrievalService;
        this.toolUsageAuditService = toolUsageAuditService;
    }

    @Tool(name = "searchUploadedDocuments",
            description = """
                          Searches approved text segments extracted from PDFs uploaded by the current user.
                          The search is scoped to the active class. Use this when the user refers to uploaded
                          material, reports, PDFs, topics, entities, or document-specific facts.
                          """)
    public DocumentContextResult searchUploadedDocuments(
            @ToolParam(description = "The user question or the fact to look up inside uploaded PDFs.") String query,
            @ToolParam(required = false,
                    description = "Optional ingestion ID to narrow the search.") String ingestionIdHint,
            @ToolParam(required = false,
                    description = "Optional exact uploaded filename to narrow the search.") String filenameHint,
            @ToolParam(required = false, description = "Optional topic from the document inventory.") String topicHint,
            @ToolParam(required = false, description = "Optional tag from the document inventory.") String tagHint,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
            "searchUploadedDocuments",
            toolContext,
            "query_len=%d ingestion_id_hint=%s filename_hint=%s topic_hint=%s tag_hint=%s".formatted(
                query == null ? 0 : query.length(),
                ingestionIdHint == null ? "none" : ingestionIdHint,
                filenameHint == null ? "none" : filenameHint,
                topicHint == null ? "none" : topicHint,
                tagHint == null ? "none" : tagHint),
            () -> {
                var rawGroupClassId = toolContext.getContext().get(ToolContextKeys.GROUP_CLASS_ID);
                var groupClassId = rawGroupClassId == null || String.valueOf(rawGroupClassId).isBlank()
                        ? null
                        : java.util.UUID.fromString(String.valueOf(rawGroupClassId));
                var result = documentRetrievalService
                        .search(groupClassId, query, ingestionIdHint, filenameHint, topicHint, tagHint);
                return new ToolUsageAuditService.ToolResult<>(result,
                        "hits=%d context_found=%s".formatted(result.hits().size(), result.contextFound()));
            });
    }
}
