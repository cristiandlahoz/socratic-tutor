package com.wornux.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wornux.application.subject.SubjectConfigService;
import com.wornux.domain.subject.SubjectConfigRevisionEntity;
import com.wornux.domain.subject.SubjectEntity;
import com.wornux.infrastructure.persistence.subject.SubjectConfigRevisionJpaRepository;
import com.wornux.infrastructure.persistence.subject.SubjectJpaRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubjectConfigServiceTest {

  @Mock private SubjectJpaRepository subjectRepository;
  @Mock private SubjectConfigRevisionJpaRepository revisionRepository;

  @Test
  void cacheKeyIncludesVersionSoPublishedConfigCanAdvance() {
    var subject = SubjectEntity.create("intro", "Intro");
    var revisionOne =
        SubjectConfigRevisionEntity.create(
            subject, 1, Map.of("scope", "v1"), Map.of(), Map.of(), "test");
    subject.publishConfig(revisionOne, 1);

    when(subjectRepository.findBySlug("intro")).thenReturn(Optional.of(subject));

    var service = new SubjectConfigService(subjectRepository, revisionRepository);
    var first = service.current("intro");
    var second = service.current("intro");

    assertThat(first).isSameAs(second);
    assertThat(first.config()).containsEntry("scope", "v1");
  }
}
