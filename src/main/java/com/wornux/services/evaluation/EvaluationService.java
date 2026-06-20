package com.wornux.services.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;

    public EvaluationService(EvaluationRepository evaluationRepository) {
        this.evaluationRepository = evaluationRepository;
    }

    @Transactional
    public Evaluation createPending(String title, String instruction) {
        return evaluationRepository.save(Evaluation.create(title, instruction));
    }

    @Transactional(readOnly = true)
    public List<Evaluation> listAll() {
        return evaluationRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Evaluation get(UUID evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationId));
    }

    @Transactional
    public Evaluation saveQuestions(UUID evaluationId, String questionsJson) {
        var evaluation = get(evaluationId);
        evaluation.setQuestionsJson(questionsJson);
        touch(evaluation);
        return evaluation;
    }

    @Transactional
    public Evaluation markRunning(UUID evaluationId) {
        var evaluation = get(evaluationId);
        evaluation.setStatus(EvaluationStatus.RUNNING);
        touch(evaluation);
        return evaluation;
    }

    @Transactional
    public Evaluation saveAnswers(UUID evaluationId, String answersJson) {
        var evaluation = get(evaluationId);
        evaluation.setAnswersJson(answersJson);
        evaluation.setStatus(EvaluationStatus.RUNNING);
        touch(evaluation);
        return evaluation;
    }

    @Transactional
    public Evaluation completeReport(UUID evaluationId, String reportMarkdown) {
        var evaluation = get(evaluationId);
        evaluation.setReportMarkdown(reportMarkdown);
        evaluation.setStatus(EvaluationStatus.COMPLETED);
        touch(evaluation);
        return evaluation;
    }

    @Transactional
    public Evaluation markFailed(UUID evaluationId) {
        var evaluation = get(evaluationId);
        evaluation.setStatus(EvaluationStatus.FAILED);
        touch(evaluation);
        return evaluation;
    }

    @Transactional
    public void delete(UUID evaluationId) {
        var evaluation = get(evaluationId);
        evaluationRepository.delete(evaluation);
    }

    @Transactional
    public Evaluation update(UUID evaluationId, String title, String instruction) {
        var evaluation = get(evaluationId);
        evaluation.setTitle(title);
        evaluation.setInstruction(instruction);
        touch(evaluation);
        return evaluation;
    }

    private void touch(Evaluation evaluation) {
        evaluation.setUpdatedAt(Instant.now());
    }
}
