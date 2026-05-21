package com.wornux.infrastructure.persistence.profile;

import com.wornux.domain.profile.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileSignalJpaRepository
    extends JpaRepository<StudentProfileSignalEntity, Long> {}
