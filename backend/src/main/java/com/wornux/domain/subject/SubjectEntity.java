package com.wornux.domain.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "subject")
@NamedEntityGraph(
    name = "Subject.withCurrentConfigRevision",
    attributeNodes = @NamedAttributeNode("currentConfigRevision"))
@Getter
@Setter
public class SubjectEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 96)
  private String slug;

  @Column(name = "display_name", nullable = false, columnDefinition = "text")
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private SubjectStatus status;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_config_revision_id")
  private SubjectConfigRevisionEntity currentConfigRevision;

  @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY)
  @OrderBy("version asc")
  @BatchSize(size = 50)
  private List<SubjectConfigRevisionEntity> configRevisions = new ArrayList<>();

  @Column(name = "config_version", nullable = false)
  private long configVersion;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SubjectEntity() {}

  public static SubjectEntity create(String slug, String displayName) {
    var now = Instant.now();
    var entity = new SubjectEntity();
    entity.id = UUID.randomUUID();
    entity.slug = slug;
    entity.displayName = displayName;
    entity.status = SubjectStatus.ACTIVE;
    entity.configVersion = 1L;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void publishConfig(SubjectConfigRevisionEntity revision) {
    this.currentConfigRevision = revision;
    this.configVersion = revision.getVersion();
    this.updatedAt = Instant.now();
  }

  public void publishConfig(SubjectConfigRevisionEntity revision, long version) {
    this.currentConfigRevision = revision;
    this.configVersion = version;
    this.updatedAt = Instant.now();
  }
}
