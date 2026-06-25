package com.wornux.data.repositories.identity;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @EntityGraph(attributePaths = {"lastTenantAccount", "lastGroupClassMember", "lastGroupClassMember.groupClass"})
    Optional<Account> findByEmail(String email);

    @EntityGraph(attributePaths = {"lastTenantAccount", "lastGroupClassMember", "lastGroupClassMember.groupClass"})
    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);
}
