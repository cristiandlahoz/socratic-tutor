package com.wornux.chat.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class StudentTopicMasteryId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "topic_key", nullable = false, length = 32)
  private String topicKey;

  protected StudentTopicMasteryId() {}

  public StudentTopicMasteryId(UUID clientId, TopicKey topicKey) {
    this.clientId = clientId;
    this.topicKey = topicKey.name();
  }

  public UUID getClientId() {
    return clientId;
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
