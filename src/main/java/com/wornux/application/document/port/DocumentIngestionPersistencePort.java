package com.wornux.application.document.port;

import com.wornux.domain.document.DocumentEntity;
import com.wornux.domain.document.DocumentIngestionJobEntity;
import com.wornux.domain.document.DocumentSegmentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentIngestionPersistencePort {
  DocumentEntity saveDocument(DocumentEntity documentEntity);

  Optional<DocumentEntity> findDocumentByIdAndClientId(UUID documentId, UUID clientId);

  Optional<DocumentEntity> findLatestDocumentByClientId(UUID clientId);

  DocumentIngestionJobEntity saveJob(DocumentIngestionJobEntity jobEntity);

  Optional<DocumentIngestionJobEntity> findLatestJobByDocumentId(UUID documentId);

  void deleteSegmentsByDocumentId(UUID documentId);

  List<DocumentSegmentEntity> saveAllSegments(Collection<DocumentSegmentEntity> segmentEntities);

  List<DocumentSegmentEntity> findSegmentsByDocumentIdOrderByOrdinalAsc(UUID documentId);
}
