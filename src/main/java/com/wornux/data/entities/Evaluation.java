package com.wornux.data.entities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wornux.data.enums.EvaluationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@NamedEntityGraph(name = "Evaluation.withCurrentRevision", attributeNodes = @NamedAttributeNode("currentRevision"))
@Table(name = "evaluation")
@Getter
@Setter
public class Evaluation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 96)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvaluationStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id")
    private EvaluationRevision currentRevision;

    @OneToMany(mappedBy = "evaluation", fetch = FetchType.LAZY)
    @OrderBy("version asc")
    @BatchSize(size = 50)
    private List<EvaluationRevision> revisions = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected Evaluation() {}

    public static Evaluation draft(Subject subject, String slug, String title) {
        var now = Instant.now();
        var entity = new Evaluation();
        entity.id = UUID.randomUUID();
        entity.subject = subject;
        entity.slug = slug;
        entity.title = title;
        entity.status = EvaluationStatus.DRAFT;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void publish(EvaluationRevision revision) {
        this.currentRevision = revision;
        this.status = EvaluationStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }
}
