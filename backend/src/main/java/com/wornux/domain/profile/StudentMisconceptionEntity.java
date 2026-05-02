package com.wornux.domain.profile;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
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
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_misconception")
@Getter
@Setter
public class StudentMisconceptionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "topic_key", nullable = false, length = 32)
  private String topicKey;

  @Column(name = "misconception_key", nullable = false, length = 64)
  private String misconceptionKey;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(nullable = false, precision = 4, scale = 3)
  private BigDecimal confidence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MisconceptionStatus status;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  protected StudentMisconceptionEntity() {}

  public static StudentMisconceptionEntity create(
      UUID clientId,
      TopicKey topicKey,
      String misconceptionKey,
      String description,
      BigDecimal confidence) {
    var entity = new StudentMisconceptionEntity();
    entity.clientId = clientId;
    entity.topicKey = topicKey.name();
    entity.misconceptionKey = misconceptionKey;
    entity.description = description;
    entity.confidence = confidence;
    entity.status = MisconceptionStatus.ACTIVE;
    entity.lastSeenAt = Instant.now();
    return entity;
  }

  public void refresh(BigDecimal confidence) {
    this.confidence = confidence;
    this.status = MisconceptionStatus.ACTIVE;
    this.lastSeenAt = Instant.now();
  }

  public void resolve() {
    this.status = MisconceptionStatus.RESOLVED;
  }
}
