package com.wornux.data.entities.evaluation;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
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

@Entity(name = "AcademicEvaluation")
@Table(name = "evaluation")
@Getter
@Setter
public class Evaluation {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_class_id", nullable = false)
    private GroupClass groupClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_group_class_member_id", nullable = false)
    private GroupClassMember createdByGroupClassMember;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationLifecycleStatus status;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
