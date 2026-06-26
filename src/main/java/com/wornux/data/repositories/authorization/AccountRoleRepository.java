package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.AccountRole;
import com.wornux.data.entities.authorization.AccountRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {

    @EntityGraph(attributePaths = {"role", "account"})
    List<AccountRole> findByAccount_IdAndRole_ActiveTrue(UUID accountId);

    Optional<AccountRole> findByAccount_IdAndRole_CodeAndRole_ActiveTrue(UUID accountId, String roleCode);
}
