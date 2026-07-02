package com.wornux.data.entities.identity;

import java.time.Instant;

import com.wornux.data.entities.academic.GroupClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_context_preference")
@Getter
@Setter
public class AccountContextPreference {

    @Id
    @Column(name = "account_id")
    private java.util.UUID accountId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_level")
    private ContextLevel contextLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_class_id")
    private GroupClass groupClass;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
