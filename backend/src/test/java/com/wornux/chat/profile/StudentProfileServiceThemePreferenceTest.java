package com.wornux.chat.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

  @Mock private StudentProfileJpaRepository profileRepository;
  @Mock private StudentTopicMasteryJpaRepository topicMasteryRepository;
  @Mock private StudentMisconceptionJpaRepository misconceptionRepository;
  @Mock private StudentProfileSignalJpaRepository signalRepository;

  private StudentProfileService service;

  @BeforeEach
  void setUp() {
    var properties = new ProfileProperties();
    service =
        new StudentProfileService(
            profileRepository,
            topicMasteryRepository,
            misconceptionRepository,
            signalRepository,
            properties,
            new SimpleMeterRegistry());
  }

  @Test
  void getThemePreferenceFallsBackToSystemWhenClientIdIsMissing() {
    assertThat(service.getThemePreference(null)).isEqualTo(ThemePreference.SYSTEM);
  }

  @Test
  void getThemePreferenceFallsBackToSystemWhenStoredPreferenceIsNull() {
    var profile = StudentProfileEntity.create(UUID.randomUUID());
    profile.setThemePreference(null);
    when(profileRepository.findById(profile.getClientId())).thenReturn(Optional.of(profile));

    assertThat(service.getThemePreference(profile.getClientId())).isEqualTo(ThemePreference.SYSTEM);
  }

  @Test
  void updateThemePreferencePersistsSelectionWithoutBumpingProfileVersion() {
    var profile = StudentProfileEntity.create(UUID.randomUUID());
    var initialVersion = profile.getProfileVersion();
    when(profileRepository.findById(profile.getClientId())).thenReturn(Optional.of(profile));
    when(profileRepository.save(any(StudentProfileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var updatedPreference =
        service.updateThemePreference(profile.getClientId(), ThemePreference.LIGHT);

    var savedProfileCaptor = ArgumentCaptor.forClass(StudentProfileEntity.class);
    verify(profileRepository).save(savedProfileCaptor.capture());

    assertThat(updatedPreference).isEqualTo(ThemePreference.LIGHT);
    assertThat(savedProfileCaptor.getValue().getThemePreference()).isEqualTo(ThemePreference.LIGHT);
    assertThat(savedProfileCaptor.getValue().getProfileVersion()).isEqualTo(initialVersion);
  }
}
