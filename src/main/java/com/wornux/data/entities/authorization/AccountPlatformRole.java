package com.wornux.data.entities.authorization;

import java.time.Instant;

import com.wornux.data.entities.identity.Account;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_platform_role")
@Getter
@Setter
public class AccountPlatformRole {

    @EmbeddedId
    private AccountPlatformRoleId id;

    @MapsId("accountId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @MapsId("roleId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_account_id")
    private Account assignedByAccount;

    @jakarta.persistence.Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
