package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.TenantAccount;
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

@Entity
@Table(name = "safe_browser_alert")
@Getter
@Setter
public class SafeBrowserAlert {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "training_activity_id", nullable = false)
    private TrainingActivity trainingActivity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_tenant_account_id", nullable = false)
    private TenantAccount professorTenantAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_group_class_member_id")
    private GroupClassMember professorGroupClassMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafeBrowserAlertStatus status;

    @Column(name = "incident_count", nullable = false)
    private int incidentCount;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
