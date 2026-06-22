package com.wornux.services.document;

import java.util.List;

import com.wornux.data.entities.grounding.GroundingChunk;
import com.wornux.data.entities.grounding.GroundingDocument;
import com.wornux.data.repositories.grounding.GroundingChunkRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final GroundingChunkRepository groundingChunkRepository;

    public DocumentEmbeddingService(EmbeddingModel embeddingModel, GroundingChunkRepository groundingChunkRepository) {
        this.embeddingModel = embeddingModel;
        this.groundingChunkRepository = groundingChunkRepository;
    }

    public void reindex(GroundingDocument document, List<GroundingChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        var texts = chunks.stream().map(GroundingChunk::getContent).toList();
        var embeddings = embeddingModel.embed(texts);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(embeddings.get(i));
        }
        groundingChunkRepository.saveAll(chunks);
    }

    public void deleteSegments(List<GroundingChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        chunks.forEach(chunk -> chunk.setEmbedding(null));
        groundingChunkRepository.saveAll(chunks);
    }
}
