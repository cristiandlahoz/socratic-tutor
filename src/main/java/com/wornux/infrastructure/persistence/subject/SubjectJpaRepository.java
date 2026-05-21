package com.wornux.infrastructure.persistence.subject;

import com.wornux.domain.subject.SubjectEntity;
import com.wornux.domain.subject.SubjectStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectJpaRepository extends JpaRepository<SubjectEntity, UUID> {

  @EntityGraph(value = "Subject.withCurrentConfigRevision")
  Optional<SubjectEntity> findBySlug(String slug);

  Optional<SubjectEntity> findFirstByStatusOrderByCreatedAtAsc(SubjectStatus status);
}
