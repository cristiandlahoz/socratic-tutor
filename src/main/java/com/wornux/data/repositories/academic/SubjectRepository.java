package com.wornux.data.repositories.academic;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import com.wornux.data.entities.academic.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("academicSubjectRepository")
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    Optional<Subject> findByTenant_IdAndCode(UUID tenantId, String code);

    List<Subject> findByTenant_IdOrderByCodeAsc(UUID tenantId);
}
