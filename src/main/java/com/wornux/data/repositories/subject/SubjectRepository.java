package com.wornux.data.repositories.subject;

import java.util.Optional;

import com.wornux.data.entities.Subject;
import com.wornux.data.enums.SubjectStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @EntityGraph(value = "Subject.withCurrentConfigRevision")
    Optional<Subject> findBySlug(String slug);

    Optional<Subject> findFirstByStatusOrderByCreatedAtAsc(SubjectStatus status);
}
