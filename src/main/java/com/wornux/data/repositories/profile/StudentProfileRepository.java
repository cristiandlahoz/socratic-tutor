package com.wornux.data.repositories.profile;

import com.wornux.data.entities.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
