package com.wornux.application.profile;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.wornux.ai.config.ProfileProperties;
import com.wornux.ai.profile.TurnProfileUpdate;
import com.wornux.application.profile.port.StudentProfilePersistencePort;
import com.wornux.domain.profile.MisconceptionStatus;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.profile.StudentMisconceptionEntity;
import com.wornux.domain.profile.StudentProfileEntity;
import com.wornux.domain.profile.StudentProfileSignalEntity;
import com.wornux.domain.profile.StudentProfileSnapshot;
import com.wornux.domain.profile.ThemePreference;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final StudentProfilePersistencePort profilePort;
  private final ProfileProperties profileProperties;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;

  public StudentProfileService(
      StudentProfilePersistencePort profilePort,
      ProfileProperties profileProperties,
      MeterRegistry meterRegistry,
      ObjectMapper objectMapper) {
    this.profilePort = profilePort;
    this.profileProperties = profileProperties;
    this.meterRegistry = meterRegistry;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public StudentProfileSnapshot load(UUID clientId) {
    if (clientId == null) {
      return StudentProfileSnapshot.anonymous();
    }

    var profile =
        profilePort
            .findProfileById(clientId)
            .orElseGet(() -> profilePort.saveProfile(StudentProfileEntity.create(clientId)));
    resolveStaleMisconceptions(clientId);

    var activeMisconceptions =
        profilePort.findMisconceptionsByClientIdOrderByLastSeenAtDesc(clientId).stream()
            .filter(misconception -> misconception.getStatus() == MisconceptionStatus.ACTIVE)
            .map(StudentMisconceptionEntity::getMisconceptionKey)
            .limit(4)
            .toList();

    return new StudentProfileSnapshot(
        profile.getPreferredLanguage(),
        profile.getHelpMode(),
        profile.isNeedsConcreteExamples(),
        activeMisconceptions,
        profile.getProfileVersion(),
        learningProfileFrom(profile));
  }

  @Transactional
  public ThemePreference getThemePreference(UUID clientId) {
    if (clientId == null) {
      return ThemePreference.SYSTEM;
    }

    var preference =
        profilePort
            .findProfileById(clientId)
            .orElseGet(() -> profilePort.saveProfile(StudentProfileEntity.create(clientId)))
            .getThemePreference();
    return preference == null ? ThemePreference.SYSTEM : preference;
  }

  @Transactional
  public ThemePreference updateThemePreference(UUID clientId, ThemePreference preference) {
    if (clientId == null) {
      return ThemePreference.SYSTEM;
    }

    var nextPreference = preference == null ? ThemePreference.SYSTEM : preference;
    var profile =
        profilePort
            .findProfileById(clientId)
            .orElseGet(() -> profilePort.saveProfile(StudentProfileEntity.create(clientId)));

    if (profile.getThemePreference() == nextPreference) {
      return nextPreference;
    }

    profile.setThemePreference(nextPreference);
    profile.touchWithoutProfileVersion();
    profilePort.saveProfile(profile);
    return nextPreference;
  }

  @Transactional
  public void applyTurnSignals(UUID clientId, TurnProfileUpdate update) {
    if (clientId == null || update == null) {
      return;
    }

    var profile =
        profilePort
            .findProfileById(clientId)
            .orElseGet(() -> profilePort.saveProfile(StudentProfileEntity.create(clientId)));
    boolean changed = false;

    if (update.preferredLanguage() != null
        && !update.preferredLanguage().isBlank()
        && !update.preferredLanguage().equalsIgnoreCase(profile.getPreferredLanguage())) {
      profile.setPreferredLanguage(update.preferredLanguage());
      changed = true;
    }

    if (update.needsConcreteExamples() && !profile.isNeedsConcreteExamples()) {
      profile.setNeedsConcreteExamples(true);
      changed = true;
    }

    if (update.recommendedHelpMode() != null
        && profile.getHelpMode() != update.recommendedHelpMode()
        && update.toolEvidence().stream().filter(TurnProfileUpdate.ToolEvidence::useful).count()
            >= 2) {
      profile.setHelpMode(update.recommendedHelpMode());
      meterRegistry.counter("profile.help_mode.changed").increment();
      changed = true;
    }

    for (var misconceptionObservation : update.misconceptionsObserved()) {
      var misconception =
          profilePort
              .findMisconceptionByClientIdAndKey(
                  clientId, misconceptionObservation.misconceptionKey())
              .orElseGet(
                  () ->
                      StudentMisconceptionEntity.create(
                          clientId,
                          misconceptionObservation.topicKey(),
                          misconceptionObservation.misconceptionKey(),
                          misconceptionObservation.description(),
                          misconceptionObservation.confidence()));
      misconception.refresh(misconceptionObservation.confidence());
      profilePort.saveMisconception(misconception);
      meterRegistry.counter("profile.misconception.detected").increment();
      changed = true;
    }

    profilePort.saveSignal(
        StudentProfileSignalEntity.from(
            clientId,
            update.conversationId(),
            update.turnId(),
            "turn_update",
            update.signalPayload()));

    if (changed || update.hasProfileMutation()) {
      profile.touch();
      profilePort.saveProfile(profile);
      meterRegistry.counter("profile.updates.total").increment();
      return;
    }

    meterRegistry.counter("profile.update.noop").increment();
    profilePort.saveProfile(profile);
  }

  @Transactional
  public void applyEvaluationProfile(
      UUID clientId, UUID attemptId, StudentLearningProfile learningProfile) {
    if (clientId == null || learningProfile == null) {
      return;
    }

    var profile =
        profilePort
            .findProfileById(clientId)
            .orElseGet(() -> profilePort.saveProfile(StudentProfileEntity.create(clientId)));
    profile.setPreferredLanguage(learningProfile.preferredLanguage());
    profile.setLearningProfile(objectMapper.convertValue(learningProfile, MAP_TYPE));
    profilePort.saveSignal(
        StudentProfileSignalEntity.from(
            clientId,
            null,
            attemptId == null ? UUID.randomUUID() : attemptId,
            "evaluation_profile",
            Map.of(
                "attemptId",
                attemptId == null ? "" : attemptId.toString(),
                "recentEvidenceIds",
                learningProfile.recentEvidenceIds(),
                "weakConceptCount",
                learningProfile.weakConcepts().size(),
                "misconceptionCount",
                learningProfile.activeMisconceptions().size())));
    profile.touch();
    profilePort.saveProfile(profile);
    meterRegistry.counter("profile.evaluation_updates.total").increment();
  }

  private void resolveStaleMisconceptions(UUID clientId) {
    var cutoff = Instant.now().minus(profileProperties.getMisconceptionTtlDays(), ChronoUnit.DAYS);
    for (var misconception :
        profilePort.findMisconceptionsByClientIdAndStatusNotAndLastSeenAtBefore(
            clientId, MisconceptionStatus.RESOLVED, cutoff)) {
      misconception.resolve();
      profilePort.saveMisconception(misconception);
    }
  }

  private StudentLearningProfile learningProfileFrom(StudentProfileEntity profile) {
    if (profile.getLearningProfile() == null || profile.getLearningProfile().isEmpty()) {
      return StudentLearningProfile.empty(profile.getPreferredLanguage());
    }
    try {
      return objectMapper.convertValue(profile.getLearningProfile(), StudentLearningProfile.class);
    } catch (IllegalArgumentException exception) {
      return StudentLearningProfile.empty(profile.getPreferredLanguage());
    }
  }
}
