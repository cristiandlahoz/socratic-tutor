package com.wornux.data.repositories.subject;

import com.wornux.data.entities.SubjectConfigRevision;
import com.wornux.data.entities.Subject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectConfigRevisionRepository
    extends JpaRepository<SubjectConfigRevision, UUID> {

  Optional<SubjectConfigRevision> findBySubjectAndVersion(Subject subject, long version);

  Optional<SubjectConfigRevision> findFirstBySubjectOrderByVersionDesc(Subject subject);
}
