package com.wornux.data.entities.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conversation")
@Getter
@Setter
public class Conversation {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_class_member_id", nullable = false)
    private GroupClassMember groupClassMember;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_snapshot_id")
    private ConversationSnapshot currentSnapshot;

    @OneToMany(mappedBy = "conversation")
    private List<ConversationSnapshot> snapshots = new ArrayList<>();

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
