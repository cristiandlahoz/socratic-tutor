package com.wornux.data.repositories.subject;

import java.util.Optional;

import com.wornux.data.entities.Subject;
import com.wornux.data.entities.SubjectConfigRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectConfigRevisionRepository extends JpaRepository<SubjectConfigRevision, Long> {

    Optional<SubjectConfigRevision> findBySubjectAndVersion(Subject subject, long version);

    Optional<SubjectConfigRevision> findFirstBySubjectOrderByVersionDesc(Subject subject);
}
