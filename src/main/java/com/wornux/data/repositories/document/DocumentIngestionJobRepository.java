package com.wornux.data.repositories.document;

import com.wornux.data.entities.*;
import com.wornux.data.enums.*;
import com.wornux.domain.document.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobRepository
    extends JpaRepository<DocumentIngestionJob, UUID> {

  Optional<DocumentIngestionJob> findFirstByDocument_IdOrderByStartedAtDesc(UUID documentId);
}
