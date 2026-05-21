package com.wornux.infrastructure.persistence.document;

import com.wornux.domain.document.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

  Optional<DocumentEntity> findByIdAndClientId(UUID id, UUID clientId);

  Optional<DocumentEntity> findFirstByClientIdOrderByUpdatedAtDesc(UUID clientId);

  List<DocumentEntity> findByClientIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
      UUID clientId, String status);
}
