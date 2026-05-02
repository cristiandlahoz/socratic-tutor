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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "student_profile_signal")
public class StudentProfileSignalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "turn_id", nullable = false)
  private UUID turnId;

  @Column(name = "signal_type", nullable = false, length = 32)
  private String signalType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StudentProfileSignalEntity() {}

  public static StudentProfileSignalEntity from(
      UUID clientId,
      UUID conversationId,
      UUID turnId,
      String signalType,
      Map<String, Object> payload) {
    var entity = new StudentProfileSignalEntity();
    entity.clientId = clientId;
    entity.conversationId = conversationId;
    entity.turnId = turnId;
    entity.signalType = signalType;
    entity.payload = new LinkedHashMap<>(payload);
    entity.createdAt = Instant.now();
    return entity;
  }
}
