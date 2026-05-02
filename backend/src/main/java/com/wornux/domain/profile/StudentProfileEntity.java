package com.wornux.domain.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_profile")
@Getter
@Setter
public class StudentProfileEntity {

  @Id
  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "preferred_language", nullable = false, length = 8)
  private String preferredLanguage;

  @Enumerated(EnumType.STRING)
  @Column(name = "overall_level", nullable = false, length = 16)
  private StudentOverallLevel overallLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "help_mode", nullable = false, length = 16)
  private HelpMode helpMode;

  @Column(name = "needs_concrete_examples", nullable = false)
  private boolean needsConcreteExamples;

  @Column(name = "confidence_score", nullable = false, precision = 4, scale = 3)
  private BigDecimal confidenceScore;

  @Column(name = "last_updated_at", nullable = false)
  private Instant lastUpdatedAt;

  @Column(name = "profile_version", nullable = false)
  private long profileVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "theme_preference", nullable = false, length = 16)
  private ThemePreference themePreference;

  protected StudentProfileEntity() {}

  public static StudentProfileEntity create(UUID clientId) {
    var entity = new StudentProfileEntity();
    entity.clientId = clientId;
    entity.preferredLanguage = "es";
    entity.overallLevel = StudentOverallLevel.DEVELOPING;
    entity.helpMode = HelpMode.GUIDED;
    entity.needsConcreteExamples = false;
    entity.confidenceScore = new BigDecimal("0.500");
    entity.lastUpdatedAt = Instant.now();
    entity.profileVersion = 1L;
    entity.themePreference = ThemePreference.SYSTEM;
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
