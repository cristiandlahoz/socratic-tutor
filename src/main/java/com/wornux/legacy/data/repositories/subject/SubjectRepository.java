package com.wornux.legacy.data.repositories.subject;

import java.util.Optional;

import com.wornux.legacy.data.entities.LegacySubject;
import com.wornux.data.enums.SubjectStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("legacySubjectRepository")
public interface SubjectRepository extends JpaRepository<LegacySubject, Long> {

    @EntityGraph(value = "Subject.withCurrentConfigRevision")
    Optional<LegacySubject> findBySlug(String slug);

    Optional<LegacySubject> findFirstByStatusOrderByCreatedAtAsc(SubjectStatus status);
}
