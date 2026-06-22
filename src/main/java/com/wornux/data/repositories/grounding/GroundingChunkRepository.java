package com.wornux.data.repositories.grounding;

import java.util.List;

import com.wornux.data.entities.grounding.GroundingChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroundingChunkRepository extends JpaRepository<GroundingChunk, Long> {
    List<GroundingChunk> findByDocument_IdOrderByChunkIndexAsc(Long documentId);

    void deleteByDocument_Id(Long documentId);
}
