package com.wornux.data.entities.conversation;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(nullable = false)
    private String title;

    @Column(name = "last_prompt_tokens")
    private Integer lastPromptTokens;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
