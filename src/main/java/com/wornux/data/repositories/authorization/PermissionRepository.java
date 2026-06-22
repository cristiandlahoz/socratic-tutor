package com.wornux.data.repositories.authorization;

import java.util.Optional;

import com.wornux.data.entities.authorization.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
