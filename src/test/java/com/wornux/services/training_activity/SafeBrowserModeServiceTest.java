package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.SafeBrowserAlertRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserEventRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SafeBrowserModeServiceTest {

    @Test
    void startSessionRejectsDisabledSafeBrowserWithoutActivating() {
        var fixture = serviceFixture(
                TrainingActivityAssignmentStatus.ASSIGNED,
                TrainingActivityLifecycleStatus.PUBLISHED,
                false,
                false,
                false);

        assertThatThrownBy(() -> fixture.service.startSession(fixture.assignmentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Safe Browser Mode is not enabled for this assignment.");

        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserSessionActive")).isFalse();
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLastHeartbeatAt")).isNull();
        verify(fixture.assignmentRepository, never()).save(any(TrainingActivityAssignment.class));
        verify(fixture.assignmentStateBus, never()).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(fixture.eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(fixture.alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    @Test
    void startSessionDeactivatesSubmittedActiveSessionWithoutLocking() {
        var fixture = serviceFixture(
                TrainingActivityAssignmentStatus.SUBMITTED,
                TrainingActivityLifecycleStatus.PUBLISHED,
                true,
                false,
                true);
        var previousHeartbeat = Instant.now().minusSeconds(60);
        ReflectionTestUtils.setField(fixture.assignment, "safeBrowserLastHeartbeatAt", previousHeartbeat);

        assertThatThrownBy(() -> fixture.service.startSession(fixture.assignmentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Submitted assignments cannot be reopened.");

        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserSessionActive")).isFalse();
        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLocked")).isFalse();
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLastHeartbeatAt")).isEqualTo(previousHeartbeat);
        verify(fixture.assignmentRepository).save(fixture.assignment);
        verify(fixture.assignmentStateBus).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(fixture.eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(fixture.alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    @Test
    void startSessionDeactivatesClosedActivitySessionWithoutLocking() {
        var fixture = serviceFixture(
                TrainingActivityAssignmentStatus.STARTED,
                TrainingActivityLifecycleStatus.CLOSED,
                true,
                false,
                true);

        assertThatThrownBy(() -> fixture.service.startSession(fixture.assignmentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The evaluation window has ended.");

        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserSessionActive")).isFalse();
        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLocked")).isFalse();
        verify(fixture.assignmentRepository).save(fixture.assignment);
        verify(fixture.assignmentStateBus).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(fixture.eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(fixture.alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    @Test
    void startSessionDeactivatesLockedActiveSessionWithoutCreatingAnotherLock() {
        var fixture = serviceFixture(
                TrainingActivityAssignmentStatus.STARTED,
                TrainingActivityLifecycleStatus.PUBLISHED,
                true,
                true,
                true);

        assertThatThrownBy(() -> fixture.service.startSession(fixture.assignmentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Safe Browser Mode was interrupted. Ask your professor to review this assignment.");

        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserSessionActive")).isFalse();
        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLocked")).isTrue();
        verify(fixture.assignmentRepository).save(fixture.assignment);
        verify(fixture.assignmentStateBus).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(fixture.eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(fixture.alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    @Test
    void deactivateSessionClearsActiveSessionWithoutLockingAssignment() {
        var fixture = serviceFixture(
                TrainingActivityAssignmentStatus.STARTED,
                TrainingActivityLifecycleStatus.PUBLISHED,
                true,
                false,
                true);

        var assignment = fixture.service.deactivateSession(fixture.assignmentId);

        assertThat(assignment).isSameAs(fixture.assignment);
        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserSessionActive")).isFalse();
        assertThat((Boolean) ReflectionTestUtils.getField(fixture.assignment, "safeBrowserLocked")).isFalse();
        verify(fixture.assignmentRepository).save(fixture.assignment);
        verify(fixture.assignmentStateBus).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(fixture.eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(fixture.alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    @Test
    void heartbeatDoesNotReactivateSubmittedAssignment() {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var previousHeartbeat = Instant.now().minusSeconds(60);

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "safeBrowserEnabled", true);
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.SUBMITTED);
        ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", true);
        ReflectionTestUtils.setField(assignment, "safeBrowserLastHeartbeatAt", previousHeartbeat);

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenAnswer(invocation -> invocation.getArgument(0));

        var eventRepository = mock(SafeBrowserEventRepository.class);
        var alertRepository = mock(SafeBrowserAlertRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                UUID.randomUUID(),
                GroupClassMemberKind.STUDENT));
        var assignmentStateBus = mock(SafeBrowserAssignmentStateBus.class);
        var service = new SafeBrowserModeService(
                assignmentRepository,
                mock(TrainingActivityRepository.class),
                eventRepository,
                alertRepository,
                contextResolver,
                assignmentStateBus);

        service.recordHeartbeat(assignmentId);

        assertThat((Boolean) ReflectionTestUtils.getField(assignment, "safeBrowserSessionActive")).isFalse();
        assertThat(ReflectionTestUtils.getField(assignment, "safeBrowserLastHeartbeatAt")).isEqualTo(previousHeartbeat);
        verify(assignmentRepository).save(assignment);
        verify(assignmentStateBus).publish(any(SafeBrowserAssignmentStateBus.Notification.class));
        verify(eventRepository, never()).save(any(SafeBrowserEvent.class));
        verify(alertRepository, never()).save(any(SafeBrowserAlert.class));
    }

    private static ServiceFixture serviceFixture(
            TrainingActivityAssignmentStatus assignmentStatus,
            TrainingActivityLifecycleStatus activityStatus,
            boolean safeBrowserEnabled,
            boolean safeBrowserLocked,
            boolean safeBrowserSessionActive) {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "safeBrowserEnabled", safeBrowserEnabled);
        ReflectionTestUtils.setField(activity, "status", activityStatus);

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", assignmentStatus);
        ReflectionTestUtils.setField(assignment, "safeBrowserLocked", safeBrowserLocked);
        ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", safeBrowserSessionActive);

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenAnswer(invocation -> invocation.getArgument(0));

        var eventRepository = mock(SafeBrowserEventRepository.class);
        var alertRepository = mock(SafeBrowserAlertRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                UUID.randomUUID(),
                GroupClassMemberKind.STUDENT));
        var assignmentStateBus = mock(SafeBrowserAssignmentStateBus.class);
        var service = new SafeBrowserModeService(
                assignmentRepository,
                mock(TrainingActivityRepository.class),
                eventRepository,
                alertRepository,
                contextResolver,
                assignmentStateBus);
        return new ServiceFixture(
                service,
                assignmentId,
                assignment,
                assignmentRepository,
                eventRepository,
                alertRepository,
                assignmentStateBus);
    }

    private record ServiceFixture(
            SafeBrowserModeService service,
            UUID assignmentId,
            TrainingActivityAssignment assignment,
            TrainingActivityAssignmentRepository assignmentRepository,
            SafeBrowserEventRepository eventRepository,
            SafeBrowserAlertRepository alertRepository,
            SafeBrowserAssignmentStateBus assignmentStateBus) {}
}
