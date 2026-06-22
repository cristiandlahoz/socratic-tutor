package com.wornux.legacy.data.repositories.profile;

import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
