package com.wornux.data.repositories.grounding;

import java.util.List;

import com.wornux.data.entities.grounding.GroundingCollection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroundingCollectionRepository extends JpaRepository<GroundingCollection, Long> {
    List<GroundingCollection> findByGroupClass_IdOrderByCreatedAtDesc(java.util.UUID groupClassId);
}
