package com.wornux.infrastructure.persistence.subject;

import com.wornux.domain.subject.SubjectConfigRevisionEntity;
import com.wornux.domain.subject.SubjectEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectConfigRevisionJpaRepository
    extends JpaRepository<SubjectConfigRevisionEntity, UUID> {

  Optional<SubjectConfigRevisionEntity> findBySubjectAndVersion(SubjectEntity subject, long version);

  Optional<SubjectConfigRevisionEntity> findFirstBySubjectOrderByVersionDesc(SubjectEntity subject);
}
