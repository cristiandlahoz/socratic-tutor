package com.wornux.legacy.data.entities;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.wornux.data.enums.ThemePreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student_profile")
@Getter
@Setter
public class StudentProfile {

    @Id
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage;

    @Column(name = "needs_concrete_examples", nullable = false)
    private boolean needsConcreteExamples;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "profile_version", nullable = false)
    private long profileVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 16)
    private ThemePreference themePreference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learning_profile", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> learningProfile = new LinkedHashMap<>();

    protected StudentProfile() {}

    public static StudentProfile create(UUID clientId) {
        var entity = new StudentProfile();
        entity.clientId = clientId;
        entity.preferredLanguage = "es";
        entity.needsConcreteExamples = false;
        entity.lastUpdatedAt = Instant.now();
        entity.profileVersion = 1L;
        entity.themePreference = ThemePreference.SYSTEM;
        entity.learningProfile = new LinkedHashMap<>();
        return entity;
    }

    public void touch() {
        this.lastUpdatedAt = Instant.now();
        this.profileVersion++;
    }

    public void touchWithoutProfileVersion() {
        this.lastUpdatedAt = Instant.now();
    }
}
