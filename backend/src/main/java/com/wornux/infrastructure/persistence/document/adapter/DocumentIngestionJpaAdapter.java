package com.wornux.infrastructure.persistence.document.adapter;

import com.wornux.application.document.port.DocumentIngestionPersistencePort;
import com.wornux.domain.document.DocumentEntity;
import com.wornux.domain.document.DocumentIngestionJobEntity;
import com.wornux.domain.document.DocumentSegmentEntity;
import com.wornux.infrastructure.persistence.document.DocumentIngestionJobJpaRepository;
import com.wornux.infrastructure.persistence.document.DocumentJpaRepository;
import com.wornux.infrastructure.persistence.document.DocumentSegmentJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionJpaAdapter implements DocumentIngestionPersistencePort {

  private final DocumentJpaRepository documentJpaRepository;
  private final DocumentSegmentJpaRepository documentSegmentJpaRepository;
  private final DocumentIngestionJobJpaRepository documentIngestionJobJpaRepository;

  public DocumentIngestionJpaAdapter(
      DocumentJpaRepository documentJpaRepository,
      DocumentSegmentJpaRepository documentSegmentJpaRepository,
      DocumentIngestionJobJpaRepository documentIngestionJobJpaRepository) {
    this.documentJpaRepository = documentJpaRepository;
    this.documentSegmentJpaRepository = documentSegmentJpaRepository;
    this.documentIngestionJobJpaRepository = documentIngestionJobJpaRepository;
  }

  @Override
  public DocumentEntity saveDocument(DocumentEntity documentEntity) {
    return documentJpaRepository.save(documentEntity);
  }

  @Override
  public Optional<DocumentEntity> findDocumentByIdAndClientId(UUID documentId, UUID clientId) {
    return documentJpaRepository.findByIdAndClientId(documentId, clientId);
  }

  @Override
  public Optional<DocumentEntity> findLatestDocumentByClientId(UUID clientId) {
    return documentJpaRepository.findFirstByClientIdOrderByUpdatedAtDesc(clientId);
  }

  @Override
  public DocumentIngestionJobEntity saveJob(DocumentIngestionJobEntity jobEntity) {
    return documentIngestionJobJpaRepository.save(jobEntity);
  }

  @Override
  public Optional<DocumentIngestionJobEntity> findLatestJobByDocumentId(UUID documentId) {
    return documentIngestionJobJpaRepository.findFirstByDocument_IdOrderByStartedAtDesc(documentId);
  }

  @Override
  public void deleteSegmentsByDocumentId(UUID documentId) {
    documentSegmentJpaRepository.deleteByDocument_Id(documentId);
  }

  @Override
  public List<DocumentSegmentEntity> saveAllSegments(Collection<DocumentSegmentEntity> segmentEntities) {
    return documentSegmentJpaRepository.saveAll(segmentEntities);
  }

  @Override
  public List<DocumentSegmentEntity> findSegmentsByDocumentIdOrderByOrdinalAsc(UUID documentId) {
    return documentSegmentJpaRepository.findByDocument_IdOrderByOrdinalAsc(documentId);
  }
}
