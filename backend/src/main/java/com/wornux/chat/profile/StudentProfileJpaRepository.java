package com.wornux.chat.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentProfileJpaRepository extends JpaRepository<StudentProfileEntity, UUID> {
}
