package com.wornux.data.repositories.identity;

import java.util.UUID;

import com.wornux.data.entities.identity.AccountContextPreference;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountContextPreferenceRepository extends JpaRepository<AccountContextPreference, UUID> {
    @Override
    @EntityGraph(attributePaths = { "tenant", "groupClass", "groupClass.tenant" })
    java.util.Optional<AccountContextPreference> findById(UUID accountId);
}
