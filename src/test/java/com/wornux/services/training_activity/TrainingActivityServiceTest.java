package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.config.SocraticEmailProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingActivityServiceTest {

    @Test
    void closeExpiresNonSubmittedAssignmentsAndLeavesSubmittedAssignmentsUntouched() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = activity(activityId, groupClassId);
        var assigned = assignment(TrainingActivityAssignmentStatus.ASSIGNED);
        var started = assignment(TrainingActivityAssignmentStatus.STARTED);
        var submitted = assignment(TrainingActivityAssignmentStatus.SUBMITTED);
        var excused = assignment(TrainingActivityAssignmentStatus.EXCUSED);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.save(activity)).thenAnswer(invocation -> invocation.getArgument(0));

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(
            assignmentRepository.findByTrainingActivity_IdAndStatusNot(
                activityId,
                TrainingActivityAssignmentStatus.SUBMITTED
            )
        ).thenReturn(List.of(assigned, started, excused));
        when(assignmentRepository.saveAll(List.of(assigned, started, excused))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR
            )
        );

        var service = new TrainingActivityService(
            activityRepository,
            assignmentRepository,
            mock(GroupClassMemberRepository.class),
            mock(EmailService.class),
            mock(EmailTemplateService.class),
            new SocraticEmailProperties(),
            contextResolver,
            new TrainingActivityLaunchedBus(),
            null
        );

        service.close(activityId);

        assertThat(ReflectionTestUtils.getField(activity, "status")).isEqualTo(
            TrainingActivityLifecycleStatus.CLOSED
        );
        assertThat(ReflectionTestUtils.getField(activity, "closesAt")).isNotNull();
        assertThat(ReflectionTestUtils.getField(assigned, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXPIRED
        );
        assertThat(ReflectionTestUtils.getField(started, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXPIRED
        );
        assertThat(ReflectionTestUtils.getField(submitted, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.SUBMITTED
        );
        assertThat(ReflectionTestUtils.getField(excused, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXCUSED
        );
    }

    private static TrainingActivity activity(UUID activityId, UUID groupClassId) {
        var groupClass = new GroupClass();
        ReflectionTestUtils.setField(groupClass, "id", groupClassId);

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "groupClass", groupClass);
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        ReflectionTestUtils.setField(activity, "updatedAt", Instant.now());
        return activity;
    }

    private static TrainingActivityAssignment assignment(TrainingActivityAssignmentStatus status) {
        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "status", status);
        ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", true);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        return assignment;
    }
}
