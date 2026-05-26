package com.wornux.data.repositories.profile;

import com.wornux.data.entities.*;
import com.wornux.data.enums.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentMisconceptionRepository
    extends JpaRepository<StudentMisconception, Long> {

  List<StudentMisconception> findByClientIdOrderByLastSeenAtDesc(UUID clientId);

  Optional<StudentMisconception> findByClientIdAndMisconceptionKey(
      UUID clientId, String misconceptionKey);

  List<StudentMisconception> findByClientIdAndStatusNotAndLastSeenAtBefore(
      UUID clientId, MisconceptionStatus status, Instant cutoff);
}
