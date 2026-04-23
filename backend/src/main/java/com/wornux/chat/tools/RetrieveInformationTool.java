package com.wornux.chat.tools;

import com.wornux.documentingest.DocumentContextResult;
import com.wornux.documentingest.DocumentRetrievalService;
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

  @Tool(
      name = "searchUploadedDocuments",
      description =
          "Searches approved text segments extracted from PDFs uploaded by the current user. Use"
              + " this when the question refers to an uploaded document, report, PDF, or"
              + " document-specific facts.")
  public DocumentContextResult searchUploadedDocuments(
      @ToolParam(description = "The user question or the fact to look up inside uploaded PDFs.")
          String query,
      @ToolParam(
              required = false,
              description = "Optional exact uploaded filename to narrow the search.")
          String filenameHint,
      ToolContext toolContext) {
    return toolUsageAuditService.audit(
        "searchUploadedDocuments",
        toolContext,
        "query_len=%d filename_hint=%s"
            .formatted(
                query == null ? 0 : query.length(), filenameHint == null ? "none" : filenameHint),
        () -> {
          var clientId =
              java.util.UUID.fromString(
                  String.valueOf(toolContext.getContext().get(ToolUsageAuditService.CLIENT_ID)));
          var result = documentRetrievalService.search(clientId, query, filenameHint);
          return new ToolUsageAuditService.ToolResult<>(
              result,
              "hits=%d context_found=%s".formatted(result.hits().size(), result.contextFound()),
              new ToolLearningSignal("uploaded_documents", false, "retrieval_context"));
        });
  }
}
