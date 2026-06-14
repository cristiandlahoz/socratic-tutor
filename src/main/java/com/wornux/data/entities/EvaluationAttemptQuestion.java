package com.wornux.data.entities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_attempt_question")
@Getter
@Setter
public class EvaluationAttemptQuestion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private EvaluationAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_example_id")
    private EvaluationQuestionExample sourceExample;

    @Column(name = "question_key", nullable = false, length = 96)
    private String questionKey;

    @Column(name = "blueprint_key", nullable = false, length = 96)
    private String blueprintKey;

    @Column(nullable = false)
    private int ordinal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> questionSnapshot = new LinkedHashMap<>();

    @Column(name = "question_hash", nullable = false, length = 64)
    private String questionHash;

    @OneToMany(mappedBy = "attemptQuestion", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("answeredAt desc")
    @BatchSize(size = 50)
    private List<EvaluationAttemptResponse> responses = new ArrayList<>();

    protected EvaluationAttemptQuestion() {}

    public static EvaluationAttemptQuestion generated(
            EvaluationAttempt attempt,
            EvaluationQuestionExample sourceExample,
            String questionKey,
            String blueprintKey,
            int ordinal,
            Map<String, Object> questionSnapshot,
            String questionHash) {
        var entity = new EvaluationAttemptQuestion();
        entity.id = UUID.randomUUID();
        entity.attempt = attempt;
        entity.sourceExample = sourceExample;
        entity.questionKey = questionKey;
        entity.blueprintKey = blueprintKey;
        entity.ordinal = ordinal;
        entity.questionSnapshot =
                questionSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(questionSnapshot);
        entity.questionHash = questionHash;
        return entity;
    }
}
