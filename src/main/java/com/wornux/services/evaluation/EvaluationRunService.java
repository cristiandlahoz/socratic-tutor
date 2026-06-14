package com.wornux.services.evaluation;

import com.wornux.data.entities.EvaluationRun;
import com.wornux.data.enums.EvaluationRunStatus;
import com.wornux.data.repositories.evaluation.EvaluationRunRepository;
import com.wornux.services.evaluation.EvaluationChatService.AnswerRecord;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService.GeneratedQuestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationRunService {

  private final EvaluationRunRepository runRepository;
  private final EvaluationQuestionGenerationService questionGenerationService;

  public EvaluationRunService(
      EvaluationRunRepository runRepository,
      EvaluationQuestionGenerationService questionGenerationService) {
    this.runRepository = runRepository;
    this.questionGenerationService = questionGenerationService;
  }

  @Transactional
  public EvaluationRun createRun(UUID evaluationId, UUID studentClientId, String questionsJson) {
    var run = EvaluationRun.create(evaluationId, studentClientId, questionsJson);
    return runRepository.save(run);
  }

  @Transactional(readOnly = true)
  public EvaluationRun loadRun(UUID runId) {
    return runRepository
        .findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation run " + runId));
  }

  @Transactional(readOnly = true)
  public List<EvaluationRun> listRunsForEvaluation(UUID evaluationId) {
    return runRepository.findByEvaluationIdOrderByCreatedAtDesc(evaluationId);
  }

  @Transactional(readOnly = true)
  public List<EvaluationRun> listRunsForStudent(UUID studentClientId) {
    return runRepository.findByStudentClientIdOrderByCreatedAtDesc(studentClientId);
  }

  @Transactional
  public EvaluationRun appendAnswer(UUID runId, AnswerRecord answer) {
    var run = loadRun(runId);
    var memory = new EvaluationMemory(run);
    var answers = new ArrayList<>(memory.getAnswersGiven());
    answers.add(answer);
    memory.setAnswersGiven(answers);
    touch(run);
    return run;
  }

  @Transactional
  public EvaluationRun persistConversation(UUID runId, List<GeneratedQuestion> questions, List<AnswerRecord> answers) {
    var run = loadRun(runId);
    var memory = new EvaluationMemory(run);
    memory.setQuestionsAsked(questions);
    memory.setAnswersGiven(answers);
    run.setUpdatedAt(Instant.now());
    return run;
  }

  @Transactional
  public EvaluationRun completeReport(UUID runId, String reportMarkdown) {
    var run = loadRun(runId);
    var memory = new EvaluationMemory(run);
    memory.setReportMarkdown(reportMarkdown);
    run.setStatus(EvaluationRunStatus.COMPLETED);
    touch(run);
    return run;
  }

  @Transactional
  public EvaluationRun markFailed(UUID runId) {
    var run = loadRun(runId);
    run.setStatus(EvaluationRunStatus.FAILED);
    touch(run);
    return run;
  }

  private void touch(EvaluationRun run) {
    run.setUpdatedAt(Instant.now());
  }
}
