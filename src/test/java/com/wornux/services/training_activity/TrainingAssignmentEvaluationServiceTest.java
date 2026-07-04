package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class TrainingAssignmentEvaluationServiceTest {

    @Test
    void startInitializesAssignedEvaluation() {
        var fixture = fixture();

        var assignment = fixture.service.start(fixture.assignmentId);

        assertThat(
            ReflectionTestUtils.getField(assignment, "status")
        ).isEqualTo(TrainingActivityAssignmentStatus.STARTED);
        assertThat(
            ReflectionTestUtils.getField(assignment, "questionCount")
        ).isEqualTo(1);
        assertThat(
            (String) ReflectionTestUtils.getField(assignment, "currentQuestion")
        ).isNotBlank();
        assertThat(
            ReflectionTestUtils.getField(assignment, "startedAt")
        ).isNotNull();
    }

    @Test
    void answerPersistsTranscriptAndAdvancesQuestion() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);

        var assignment = fixture.service.answer(
            fixture.assignmentId,
            "I understand the basics."
        );

        assertThat(
            (String) ReflectionTestUtils.getField(
                assignment,
                "evaluationTranscript"
            )
        ).contains("I understand the basics.");
        assertThat(
            ReflectionTestUtils.getField(assignment, "questionCount")
        ).isEqualTo(2);
        assertThat(
            (String) ReflectionTestUtils.getField(assignment, "currentQuestion")
        ).isNotBlank();
    }

    @Test
    void answerRejectsBlankInput() {
        var fixture = fixture();

        assertThatThrownBy(() ->
            fixture.service.answer(fixture.assignmentId, "   ")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evaluation answers cannot be blank.");
    }

    @Test
    void answerClosesPublishedActivityWhenAllAssignmentsAreTerminal() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        fixture.service.answer(fixture.assignmentId, "First answer");
        fixture.service.answer(fixture.assignmentId, "Second answer");

        var assignment = fixture.service.answer(
            fixture.assignmentId,
            "Final answer"
        );

        assertThat(assignment.getStatus()).isEqualTo(
            TrainingActivityAssignmentStatus.SUBMITTED
        );
        assertThat(assignment.getTrainingActivity().getStatus()).isEqualTo(
            TrainingActivityLifecycleStatus.CLOSED
        );
        assertThat(assignment.getTrainingActivity().getClosesAt()).isNotNull();
        verify(fixture.activityRepository).save(assignment.getTrainingActivity());
    }

    @Test
    void answerRejectsExpiredAssignment() {
        var fixture = fixture();
        fixture.assignment.setStatus(TrainingActivityAssignmentStatus.EXPIRED);

        assertThatThrownBy(() ->
            fixture.service.answer(fixture.assignmentId, "Too late")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The evaluation assignment has ended.");
    }

    private static Fixture fixture() {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "title", "Pointers");
        ReflectionTestUtils.setField(
            activity,
            "status",
            TrainingActivityLifecycleStatus.PUBLISHED
        );

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(
            assignment,
            "status",
            TrainingActivityAssignmentStatus.ASSIGNED
        );
        ReflectionTestUtils.setField(assignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());

        var repository = mock(TrainingActivityAssignmentRepository.class);
        when(repository.findWithTrainingActivityById(assignmentId)).thenReturn(
            Optional.of(assignment)
        );
        when(
            repository.findByTrainingActivity_IdOrderByUpdatedAtDesc(
                activityId
            )
        ).thenAnswer(invocation -> List.of(assignment));
        when(repository.save(assignment)).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(activity)).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                groupClassId,
                GroupClassMemberKind.STUDENT
            )
        );

        var service = new TrainingAssignmentEvaluationService(
            repository,
            activityRepository,
            contextResolver,
            new TrainingAssignmentTutorService(),
            new JsonMapper()
        );
        return new Fixture(service, activityRepository, assignment, assignmentId);
    }

    private record Fixture(
        TrainingAssignmentEvaluationService service,
        TrainingActivityRepository activityRepository,
        TrainingActivityAssignment assignment,
        UUID assignmentId
    ) {}
}
