package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingAssignmentEvaluationServiceTest {

    @Test
    void af4_blankWhitespaceIsRejectedBeforeAnyPersistenceOrJob() {
        var fixture = fixture();

        assertThatThrownBy(() -> fixture.service.submitAnswer(fixture.assignmentId, " \n\t ", fixture.submissionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe una respuesta antes de continuar");

        verify(fixture.assignmentRepository, never()).save(any());
        verify(fixture.turnRepository, never()).save(any());
        verify(fixture.jobRepository, never()).insertTutorJobIfAbsent(any(), anyString(), anyInt(), any(), any(), any(), any(), anyLong(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void af5_meaningfulAnswerPreservesWhitespaceAndAtomicallyEnqueuesTutorWork() {
        var fixture = fixture();
        var turn = question(fixture.assignment);
        when(fixture.turnRepository.findByAssignment_IdAndAnswerSubmissionId(fixture.assignmentId, fixture.submissionId)).thenReturn(Optional.empty());
        when(fixture.turnRepository.findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(fixture.assignmentId)).thenReturn(Optional.of(turn));
        when(fixture.turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(fixture.assignmentId)).thenReturn(List.of(turn));

        fixture.service.submitAnswer(fixture.assignmentId, " no sé ", fixture.submissionId);

        assertThat(turn.getAnswerText()).isEqualTo(" no sé ");
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        verify(fixture.jobRepository).insertTutorJobIfAbsent(any(), eq("NEXT_DECISION"), anyInt(), any(), any(), any(), any(), anyLong(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void af7_duplicateSubmissionWithEquivalentPayloadIsIdempotent() {
        var fixture = fixture();
        var persisted = question(fixture.assignment);
        persisted.setAnswerText("same answer");
        when(fixture.turnRepository.findByAssignment_IdAndAnswerSubmissionId(fixture.assignmentId, fixture.submissionId)).thenReturn(Optional.of(persisted));
        when(fixture.turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(fixture.assignmentId)).thenReturn(List.of(persisted));

        fixture.service.submitAnswer(fixture.assignmentId, "same answer", fixture.submissionId);

        verify(fixture.turnRepository, never()).save(any());
        verify(fixture.jobRepository, never()).insertTutorJobIfAbsent(any(), anyString(), anyInt(), any(), any(), any(), any(), anyLong(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void af7_duplicateSubmissionWithConflictingPayloadIsRejectedDeterministically() {
        var fixture = fixture();
        var persisted = question(fixture.assignment);
        persisted.setAnswerText("first answer");
        when(fixture.turnRepository.findByAssignment_IdAndAnswerSubmissionId(fixture.assignmentId, fixture.submissionId)).thenReturn(Optional.of(persisted));

        assertThatThrownBy(() -> fixture.service.submitAnswer(fixture.assignmentId, "different answer", fixture.submissionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The response submission id was already used for a different answer.");
    }

    private static TrainingActivityTurn question(TrainingActivityAssignment assignment) {
        var turn = new TrainingActivityTurn();
        turn.setId(UUID.randomUUID());
        turn.setAssignment(assignment);
        turn.setSequenceNumber(1);
        turn.setQuestionText("¿Qué entiendes?");
        turn.setQuestionCreatedAt(Instant.now());
        return turn;
    }

    private static Fixture fixture() {
        var assignmentId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);
        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        ReflectionTestUtils.setField(assignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findLockedWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var turnRepository = mock(TrainingActivityTurnRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), memberId,
                UUID.randomUUID(), GroupClassMemberKind.STUDENT));
        return new Fixture(new TrainingAssignmentEvaluationService(assignmentRepository, turnRepository, jobRepository, contextResolver),
                assignmentRepository, turnRepository, jobRepository, assignmentId, UUID.randomUUID(), assignment);
    }

    private record Fixture(TrainingAssignmentEvaluationService service, TrainingActivityAssignmentRepository assignmentRepository,
                           TrainingActivityTurnRepository turnRepository, TrainingActivityAiJobRepository jobRepository,
                           UUID assignmentId, UUID submissionId, TrainingActivityAssignment assignment) { }
}
