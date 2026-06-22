package com.wornux.services.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.evaluation.Evaluation;
import com.wornux.data.entities.evaluation.EvaluationLifecycleStatus;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final ActiveAcademicContextResolver contextResolver;

    public EvaluationService(EvaluationRepository evaluationRepository, ActiveAcademicContextResolver contextResolver) {
        this.evaluationRepository = evaluationRepository;
        this.contextResolver = contextResolver;
    }

    @Transactional
    public Evaluation createPending(String title, String instruction) {
        var context = requireProfessorContext();
        var evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setGroupClass(new com.wornux.data.entities.academic.GroupClass());
        evaluation.getGroupClass().setId(context.groupClassId());
        evaluation.setCreatedByGroupClassMember(new GroupClassMember());
        evaluation.getCreatedByGroupClassMember().setId(context.groupClassMemberId());
        evaluation.setTitle(title);
        evaluation.setInstructions(instruction);
        evaluation.setStatus(EvaluationLifecycleStatus.DRAFT);
        evaluation.setCreatedAt(Instant.now());
        evaluation.setUpdatedAt(Instant.now());
        return evaluationRepository.save(evaluation);
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listAll() {
        return contextResolver.resolveCurrent()
                .map(context -> evaluationRepository.findByGroupClass_IdOrderByUpdatedAtDesc(context.groupClassId()))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public Evaluation get(UUID evaluationId) {
        var context = contextResolver.requireCurrent();
        return evaluationRepository.findById(evaluationId)
                .filter(evaluation -> evaluation.getGroupClass() != null
                        && context.groupClassId().equals(evaluation.getGroupClass().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationId));
    }

    @Transactional
    public Evaluation saveQuestions(UUID evaluationId, String questionsJson) {
        throw new SetupRequiredException("UC-002 blocker: the target evaluation model does not yet define question payload persistence.");
    }

    @Transactional
    public Evaluation markRunning(UUID evaluationId) {
        throw new SetupRequiredException("UC-002 blocker: evaluation execution still requires a follow-up use case.");
    }

    @Transactional
    public Evaluation saveAnswers(UUID evaluationId, String answersJson) {
        throw new SetupRequiredException("UC-002 blocker: the target evaluation assignment model does not yet define answer payload persistence.");
    }

    @Transactional
    public Evaluation completeReport(UUID evaluationId, String reportMarkdown) {
        throw new SetupRequiredException("UC-002 blocker: the target evaluation model does not yet define report persistence.");
    }

    @Transactional
    public Evaluation markFailed(UUID evaluationId) {
        throw new SetupRequiredException("UC-002 blocker: the target evaluation model does not yet define execution failure persistence.");
    }

    @Transactional
    public void delete(UUID evaluationId) {
        evaluationRepository.delete(get(evaluationId));
    }

    @Transactional
    public Evaluation update(UUID evaluationId, String title, String instruction) {
        var evaluation = get(evaluationId);
        evaluation.setTitle(title);
        evaluation.setInstructions(instruction);
        evaluation.setUpdatedAt(Instant.now());
        return evaluationRepository.save(evaluation);
    }

    private com.wornux.services.context.ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassRole() != GroupClassMemberRole.PROFESSOR) {
            throw new SetupRequiredException("An active professor class context is required before managing evaluations.");
        }
        return context;
    }
}
