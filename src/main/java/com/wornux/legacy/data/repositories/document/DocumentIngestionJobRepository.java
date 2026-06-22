package com.wornux.legacy.data.repositories.document;

import java.util.Optional;
import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobRepository extends JpaRepository<DocumentIngestionJob, Long> {

    Optional<DocumentIngestionJob> findFirstByDocument_IdOrderByStartedAtDesc(UUID documentId);
}
