package com.wornux.data.entities.authorization;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class AccountRoleId implements Serializable {

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "role_id")
    private Long roleId;
}
