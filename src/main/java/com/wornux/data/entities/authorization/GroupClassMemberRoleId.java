package com.wornux.data.entities.authorization;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class GroupClassMemberRoleId implements Serializable {

    @Column(name = "group_class_member_id")
    private UUID groupClassMemberId;

    @Column(name = "role_id")
    private UUID roleId;
}
