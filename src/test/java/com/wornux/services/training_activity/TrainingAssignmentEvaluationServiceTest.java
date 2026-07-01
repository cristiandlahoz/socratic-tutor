package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingAssignmentEvaluationServiceTest {

    @Test
    void startInitializesAssignedEvaluation() {
        var fixture = fixture();

        var assignment = fixture.service.start(fixture.assignmentId);

        assertThat(ReflectionTestUtils.getField(assignment, "status")).isEqualTo(TrainingActivityAssignmentStatus.STARTED);
        assertThat(ReflectionTestUtils.getField(assignment, "questionCount")).isEqualTo(1);
        assertThat((String) ReflectionTestUtils.getField(assignment, "currentQuestion")).isNotBlank();
        assertThat(ReflectionTestUtils.getField(assignment, "startedAt")).isNotNull();
    }

    @Test
    void answerPersistsTranscriptAndAdvancesQuestion() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);

        var assignment = fixture.service.answer(fixture.assignmentId, "I understand the basics.");

        assertThat((String) ReflectionTestUtils.getField(assignment, "evaluationTranscript"))
                .contains("I understand the basics.");
        assertThat(ReflectionTestUtils.getField(assignment, "questionCount")).isEqualTo(2);
        assertThat((String) ReflectionTestUtils.getField(assignment, "currentQuestion")).isNotBlank();
    }

    private static Fixture fixture() {
        var assignmentId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Pointers");

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.ASSIGNED);
        ReflectionTestUtils.setField(assignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());

        var repository = mock(TrainingActivityAssignmentRepository.class);
        when(repository.findWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(assignment));
        when(repository.save(assignment)).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
            UUID.randomUUID(), UUID.randomUUID(), memberId, groupClassId, GroupClassMemberRole.STUDENT));

        var service = new TrainingAssignmentEvaluationService(
                repository, contextResolver, new TrainingAssignmentTutorService(), new ObjectMapper());
        return new Fixture(service, assignmentId);
    }

    private record Fixture(TrainingAssignmentEvaluationService service, UUID assignmentId) {}
}
