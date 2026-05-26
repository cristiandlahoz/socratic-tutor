package com.wornux.data.repositories.profile;

import com.wornux.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileSignalRepository
    extends JpaRepository<StudentProfileSignal, Long> {}
