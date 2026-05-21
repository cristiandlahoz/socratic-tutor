package com.wornux.infrastructure.persistence.profile;

import com.wornux.domain.profile.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileJpaRepository extends JpaRepository<StudentProfileEntity, UUID> {}
