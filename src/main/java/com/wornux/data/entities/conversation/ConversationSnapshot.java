package com.wornux.data.entities.conversation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversation_snapshot")
@Getter
@Setter
public class ConversationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_snapshot_id")
    private ConversationSnapshot previousSnapshot;

    @Column(name = "snapshot_no", nullable = false)
    private long snapshotNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "carry_context", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> carryContext = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> messages = List.of();

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "compacted_at")
    private Instant compactedAt;
}
