package com.wornux.legacy.data.repositories.document;

import java.util.List;
import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, Long> {

    List<DocumentSegment> findByDocument_IdOrderByOrdinalAsc(UUID documentId);

    void deleteByDocument_Id(UUID documentId);
}
