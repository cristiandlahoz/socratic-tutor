package com.wornux.chat.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_profile")
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
    return entity;
  }

  public UUID getClientId() {
    return clientId;
  }

  public String getPreferredLanguage() {
    return preferredLanguage;
  }

  public void setPreferredLanguage(String preferredLanguage) {
    this.preferredLanguage = preferredLanguage;
  }

  public StudentOverallLevel getOverallLevel() {
    return overallLevel;
  }

  public void setOverallLevel(StudentOverallLevel overallLevel) {
    this.overallLevel = overallLevel;
  }

  public HelpMode getHelpMode() {
    return helpMode;
  }

  public void setHelpMode(HelpMode helpMode) {
    this.helpMode = helpMode;
  }

  public boolean isNeedsConcreteExamples() {
    return needsConcreteExamples;
  }

  public void setNeedsConcreteExamples(boolean needsConcreteExamples) {
    this.needsConcreteExamples = needsConcreteExamples;
  }

  public BigDecimal getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(BigDecimal confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public long getProfileVersion() {
    return profileVersion;
  }

  public void touch() {
    this.lastUpdatedAt = Instant.now();
    this.profileVersion++;
  }
}
