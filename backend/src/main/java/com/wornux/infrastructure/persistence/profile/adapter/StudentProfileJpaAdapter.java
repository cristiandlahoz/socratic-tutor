package com.wornux.infrastructure.persistence.profile.adapter;

import com.wornux.application.profile.port.StudentProfilePersistencePort;
import com.wornux.domain.profile.MisconceptionStatus;
import com.wornux.domain.profile.StudentMisconceptionEntity;
import com.wornux.domain.profile.StudentProfileEntity;
import com.wornux.domain.profile.StudentProfileSignalEntity;
import com.wornux.domain.profile.StudentTopicMasteryEntity;
import com.wornux.domain.profile.StudentTopicMasteryId;
import com.wornux.infrastructure.persistence.profile.StudentMisconceptionJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentProfileJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentProfileSignalJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentTopicMasteryJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StudentProfileJpaAdapter implements StudentProfilePersistencePort {

  private final StudentProfileJpaRepository studentProfileJpaRepository;
  private final StudentTopicMasteryJpaRepository studentTopicMasteryJpaRepository;
  private final StudentMisconceptionJpaRepository studentMisconceptionJpaRepository;
  private final StudentProfileSignalJpaRepository studentProfileSignalJpaRepository;

  public StudentProfileJpaAdapter(
      StudentProfileJpaRepository studentProfileJpaRepository,
      StudentTopicMasteryJpaRepository studentTopicMasteryJpaRepository,
      StudentMisconceptionJpaRepository studentMisconceptionJpaRepository,
      StudentProfileSignalJpaRepository studentProfileSignalJpaRepository) {
    this.studentProfileJpaRepository = studentProfileJpaRepository;
    this.studentTopicMasteryJpaRepository = studentTopicMasteryJpaRepository;
    this.studentMisconceptionJpaRepository = studentMisconceptionJpaRepository;
    this.studentProfileSignalJpaRepository = studentProfileSignalJpaRepository;
  }

  @Override
  public Optional<StudentProfileEntity> findProfileById(UUID clientId) {
    return studentProfileJpaRepository.findById(clientId);
  }

  @Override
  public StudentProfileEntity saveProfile(StudentProfileEntity profileEntity) {
    return studentProfileJpaRepository.save(profileEntity);
  }

  @Override
  public Optional<StudentTopicMasteryEntity> findMasteryById(StudentTopicMasteryId masteryId) {
    return studentTopicMasteryJpaRepository.findById(masteryId);
  }

  @Override
  public List<StudentTopicMasteryEntity> findMasteriesByClientId(UUID clientId) {
    return studentTopicMasteryJpaRepository.findById_ClientId(clientId);
  }

  @Override
  public StudentTopicMasteryEntity saveMastery(StudentTopicMasteryEntity masteryEntity) {
    return studentTopicMasteryJpaRepository.save(masteryEntity);
  }

  @Override
  public List<StudentMisconceptionEntity> findMisconceptionsByClientIdOrderByLastSeenAtDesc(
      UUID clientId) {
    return studentMisconceptionJpaRepository.findByClientIdOrderByLastSeenAtDesc(clientId);
  }

  @Override
  public Optional<StudentMisconceptionEntity> findMisconceptionByClientIdAndKey(
      UUID clientId, String key) {
    return studentMisconceptionJpaRepository.findByClientIdAndMisconceptionKey(clientId, key);
  }

  @Override
  public List<StudentMisconceptionEntity>
      findMisconceptionsByClientIdAndStatusNotAndLastSeenAtBefore(
          UUID clientId, MisconceptionStatus status, Instant cutoff) {
    return studentMisconceptionJpaRepository.findByClientIdAndStatusNotAndLastSeenAtBefore(
        clientId, status, cutoff);
  }

  @Override
  public StudentMisconceptionEntity saveMisconception(
      StudentMisconceptionEntity misconceptionEntity) {
    return studentMisconceptionJpaRepository.save(misconceptionEntity);
  }

  @Override
  public StudentProfileSignalEntity saveSignal(StudentProfileSignalEntity signalEntity) {
    return studentProfileSignalJpaRepository.save(signalEntity);
  }
}
