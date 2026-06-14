package com.wornux.services.subject;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.data.entities.SubjectConfigRevision;
import com.wornux.data.enums.SubjectStatus;
import com.wornux.data.repositories.subject.SubjectConfigRevisionRepository;
import com.wornux.data.repositories.subject.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectConfigService {

    private final SubjectRepository subjectRepository;
    private final SubjectConfigRevisionRepository revisionRepository;
    private final Cache<String, SubjectConfig> cache =
            Caffeine.newBuilder().maximumSize(128).expireAfterWrite(Duration.ofMinutes(20)).build();

    public SubjectConfigService(
            SubjectRepository subjectRepository,
            SubjectConfigRevisionRepository revisionRepository) {
        this.subjectRepository = subjectRepository;
        this.revisionRepository = revisionRepository;
    }

    @Transactional(readOnly = true)
    public String defaultSubjectSlug() {
        return subjectRepository.findFirstByStatusOrderByCreatedAtAsc(SubjectStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active subject exists"))
                .getSlug();
    }

    @Transactional(readOnly = true)
    public SubjectConfig current(String slug) {
        var subject = subjectRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subject " + slug));
        var cacheKey = cacheKey(subject.getSlug(), subject.getConfigVersion());
        return cache.get(cacheKey, _ -> {
            var revision = subject.getCurrentConfigRevision();
            if (revision == null) {
                revision = revisionRepository.findFirstBySubjectOrderByVersionDesc(subject)
                        .orElseThrow(
                            () -> new IllegalStateException(
                                    "Subject has no current config revision " + subject.getSlug()));
            }
            return toConfig(subject.getId(), subject.getSlug(), subject.getDisplayName(), revision);
        });
    }

    @Transactional
    public SubjectConfig publishRevision(
            String slug,
            Map<String, Object> config,
            Map<String, Object> rubricDefaults,
            Map<String, Object> questionPolicy,
            String createdBy) {
        var subject = subjectRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subject " + slug));
        long nextVersion = subject.getConfigVersion() + 1;
        var revision = revisionRepository.save(
            SubjectConfigRevision.create(subject, nextVersion, config, rubricDefaults, questionPolicy, createdBy));
        subject.publishConfig(revision, nextVersion);
        subjectRepository.save(subject);
        cache.invalidateAll();
        return toConfig(subject.getId(), subject.getSlug(), subject.getDisplayName(), revision);
    }

    private SubjectConfig toConfig(UUID subjectId, String slug, String displayName, SubjectConfigRevision revision) {
        return new SubjectConfig(subjectId,
                slug,
                displayName,
                revision.getVersion(),
                revision.getId(),
                copy(revision.getConfig()),
                copy(revision.getRubricDefaults()),
                copy(revision.getQuestionPolicy()));
    }

    private static String cacheKey(String slug, long version) {
        return slug + ":" + version;
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }
}
