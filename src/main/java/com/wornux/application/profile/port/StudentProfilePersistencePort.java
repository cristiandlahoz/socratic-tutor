package com.wornux.application.profile.port;

import com.wornux.domain.profile.MisconceptionStatus;
import com.wornux.domain.profile.StudentMisconceptionEntity;
import com.wornux.domain.profile.StudentProfileEntity;
import com.wornux.domain.profile.StudentProfileSignalEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentProfilePersistencePort {
  Optional<StudentProfileEntity> findProfileById(UUID clientId);

  StudentProfileEntity saveProfile(StudentProfileEntity profileEntity);

  List<StudentMisconceptionEntity> findMisconceptionsByClientIdOrderByLastSeenAtDesc(UUID clientId);

  Optional<StudentMisconceptionEntity> findMisconceptionByClientIdAndKey(UUID clientId, String key);

  List<StudentMisconceptionEntity> findMisconceptionsByClientIdAndStatusNotAndLastSeenAtBefore(
      UUID clientId, MisconceptionStatus status, Instant cutoff);

  StudentMisconceptionEntity saveMisconception(StudentMisconceptionEntity misconceptionEntity);

  StudentProfileSignalEntity saveSignal(StudentProfileSignalEntity signalEntity);
}
