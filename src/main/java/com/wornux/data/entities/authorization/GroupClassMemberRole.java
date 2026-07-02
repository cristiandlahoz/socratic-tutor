package com.wornux.data.entities.authorization;

import java.time.Instant;

import com.wornux.data.entities.academic.GroupClassMember;
import jakarta.persistence.Column;
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
@Table(name = "group_class_member_role")
@Getter
@Setter
public class GroupClassMemberRole {

    @EmbeddedId
    private GroupClassMemberRoleId id;

    @MapsId("groupClassMemberId")
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_class_member_id", nullable = false)
    private GroupClassMember groupClassMember;

    @MapsId("roleId")
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_group_class_member_id")
    private GroupClassMember assignedByGroupClassMember;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
