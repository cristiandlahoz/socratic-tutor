package com.wornux.chat.profile;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileJpaRepository extends JpaRepository<StudentProfileEntity, UUID> {}
