package com.wornux.data.repositories.identity;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @EntityGraph(attributePaths = {
            "lastTenantAccount",
            "lastTenantAccount.tenant",
            "lastGroupClassMember",
            "lastGroupClassMember.groupClass",
            "lastGroupClassMember.groupClass.tenant",
            "lastGroupClassMember.tenantAccount",
            "lastGroupClassMember.tenantAccount.tenant"
    })
    Optional<Account> findByEmail(String email);

    @EntityGraph(attributePaths = {
            "lastTenantAccount",
            "lastTenantAccount.tenant",
            "lastGroupClassMember",
            "lastGroupClassMember.groupClass",
            "lastGroupClassMember.groupClass.tenant",
            "lastGroupClassMember.tenantAccount",
            "lastGroupClassMember.tenantAccount.tenant"
    })
    Optional<Account> findByUsername(String username);

    @EntityGraph(attributePaths = {
            "lastTenantAccount",
            "lastTenantAccount.tenant",
            "lastGroupClassMember",
            "lastGroupClassMember.groupClass",
            "lastGroupClassMember.groupClass.tenant",
            "lastGroupClassMember.tenantAccount",
            "lastGroupClassMember.tenantAccount.tenant"
    })
    Optional<Account> findById(UUID id);

    boolean existsByUsername(String username);
}
