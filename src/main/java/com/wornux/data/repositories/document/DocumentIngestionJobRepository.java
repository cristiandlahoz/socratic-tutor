package com.wornux.data.repositories.document;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobRepository extends JpaRepository<DocumentIngestionJob, Long> {

    Optional<DocumentIngestionJob> findFirstByDocument_IdOrderByStartedAtDesc(UUID documentId);
}
