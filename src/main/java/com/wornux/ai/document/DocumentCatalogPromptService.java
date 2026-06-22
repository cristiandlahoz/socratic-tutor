package com.wornux.ai.document;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.grounding.GroundingDocumentStatus;
import com.wornux.data.repositories.grounding.GroundingDocumentRepository;
import org.springframework.stereotype.Service;

@Service
public class DocumentCatalogPromptService {

    private final GroundingDocumentRepository groundingDocumentRepository;

    public DocumentCatalogPromptService(GroundingDocumentRepository groundingDocumentRepository) {
        this.groundingDocumentRepository = groundingDocumentRepository;
    }

    public String buildInventoryPrompt(UUID groupClassId) {
        if (groupClassId == null) {
            return "";
        }

        List<com.wornux.data.entities.grounding.GroundingDocument> documents = groundingDocumentRepository
                .findByCollection_GroupClass_IdAndStatusOrderByUpdatedAtDescCreatedAtDesc(groupClassId, GroundingDocumentStatus.READY);
        if (documents.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(
                "Indexed grounding documents available for the active class context:\n");
        documents.stream().limit(10).forEach(document -> builder.append("- ")
                .append(document.getTitle())
                .append(" | id: ")
                .append(document.getId())
                .append('\n'));
        return builder.toString().trim();
    }
}
