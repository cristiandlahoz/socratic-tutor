package com.wornux.data.repositories.document;

import com.wornux.data.entities.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobRepository
    extends JpaRepository<DocumentIngestionJob, UUID> {

  Optional<DocumentIngestionJob> findFirstByDocument_IdOrderByStartedAtDesc(UUID documentId);
}
