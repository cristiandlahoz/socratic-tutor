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
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Embeddable
public class StudentTopicMasteryId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Getter
  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "topic_key", nullable = false, length = 32)
  private String topicKey;

  protected StudentTopicMasteryId() {}

  public StudentTopicMasteryId(UUID clientId, TopicKey topicKey) {
    this.clientId = clientId;
    this.topicKey = topicKey.name();
  }

  public TopicKey topic() {
    return TopicKey.valueOf(topicKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StudentTopicMasteryId that)) {
      return false;
    }
    return Objects.equals(clientId, that.clientId) && Objects.equals(topicKey, that.topicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, topicKey);
  }
}
