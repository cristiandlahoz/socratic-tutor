package com.wornux.data.repositories.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupClassRepository extends JpaRepository<GroupClass, UUID> {
    Optional<GroupClass> findByTenant_IdAndCode(UUID tenantId, String code);

    List<GroupClass> findByTenant_IdOrderByNameAsc(UUID tenantId);
}
