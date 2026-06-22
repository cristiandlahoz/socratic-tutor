package com.wornux.data.repositories.authorization;

import java.util.Optional;

import com.wornux.data.entities.authorization.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    Optional<Resource> findByCode(String code);
}
