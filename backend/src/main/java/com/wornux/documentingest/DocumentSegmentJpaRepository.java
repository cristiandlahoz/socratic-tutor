package com.wornux.documentingest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSegmentJpaRepository extends JpaRepository<DocumentSegmentEntity, UUID> {

  List<DocumentSegmentEntity> findByDocument_IdOrderByOrdinalAsc(UUID documentId);

  void deleteByDocument_Id(UUID documentId);
}
