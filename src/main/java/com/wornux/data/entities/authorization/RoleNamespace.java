package com.wornux.data.entities.authorization;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "role_namespace")
@Getter
@Setter
public class RoleNamespace {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "rbac_version", nullable = false)
    private long rbacVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
