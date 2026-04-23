package com.wornux.documentingest;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

  Optional<DocumentEntity> findByIdAndClientId(UUID id, UUID clientId);

  Optional<DocumentEntity> findFirstByClientIdOrderByUpdatedAtDesc(UUID clientId);
}
