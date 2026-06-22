package com.wornux.data.repositories.authorization;

import java.util.Optional;

import com.wornux.data.entities.authorization.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}
