package com.wornux.services.evaluation;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

  private final EvaluationRepository evaluationRepository;

  public EvaluationService(EvaluationRepository evaluationRepository) {
    this.evaluationRepository = evaluationRepository;
  }

  @Transactional
  public Evaluation createDraft(String title, String instruction) {
    return evaluationRepository.save(Evaluation.create(title, instruction));
  }

  @Transactional(readOnly = true)
  public List<Evaluation> listAll() {
    return evaluationRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Evaluation get(UUID evaluationId) {
    return evaluationRepository
        .findById(evaluationId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationId));
  }

  @Transactional
  public Evaluation markGeneratingQuestions(UUID evaluationId) {
    var evaluation = get(evaluationId);
    evaluation.setStatus(EvaluationStatus.GENERATING_QUESTIONS);
    touch(evaluation);
    return evaluation;
  }

  @Transactional
  public Evaluation saveQuestions(UUID evaluationId, String questionsJson) {
    var evaluation = get(evaluationId);
    evaluation.setQuestionsJson(questionsJson);
    evaluation.setStatus(EvaluationStatus.QUESTIONS_READY);
    touch(evaluation);
    return evaluation;
  }

  @Transactional
  public Evaluation markAnswering(UUID evaluationId) {
    var evaluation = get(evaluationId);
    evaluation.setStatus(EvaluationStatus.ANSWERING);
    touch(evaluation);
    return evaluation;
  }

  @Transactional
  public Evaluation saveAnswers(UUID evaluationId, String answersJson) {
    var evaluation = get(evaluationId);
    evaluation.setAnswersJson(answersJson);
    evaluation.setStatus(EvaluationStatus.ANSWERING);
    touch(evaluation);
    return evaluation;
  }

  @Transactional
  public Evaluation markGeneratingReport(UUID evaluationId) {
    var evaluation = get(evaluationId);
    evaluation.setStatus(EvaluationStatus.GENERATING_REPORT);
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

  private void touch(Evaluation evaluation) {
    evaluation.setUpdatedAt(Instant.now());
  }
}
