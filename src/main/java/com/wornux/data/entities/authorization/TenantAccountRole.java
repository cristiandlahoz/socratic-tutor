package com.wornux.data.entities.authorization;

import java.time.Instant;

import com.wornux.data.entities.identity.TenantAccount;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant_account_role")
@Getter
@Setter
public class TenantAccountRole {

    @EmbeddedId
    private TenantAccountRoleId id;

    @MapsId("tenantAccountId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_account_id", nullable = false)
    private TenantAccount tenantAccount;

    @MapsId("roleId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "assigned_by_tenant_account_id")
    private TenantAccount assignedByTenantAccount;

    @jakarta.persistence.Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
