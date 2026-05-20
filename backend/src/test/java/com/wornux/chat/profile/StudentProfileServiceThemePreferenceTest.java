package com.wornux.chat.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.ai.config.ProfileProperties;
import com.wornux.application.profile.StudentProfileService;
import com.wornux.application.profile.port.StudentProfilePersistencePort;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.profile.StudentProfileEntity;
import com.wornux.domain.profile.ThemePreference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceThemePreferenceTest {

  @Mock private StudentProfilePersistencePort profilePort;

  private StudentProfileService service;
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    var properties = new ProfileProperties();
    service =
        new StudentProfileService(
            profilePort,
            properties,
            new SimpleMeterRegistry(),
            objectMapper);
  }

  @Test
  void getThemePreferenceFallsBackToSystemWhenClientIdIsMissing() {
    assertThat(service.getThemePreference(null)).isEqualTo(ThemePreference.SYSTEM);
  }

  @Test
  void getThemePreferenceFallsBackToSystemWhenStoredPreferenceIsNull() {
    var profile = StudentProfileEntity.create(UUID.randomUUID());
    profile.setThemePreference(null);
    when(profilePort.findProfileById(profile.getClientId())).thenReturn(Optional.of(profile));

    assertThat(service.getThemePreference(profile.getClientId())).isEqualTo(ThemePreference.SYSTEM);
  }

  @Test
  void updateThemePreferencePersistsSelectionWithoutBumpingProfileVersion() {
    var profile = StudentProfileEntity.create(UUID.randomUUID());
    var initialVersion = profile.getProfileVersion();
    when(profilePort.findProfileById(profile.getClientId())).thenReturn(Optional.of(profile));
    when(profilePort.saveProfile(any(StudentProfileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var updatedPreference =
        service.updateThemePreference(profile.getClientId(), ThemePreference.LIGHT);

    var savedProfileCaptor = ArgumentCaptor.forClass(StudentProfileEntity.class);
    verify(profilePort).saveProfile(savedProfileCaptor.capture());

    assertThat(updatedPreference).isEqualTo(ThemePreference.LIGHT);
    assertThat(savedProfileCaptor.getValue().getThemePreference()).isEqualTo(ThemePreference.LIGHT);
    assertThat(savedProfileCaptor.getValue().getProfileVersion()).isEqualTo(initialVersion);
  }

  @Test
  void applyEvaluationProfilePersistsAndReadsBackInstantEvidence() {
    var clientId = UUID.randomUUID();
    var attemptId = UUID.randomUUID();
    var observedAt = Instant.parse("2026-05-18T13:15:51.936Z");
    var profile = StudentProfileEntity.create(clientId);
    var signal =
        new StudentLearningProfile.LearningSignal(
            "loops",
            "loop tracing",
            "evaluation:" + attemptId,
            1,
            observedAt,
            "score:80",
            false,
            false);
    var learningProfile =
        new StudentLearningProfile(
            List.of(signal),
            List.of(signal),
            List.of(),
            List.of(),
            List.of("guided"),
            "es",
            List.of(attemptId.toString()),
            List.of(),
            List.of("Use short traces."));

    when(profilePort.findProfileById(clientId)).thenReturn(Optional.of(profile));
    when(profilePort.findMisconceptionsByClientIdOrderByLastSeenAtDesc(clientId))
        .thenReturn(List.of());
    when(
            profilePort.findMisconceptionsByClientIdAndStatusNotAndLastSeenAtBefore(
                any(), any(), any()))
        .thenReturn(List.of());

    service.applyEvaluationProfile(clientId, attemptId, learningProfile);
    var observedCompetencies = (List<?>) profile.getLearningProfile().get("observedCompetencies");
    @SuppressWarnings("unchecked")
    var storedSignal = (Map<String, Object>) observedCompetencies.getFirst();

    assertThat(storedSignal).containsKey("lastObservedAt");
    assertThat(service.load(clientId).learningProfile().observedCompetencies().getFirst().lastObservedAt())
        .isEqualTo(observedAt);
  }
}
