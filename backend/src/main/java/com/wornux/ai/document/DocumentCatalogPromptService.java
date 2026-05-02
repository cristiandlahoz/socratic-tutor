package com.wornux.ai.document;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentCatalogPromptService {

  private static final Logger log = LoggerFactory.getLogger(DocumentCatalogPromptService.class);

  private final DocumentJpaRepository documentRepository;
  private final DocumentIngestionProperties ingestionProperties;

  public DocumentCatalogPromptService(
      DocumentJpaRepository documentRepository, DocumentIngestionProperties ingestionProperties) {
    this.documentRepository = documentRepository;
    this.ingestionProperties = ingestionProperties;
  }

  public String buildInventoryPrompt(UUID clientId) {
    if (clientId == null) {
      return "";
    }
    var inventory = ingestionProperties.getInventory();
    List<DocumentEntity> documents =
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
    for (DocumentEntity document : documents) {
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

  private String formatLine(DocumentEntity document) {
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
