package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "safe_browser_event")
@Getter
@Setter
public class SafeBrowserEvent {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "training_activity_assignment_id", nullable = false)
    private TrainingActivityAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "safe_browser_session_id")
    private SafeBrowserSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_group_class_member_id")
    private GroupClassMember actorGroupClassMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private SafeBrowserEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafeBrowserEventSeverity severity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "client_event_id")
    private UUID clientEventId;

    @Column(name = "client_occurred_at")
    private Instant clientOccurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
