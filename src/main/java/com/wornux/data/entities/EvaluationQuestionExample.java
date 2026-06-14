package com.wornux.data.entities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_question_example")
@Getter
@Setter
public class EvaluationQuestionExample {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_revision_id", nullable = false)
    private EvaluationRevision evaluationRevision;

    @Column(name = "example_key", nullable = false, length = 96)
    private String exampleKey;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, columnDefinition = "text")
    private String guidance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rubric = new LinkedHashMap<>();

    protected EvaluationQuestionExample() {}

    public static EvaluationQuestionExample create(
            EvaluationRevision revision,
            String exampleKey,
            int ordinal,
            String guidance,
            Map<String, Object> rubric) {
        var entity = new EvaluationQuestionExample();
        entity.id = UUID.randomUUID();
        entity.evaluationRevision = revision;
        entity.exampleKey = exampleKey;
        entity.ordinal = ordinal;
        entity.guidance = guidance;
        entity.rubric = rubric == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rubric);
        return entity;
    }

    public Map<String, Object> toPromptMap() {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("exampleKey", exampleKey);
        value.put("ordinal", ordinal);
        value.put("guidance", guidance);
        value.put("rubric", rubric);
        return value;
    }
}
