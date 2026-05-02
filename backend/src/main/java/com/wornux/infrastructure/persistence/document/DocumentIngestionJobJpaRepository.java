package com.wornux.infrastructure.persistence.document;

import com.wornux.domain.document.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobJpaRepository
    extends JpaRepository<DocumentIngestionJobEntity, UUID> {

  Optional<DocumentIngestionJobEntity> findFirstByDocument_IdOrderByStartedAtDesc(UUID documentId);
}
