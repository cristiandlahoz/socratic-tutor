package com.wornux.ai.document;

import com.wornux.data.entities.*;
import com.wornux.data.enums.*;
import com.wornux.data.repositories.document.*;
import com.wornux.config.DocumentIngestionProperties;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentCatalogPromptService {

  private static final Logger log = LoggerFactory.getLogger(DocumentCatalogPromptService.class);

  private final DocumentRepository documentRepository;
  private final DocumentIngestionProperties ingestionProperties;

  public DocumentCatalogPromptService(
      DocumentRepository documentRepository, DocumentIngestionProperties ingestionProperties) {
    this.documentRepository = documentRepository;
    this.ingestionProperties = ingestionProperties;
  }

  public String buildInventoryPrompt(UUID clientId) {
    if (clientId == null) {
      return "";
    }
    var inventory = ingestionProperties.getInventory();
    List<Document> documents =
        documentRepository.findByClientIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
            clientId, DocumentStatus.INDEXED.name());
    if (documents.isEmpty()) {
      return "";
    }

    int total = documents.size();
    StringBuilder builder = new StringBuilder();
    builder.append(
        """
        Indexed uploaded documents available for this student:
        Use searchUploadedDocuments when the user question overlaps these filenames, topics, tags, entities, or example questions. The inventory is for deciding whether to search; retrieved passages are the source of truth.
        """);

    int included = 0;
    for (Document document : documents) {
      if (included >= inventory.getMaxDocuments()) {
        break;
      }
      String line = formatLine(document);
      if (builder.length() + line.length() > inventory.getMaxChars()) {
        break;
      }
      builder.append("- ").append(line).append('\n');
      included++;
    }
    if (included < total) {
      builder
          .append("- ... ")
          .append(total - included)
          .append(" more indexed document(s) omitted.\n");
    }
    log.debug(
        "document_inventory_prompt client_id={} indexed_documents={} included={} chars={}",
        clientId,
        total,
        included,
        builder.length());
    return builder.toString().trim();
  }

  private String formatLine(Document document) {
    var catalogTags =
        document.getCatalogTags() == null ? List.<String>of() : document.getCatalogTags();
    var questionExamples =
        document.getCatalogQuestionExamples() == null
            ? List.<String>of()
            : document.getCatalogQuestionExamples();
    String tags = catalogTags.isEmpty() ? "none" : String.join(", ", catalogTags);
    String examples =
        questionExamples.isEmpty()
            ? "none"
            : String.join("; ", questionExamples.stream().limit(2).toList());
    return "%s | %s | topic: %s | tags: %s | can answer: %s"
        .formatted(
            document.getOriginalFilename(),
            nonBlank(document.getCatalogTitle(), "Documento PDF"),
            nonBlank(document.getCatalogTopic(), "sin tema catalogado"),
            tags,
            examples);
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
