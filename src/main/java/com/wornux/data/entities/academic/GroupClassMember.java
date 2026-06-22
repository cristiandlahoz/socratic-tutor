package com.wornux.data.entities.academic;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.identity.TenantAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "group_class_member")
@Getter
@Setter
public class GroupClassMember {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_class_id", nullable = false)
    private GroupClass groupClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_account_id", nullable = false)
    private TenantAccount tenantAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupClassMemberRole role;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
