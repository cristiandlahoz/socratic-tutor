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

    public static final String SEARCH_COURSE_MATERIAL = "searchCourseMaterial";
    public static final String READ_COURSE_MATERIAL_PAGE = "readCourseMaterialPage";

    private final DocumentRetrievalService documentRetrievalService;
    private final ToolUsageAuditService toolUsageAuditService;

    public RetrieveInformationTool(
            DocumentRetrievalService documentRetrievalService,
            ToolUsageAuditService toolUsageAuditService) {
        this.documentRetrievalService = documentRetrievalService;
        this.toolUsageAuditService = toolUsageAuditService;
    }

    @Tool(name = SEARCH_COURSE_MATERIAL,
            description = "Search stored course material for factual course context. Returns short previews and read cursors. Use "
                    + READ_COURSE_MATERIAL_PAGE + " when a preview is not enough to answer.")
    public DocumentContextResult searchCourseMaterial(
            @ToolParam(description = "The specific question or fact to search for in stored course material.") String query,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
            SEARCH_COURSE_MATERIAL,
            toolContext,
            "query_len=%d".formatted(query == null ? 0 : query.length()),
            () -> {
                var result = documentRetrievalService.search(groupClassId(toolContext), query);
                return new ToolUsageAuditService.ToolResult<>(result,
                        "hits=%d context_found=%s".formatted(result.hits().size(), result.contextFound()));
            });
    }

    @Tool(name = READ_COURSE_MATERIAL_PAGE,
            description = "Read stored course material from a cursor returned by " + SEARCH_COURSE_MATERIAL
                    + ". Use nextCursor or previousCursor to continue reading nearby chunks.")
    public DocumentPageResult readCourseMaterialPage(
            @ToolParam(description = "A readCursor, nextCursor, or previousCursor returned by a course material tool.") String cursor,
            @ToolParam(required = false,
                    description = "Number of chunks to read. Default is 1; maximum is 3.") Integer pageSize,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
            READ_COURSE_MATERIAL_PAGE,
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
