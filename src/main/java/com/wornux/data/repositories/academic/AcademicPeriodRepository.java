package com.wornux.data.repositories.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.AcademicPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {
    Optional<AcademicPeriod> findByTenant_IdAndCode(UUID tenantId, String code);

    List<AcademicPeriod> findByTenant_IdOrderByStartsAtAsc(UUID tenantId);
}
