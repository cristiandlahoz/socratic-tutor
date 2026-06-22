package com.wornux.legacy.data.repositories.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndClientId(UUID id, UUID clientId);

    Optional<Document> findFirstByClientIdOrderByUpdatedAtDesc(UUID clientId);

    List<Document> findByClientIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(UUID clientId, String status);
}
