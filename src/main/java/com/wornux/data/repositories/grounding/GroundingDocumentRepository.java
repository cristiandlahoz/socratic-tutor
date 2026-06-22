package com.wornux.data.repositories.grounding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.grounding.GroundingDocument;
import com.wornux.data.entities.grounding.GroundingDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroundingDocumentRepository extends JpaRepository<GroundingDocument, Long> {
    Optional<GroundingDocument> findByIdAndCollection_GroupClass_Id(Long documentId, UUID groupClassId);

    Optional<GroundingDocument> findFirstByCollection_GroupClass_IdOrderByUpdatedAtDescCreatedAtDesc(UUID groupClassId);

    List<GroundingDocument> findByCollection_GroupClass_IdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
            UUID groupClassId,
            GroundingDocumentStatus status);
}
