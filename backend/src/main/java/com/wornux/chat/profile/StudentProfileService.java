package com.wornux.chat.profile;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

  private final StudentProfileJpaRepository profileRepository;
  private final StudentTopicMasteryJpaRepository topicMasteryRepository;
  private final StudentMisconceptionJpaRepository misconceptionRepository;
  private final StudentProfileSignalJpaRepository signalRepository;
  private final ProfileProperties profileProperties;
  private final MeterRegistry meterRegistry;

  public StudentProfileService(
      StudentProfileJpaRepository profileRepository,
      StudentTopicMasteryJpaRepository topicMasteryRepository,
      StudentMisconceptionJpaRepository misconceptionRepository,
      StudentProfileSignalJpaRepository signalRepository,
      ProfileProperties profileProperties,
      MeterRegistry meterRegistry) {
    this.profileRepository = profileRepository;
    this.topicMasteryRepository = topicMasteryRepository;
    this.misconceptionRepository = misconceptionRepository;
    this.signalRepository = signalRepository;
    this.profileProperties = profileProperties;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public StudentProfileSnapshot load(UUID clientId) {
    if (clientId == null) {
      return StudentProfileSnapshot.anonymous();
    }

    var profile =
        profileRepository
            .findById(clientId)
            .orElseGet(() -> profileRepository.save(StudentProfileEntity.create(clientId)));
    resolveStaleMisconceptions(clientId);

    var weakTopics =
        topicMasteryRepository.findById_ClientId(clientId).stream()
            .filter(
                topic ->
                    topic.getMasteryLevel() == MasteryLevel.STRUGGLING
                        || topic.getMasteryLevel() == MasteryLevel.DEVELOPING)
            .sorted(
                Comparator.comparing(StudentTopicMasteryEntity::getMasteryLevel)
                    .thenComparing(StudentTopicMasteryEntity::getEvidenceCount)
                    .reversed())
            .map(StudentTopicMasteryEntity::topicKey)
            .limit(2)
            .toList();
    var activeMisconceptions =
        misconceptionRepository.findByClientIdOrderByLastSeenAtDesc(clientId).stream()
            .filter(misconception -> misconception.getStatus() == MisconceptionStatus.ACTIVE)
            .map(StudentMisconceptionEntity::getMisconceptionKey)
            .limit(4)
            .toList();

    return new StudentProfileSnapshot(
        profile.getPreferredLanguage(),
        profile.getOverallLevel(),
        profile.getHelpMode(),
        profile.isNeedsConcreteExamples(),
        weakTopics,
        activeMisconceptions,
        profile.getConfidenceScore(),
        profile.getProfileVersion());
  }

  @Transactional
  public ThemePreference getThemePreference(UUID clientId) {
    if (clientId == null) {
      return ThemePreference.SYSTEM;
    }

    var preference =
        profileRepository
            .findById(clientId)
            .orElseGet(() -> profileRepository.save(StudentProfileEntity.create(clientId)))
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
        profileRepository
            .findById(clientId)
            .orElseGet(() -> profileRepository.save(StudentProfileEntity.create(clientId)));

    if (profile.getThemePreference() == nextPreference) {
      return nextPreference;
    }

    profile.setThemePreference(nextPreference);
    profile.touchWithoutProfileVersion();
    profileRepository.save(profile);
    return nextPreference;
  }

  @Transactional
  public void applyTurnSignals(UUID clientId, TurnProfileUpdate update) {
    if (clientId == null || update == null) {
      return;
    }

    var profile =
        profileRepository
            .findById(clientId)
            .orElseGet(() -> profileRepository.save(StudentProfileEntity.create(clientId)));
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

    if (update.confidenceDelta().signum() != 0) {
      var nextConfidence = profile.getConfidenceScore().add(update.confidenceDelta());
      var clamped =
          nextConfidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
      if (clamped.compareTo(profile.getConfidenceScore()) != 0) {
        profile.setConfidenceScore(clamped);
        changed = true;
      }
    }

    for (var topic : update.topicsDetected()) {
      var mastery =
          topicMasteryRepository
              .findById(new StudentTopicMasteryId(clientId, topic))
              .orElseGet(() -> StudentTopicMasteryEntity.create(clientId, topic));
      mastery.incrementEvidence();
      updateMastery(mastery, update.levelSignals());
      topicMasteryRepository.save(mastery);
    }

    for (var misconceptionObservation : update.misconceptionsObserved()) {
      var misconception =
          misconceptionRepository
              .findByClientIdAndMisconceptionKey(
                  clientId, misconceptionObservation.misconceptionKey())
              .orElseGet(
                  () ->
                      StudentMisconceptionEntity.create(
                          clientId,
                          misconceptionObservation.topic(),
                          misconceptionObservation.misconceptionKey(),
                          misconceptionObservation.description(),
                          misconceptionObservation.confidence()));
      misconception.refresh(misconceptionObservation.confidence());
      misconceptionRepository.save(misconception);
      meterRegistry.counter("profile.misconception.detected").increment();
      changed = true;
    }

    signalRepository.save(
        StudentProfileSignalEntity.from(
            clientId,
            update.conversationId(),
            update.turnId(),
            "turn_update",
            update.signalPayload()));

    if (changed || update.hasProfileMutation()) {
      recalculateOverallLevel(clientId, profile);
      profile.touch();
      profileRepository.save(profile);
      meterRegistry.counter("profile.updates.total").increment();
      return;
    }

    meterRegistry.counter("profile.update.noop").increment();
    profileRepository.save(profile);
  }

  private void resolveStaleMisconceptions(UUID clientId) {
    var cutoff = Instant.now().minus(profileProperties.getMisconceptionTtlDays(), ChronoUnit.DAYS);
    for (var misconception :
        misconceptionRepository.findByClientIdAndStatusNotAndLastSeenAtBefore(
            clientId, MisconceptionStatus.RESOLVED, cutoff)) {
      misconception.resolve();
      misconceptionRepository.save(misconception);
    }
  }

  private void updateMastery(
      StudentTopicMasteryEntity mastery, List<TurnProfileUpdate.LevelSignal> levelSignals) {
    var matchingSignals =
        levelSignals.stream().filter(signal -> signal.topic() == mastery.topicKey()).toList();
    if (matchingSignals.isEmpty()) {
      return;
    }

    long downSignals =
        matchingSignals.stream()
            .filter(signal -> signal.direction() == TurnProfileUpdate.SignalDirection.DOWN)
            .count();
    long upSignals =
        matchingSignals.stream()
            .filter(signal -> signal.direction() == TurnProfileUpdate.SignalDirection.UP)
            .count();

    if (downSignals >= 1 && mastery.getEvidenceCount() >= 2) {
      mastery.setMasteryLevel(MasteryLevel.STRUGGLING);
      return;
    }
    if (upSignals >= 2) {
      mastery.setMasteryLevel(MasteryLevel.SOLID);
      return;
    }
    if (upSignals >= 1 || downSignals >= 1) {
      mastery.setMasteryLevel(MasteryLevel.DEVELOPING);
    }
  }

  private void recalculateOverallLevel(UUID clientId, StudentProfileEntity profile) {
    var levels =
        topicMasteryRepository.findById_ClientId(clientId).stream()
            .map(StudentTopicMasteryEntity::getMasteryLevel)
            .toList();
    if (levels.isEmpty()) {
      return;
    }

    long struggling = levels.stream().filter(level -> level == MasteryLevel.STRUGGLING).count();
    long solid = levels.stream().filter(level -> level == MasteryLevel.SOLID).count();

    if (solid >= 2 && struggling == 0) {
      profile.setOverallLevel(StudentOverallLevel.INTERMEDIATE);
    } else if (struggling >= 2) {
      profile.setOverallLevel(StudentOverallLevel.BEGINNER);
    } else {
      profile.setOverallLevel(StudentOverallLevel.DEVELOPING);
    }
  }
}
