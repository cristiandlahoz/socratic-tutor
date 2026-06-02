package com.wornux.services.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.Evaluation;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

  @Mock private EvaluationRepository evaluationRepository;

  private EvaluationService evaluationService;

  @BeforeEach
  void setUp() {
    evaluationService = new EvaluationService(evaluationRepository);
  }

  @Test
  void createDraftPersistsNewEvaluation() {
    when(evaluationRepository.save(any(Evaluation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Evaluation.class));

    var evaluation = evaluationService.createDraft("Intro", "Ask about variables");

    assertEquals("Intro", evaluation.getTitle());
    assertEquals("Ask about variables", evaluation.getInstruction());
    assertEquals(EvaluationStatus.DRAFT, evaluation.getStatus());
    verify(evaluationRepository).save(any(Evaluation.class));
  }

  @Test
  void listAllUsesRepositoryOrderingQuery() {
    var evaluation = Evaluation.create("Intro", "Ask about variables");
    when(evaluationRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc())
        .thenReturn(List.of(evaluation));

    var result = evaluationService.listAll();

    assertEquals(1, result.size());
    assertSame(evaluation, result.getFirst());
  }

  @Test
  void getFailsForUnknownEvaluation() {
    var evaluationId = UUID.randomUUID();
    when(evaluationRepository.findById(evaluationId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> evaluationService.get(evaluationId));
  }
}
