package com.wornux.data.repositories.authorization;

import java.util.Optional;

import com.wornux.data.entities.authorization.Action;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, Long> {
    Optional<Action> findByCode(String code);
}
