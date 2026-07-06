package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.authorization.AccountPlatformRole;
import com.wornux.data.entities.authorization.AccountPlatformRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountPlatformRoleRepository extends JpaRepository<AccountPlatformRole, AccountPlatformRoleId> {

    @EntityGraph(attributePaths = { "role", "account" })
    List<AccountPlatformRole> findByAccount_IdAndRole_ActiveTrue(UUID accountId);
}
