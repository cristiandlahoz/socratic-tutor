package com.wornux.chat.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentMisconceptionJpaRepository extends JpaRepository<StudentMisconceptionEntity, Long> {

    List<StudentMisconceptionEntity> findByClientIdOrderByLastSeenAtDesc(UUID clientId);

    Optional<StudentMisconceptionEntity> findByClientIdAndMisconceptionKey(UUID clientId, String misconceptionKey);

    List<StudentMisconceptionEntity> findByClientIdAndStatusNotAndLastSeenAtBefore(UUID clientId, MisconceptionStatus status, Instant cutoff);
}
