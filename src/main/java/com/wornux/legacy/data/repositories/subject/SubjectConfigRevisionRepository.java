package com.wornux.legacy.data.repositories.subject;

import java.util.Optional;

import com.wornux.legacy.data.entities.LegacySubject;
import com.wornux.legacy.data.entities.SubjectConfigRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectConfigRevisionRepository extends JpaRepository<SubjectConfigRevision, Long> {

    Optional<SubjectConfigRevision> findByLegacySubjectAndVersion(LegacySubject legacySubject, long version);

    Optional<SubjectConfigRevision> findFirstByLegacySubjectOrderByVersionDesc(LegacySubject legacySubject);
}
