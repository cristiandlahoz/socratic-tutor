package com.wornux.data.repositories.profile;

import java.util.UUID;

import com.wornux.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
