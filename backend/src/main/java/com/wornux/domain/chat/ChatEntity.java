package com.wornux.domain.chat;

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
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat")
public class ChatEntity {

  @Id private UUID id;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(nullable = false)
  private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_transcript_id")
  private ChatTranscriptEntity currentTranscript;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static ChatEntity create(UUID clientId, String title) {
    var now = Instant.now();
    var entity = new ChatEntity();
    entity.id = UUID.randomUUID();
    entity.clientId = clientId;
    entity.title = title;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void activateTranscript(ChatTranscriptEntity transcript) {
    this.currentTranscript = transcript;
    touch();
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }

  public void rename(String title) {
    this.title = title;
    touch();
  }
}
