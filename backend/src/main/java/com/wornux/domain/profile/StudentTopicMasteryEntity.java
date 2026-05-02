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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "student_topic_mastery")
public class StudentTopicMasteryEntity {

  @EmbeddedId private StudentTopicMasteryId id;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "mastery_level", nullable = false, length = 16)
  private MasteryLevel masteryLevel;

  @Column(name = "evidence_count", nullable = false)
  private int evidenceCount;

  @Setter
  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  protected StudentTopicMasteryEntity() {}

  public static StudentTopicMasteryEntity create(UUID clientId, TopicKey topicKey) {
    var entity = new StudentTopicMasteryEntity();
    entity.id = new StudentTopicMasteryId(clientId, topicKey);
    entity.masteryLevel = MasteryLevel.UNKNOWN;
    entity.evidenceCount = 0;
    entity.lastSeenAt = Instant.now();
    return entity;
  }

  public TopicKey topicKey() {
    return id.topic();
  }

  public void incrementEvidence() {
    this.evidenceCount++;
    this.lastSeenAt = Instant.now();
  }
}
