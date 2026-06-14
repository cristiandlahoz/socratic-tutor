package com.wornux.services.profile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.wornux.ai.profile.TurnProfileUpdate;
import com.wornux.config.ProfileProperties;
import com.wornux.data.entities.StudentMisconception;
import com.wornux.data.entities.StudentProfile;
import com.wornux.data.entities.StudentProfileSignal;
import com.wornux.data.enums.MisconceptionStatus;
import com.wornux.data.enums.ThemePreference;
import com.wornux.data.repositories.profile.StudentMisconceptionRepository;
import com.wornux.data.repositories.profile.StudentProfileRepository;
import com.wornux.data.repositories.profile.StudentProfileSignalRepository;
import com.wornux.dtos.profile.StudentLearningProfile;
import com.wornux.dtos.profile.StudentProfileSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudentProfileService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StudentProfileRepository profileRepository;
    private final StudentMisconceptionRepository misconceptionRepository;
    private final StudentProfileSignalRepository signalRepository;
    private final ProfileProperties profileProperties;
    private final ObjectMapper objectMapper;

    public StudentProfileService(
            StudentProfileRepository profileRepository,
            StudentMisconceptionRepository misconceptionRepository,
            StudentProfileSignalRepository signalRepository,
            ProfileProperties profileProperties,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.misconceptionRepository = misconceptionRepository;
        this.signalRepository = signalRepository;
        this.profileProperties = profileProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StudentProfileSnapshot load(UUID clientId) {
        if (clientId == null) {
            return StudentProfileSnapshot.anonymous();
        }

        var profile = profileRepository.findById(clientId)
                .orElseGet(() -> profileRepository.save(StudentProfile.create(clientId)));
        resolveStaleMisconceptions(clientId);

        var activeMisconceptions = misconceptionRepository.findByClientIdOrderByLastSeenAtDesc(clientId)
                .stream()
                .filter(misconception -> misconception.getStatus() == MisconceptionStatus.ACTIVE)
                .map(StudentMisconception::getMisconceptionKey)
                .limit(4)
                .toList();

        return new StudentProfileSnapshot(profile.getPreferredLanguage(),
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

        var preference = profileRepository.findById(clientId)
                .orElseGet(() -> profileRepository.save(StudentProfile.create(clientId)))
                .getThemePreference();
        return preference == null ? ThemePreference.SYSTEM : preference;
    }

    @Transactional
    public ThemePreference updateThemePreference(UUID clientId, ThemePreference preference) {
        if (clientId == null) {
            return ThemePreference.SYSTEM;
        }

        var nextPreference = preference == null ? ThemePreference.SYSTEM : preference;
        var profile = profileRepository.findById(clientId)
                .orElseGet(() -> profileRepository.save(StudentProfile.create(clientId)));

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

        var profile = profileRepository.findById(clientId)
                .orElseGet(() -> profileRepository.save(StudentProfile.create(clientId)));
        boolean changed = false;

        for (var misconceptionObservation : update.misconceptionsObserved()) {
            var misconception = misconceptionRepository
                    .findByClientIdAndMisconceptionKey(clientId, misconceptionObservation.misconceptionKey())
                    .orElseGet(
                        () -> StudentMisconception.create(
                            clientId,
                            misconceptionObservation.topicKey(),
                            misconceptionObservation.misconceptionKey(),
                            misconceptionObservation.description()));
            misconception.refresh();
            misconceptionRepository.save(misconception);
            changed = true;
        }

        signalRepository.save(
            StudentProfileSignal
                    .from(clientId, update.conversationId(), update.turnId(), "turn_update", update.signalPayload()));

        if (changed || update.hasProfileMutation()) {
            profile.touch();
            profileRepository.save(profile);
            return;
        }

        profileRepository.save(profile);
    }

    @Transactional
    public void applyEvaluationProfile(UUID clientId, UUID attemptId, StudentLearningProfile learningProfile) {
        if (clientId == null || learningProfile == null) {
            return;
        }

        var profile = profileRepository.findById(clientId)
                .orElseGet(() -> profileRepository.save(StudentProfile.create(clientId)));
        profile.setPreferredLanguage(learningProfile.preferredLanguage());
        profile.setLearningProfile(objectMapper.convertValue(learningProfile, MAP_TYPE));
        signalRepository.save(
            StudentProfileSignal.from(
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
        profileRepository.save(profile);
    }

    private void resolveStaleMisconceptions(UUID clientId) {
        var cutoff = Instant.now().minus(profileProperties.getMisconceptionTtlDays(), ChronoUnit.DAYS);
        for (var misconception : misconceptionRepository
                .findByClientIdAndStatusNotAndLastSeenAtBefore(clientId, MisconceptionStatus.RESOLVED, cutoff)) {
            misconception.resolve();
            misconceptionRepository.save(misconception);
        }
    }

    private StudentLearningProfile learningProfileFrom(StudentProfile profile) {
        if (profile.getLearningProfile() == null || profile.getLearningProfile().isEmpty()) {
            return StudentLearningProfile.empty(profile.getPreferredLanguage());
        }
        try {
            return objectMapper.convertValue(profile.getLearningProfile(), StudentLearningProfile.class);
        }
        catch (IllegalArgumentException exception) {
            return StudentLearningProfile.empty(profile.getPreferredLanguage());
        }
    }
}
