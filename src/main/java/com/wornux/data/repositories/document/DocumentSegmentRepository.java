package com.wornux.data.repositories.document;

import com.wornux.data.entities.*;
import com.wornux.data.enums.*;
import com.wornux.domain.document.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, UUID> {

  List<DocumentSegment> findByDocument_IdOrderByOrdinalAsc(UUID documentId);

  void deleteByDocument_Id(UUID documentId);
}
