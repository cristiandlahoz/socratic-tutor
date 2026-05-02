package com.wornux.chat.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.config.ProfileProperties;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.application.profile.StudentProfileService;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.domain.profile.StudentProfileEntity;
import com.wornux.domain.profile.ThemePreference;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.persistence.profile.StudentMisconceptionJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentProfileJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentProfileSignalJpaRepository;
import com.wornux.infrastructure.persistence.profile.StudentTopicMasteryJpaRepository;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
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
