package com.wornux.specdriven.usecases.uc005_safe_browser_mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
import com.wornux.data.entities.training_activity.SafeBrowserSession;
import com.wornux.data.entities.training_activity.SafeBrowserSessionStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.SafeBrowserAlertRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserEventRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserSessionRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.config.ApplicationProperties;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UC005SafeBrowserMode {

    @Test
    void mainFlow_tokenIsOpaqueAndProfessorUnlockPermitsANewSession() {
        var fixture = fixture();

        var started = fixture.service.beginSession(fixture.assignment.getId());
        var activated = fixture.service.recordHeartbeat(fixture.assignment.getId(), started.token());
        assertThat(activated.isSafeBrowserSessionActive()).isTrue();

        fixture.service.reportViolation(fixture.assignment.getId(), started.token(), SafeBrowserEventType.TAB_HIDDEN, UUID.randomUUID());
        var professorContext = new ActiveAcademicContext(
                UUID.randomUUID(),
                fixture.activity.getCreatedByTenantAccount().getId(),
                fixture.activity.getCreatedByGroupClassMember().getId(),
                fixture.activity.getGroupClass().getId(),
                GroupClassMemberKind.PROFESSOR);
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext);
        when(fixture.contextResolver.resolveCurrent()).thenReturn(Optional.of(professorContext));
        fixture.service.unlockAssignment(fixture.assignment.getId());
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);
        when(fixture.contextResolver.resolveCurrent()).thenReturn(Optional.of(fixture.studentContext));
        var next = fixture.service.beginSession(fixture.assignment.getId());

        assertThat(started.token()).isNotBlank();
        assertThat(fixture.session.get().getTokenHash()).isNotEqualTo(started.token());
        assertThat(next.token()).isNotEqualTo(started.token());
        assertThat(fixture.session.get().getStatus()).isEqualTo(SafeBrowserSessionStatus.PENDING);
        assertThat(fixture.assignment.isSafeBrowserLocked()).isFalse();
    }

    @Test
    void af04_pendingSetupExpiryEndsSessionWithoutViolation() {
        var fixture = fixture();
        fixture.service.beginSession(fixture.assignment.getId());
        fixture.session.get().setCreatedAt(Instant.now().minusSeconds(31));
        when(fixture.sessionRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of(fixture.session.get()));
        when(fixture.sessionRepository.findByStatusAndLastHeartbeatAtBefore(any(), any())).thenReturn(List.of());

        fixture.service.expireStaleSessions();

        assertThat(fixture.session.get().getStatus()).isEqualTo(SafeBrowserSessionStatus.EXPIRED);
        verify(fixture.eventRepository, times(0)).save(any(SafeBrowserEvent.class));
    }

    @Test
    void af06_activeHeartbeatExpiryCreatesOneExpiredIncident() {
        var fixture = fixture();
        var started = fixture.service.beginSession(fixture.assignment.getId());
        fixture.service.recordHeartbeat(fixture.assignment.getId(), started.token());
        fixture.session.get().setLastHeartbeatAt(Instant.now().minusSeconds(31));
        when(fixture.sessionRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());
        when(fixture.sessionRepository.findByStatusAndLastHeartbeatAtBefore(any(), any()))
                .thenReturn(List.of(fixture.session.get()));

        fixture.service.expireStaleSessions();

        assertThat(fixture.session.get().getStatus()).isEqualTo(SafeBrowserSessionStatus.EXPIRED);
        assertThat(fixture.assignment.isSafeBrowserLocked()).isTrue();
    }

    @Test
    void af07_duplicateViolationIdIsIdempotent() {
        var fixture = fixture();
        var started = fixture.service.beginSession(fixture.assignment.getId());
        fixture.service.recordHeartbeat(fixture.assignment.getId(), started.token());
        var clientEventId = UUID.randomUUID();

        fixture.service.reportViolation(fixture.assignment.getId(), started.token(), SafeBrowserEventType.TAB_HIDDEN, clientEventId);
        fixture.service.reportViolation(fixture.assignment.getId(), started.token(), SafeBrowserEventType.TAB_HIDDEN, clientEventId);

        assertThat(fixture.assignment.isSafeBrowserLocked()).isTrue();
        assertThat(fixture.session.get().getStatus()).isEqualTo(SafeBrowserSessionStatus.VIOLATED);
        verify(fixture.alertRepository).save(any(SafeBrowserAlert.class));
        verify(fixture.eventRepository, times(2)).save(any(SafeBrowserEvent.class));
    }

    @Test
    void af08_terminalSessionCannotBeReactivatedByHeartbeat() {
        var fixture = fixture();
        var started = fixture.service.beginSession(fixture.assignment.getId());
        fixture.service.recordHeartbeat(fixture.assignment.getId(), started.token());
        fixture.service.reportViolation(fixture.assignment.getId(), started.token(), SafeBrowserEventType.WINDOW_BLUR, UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> fixture.service.recordHeartbeat(fixture.assignment.getId(), started.token()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.session.get().getStatus()).isEqualTo(SafeBrowserSessionStatus.VIOLATED);
    }

    @Test
    void br05_rejectsAHeartbeatWithTheWrongOpaqueToken() {
        var fixture = fixture();
        fixture.service.beginSession(fixture.assignment.getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> fixture.service.recordHeartbeat(fixture.assignment.getId(), "not-the-issued-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid Safe Browser session token.");
    }

    private static Fixture fixture() {
        var groupClass = new GroupClass();
        ReflectionTestUtils.setField(groupClass, "id", UUID.randomUUID());
        var professorAccount = new TenantAccount();
        ReflectionTestUtils.setField(professorAccount, "id", UUID.randomUUID());
        var professor = new GroupClassMember();
        ReflectionTestUtils.setField(professor, "id", UUID.randomUUID());
        var student = new GroupClassMember();
        ReflectionTestUtils.setField(student, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(student, "groupClass", groupClass);

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "groupClass", groupClass);
        ReflectionTestUtils.setField(activity, "createdByTenantAccount", professorAccount);
        ReflectionTestUtils.setField(activity, "createdByGroupClassMember", professor);
        ReflectionTestUtils.setField(activity, "safeBrowserEnabled", true);
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", student);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());

        var assignmentRepository = org.mockito.Mockito.mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(TrainingActivityAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var sessionRepository = org.mockito.Mockito.mock(SafeBrowserSessionRepository.class);
        var session = new AtomicReference<SafeBrowserSession>();
        when(sessionRepository.save(any(SafeBrowserSession.class))).thenAnswer(invocation -> {
            session.set(invocation.getArgument(0));
            return session.get();
        });
        when(sessionRepository.findFirstByAssignment_IdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(invocation -> {
                    Collection<SafeBrowserSessionStatus> statuses = invocation.getArgument(1);
                    return Optional.ofNullable(session.get()).filter(candidate -> statuses.contains(candidate.getStatus()));
                });
        var eventRepository = org.mockito.Mockito.mock(SafeBrowserEventRepository.class);
        var eventsByClientId = new ConcurrentHashMap<UUID, SafeBrowserEvent>();
        when(eventRepository.findBySession_IdAndClientEventId(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(eventsByClientId.get(invocation.getArgument(1))));
        when(eventRepository.save(any(SafeBrowserEvent.class))).thenAnswer(invocation -> {
            var event = invocation.getArgument(0, SafeBrowserEvent.class);
            eventsByClientId.put(event.getClientEventId(), event);
            return event;
        });
        var alertRepository = org.mockito.Mockito.mock(SafeBrowserAlertRepository.class);
        when(alertRepository.save(any(SafeBrowserAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var activityRepository = org.mockito.Mockito.mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        var contextResolver = org.mockito.Mockito.mock(ActiveAcademicContextResolver.class);
        var studentContext = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), student.getId(), groupClass.getId(), GroupClassMemberKind.STUDENT);
        when(contextResolver.requireCurrent()).thenReturn(studentContext);
        when(contextResolver.resolveCurrent()).thenReturn(Optional.of(studentContext));
        var service = new SafeBrowserModeService(
                assignmentRepository,
                activityRepository,
                sessionRepository,
                eventRepository,
                alertRepository,
                contextResolver,
                new SafeBrowserAssignmentStateBus(),
                new ApplicationProperties.SafeBrowser());
        return new Fixture(service, activity, assignment, studentContext, contextResolver, session, sessionRepository, eventRepository, alertRepository);
    }

    private record Fixture(
            SafeBrowserModeService service,
            TrainingActivity activity,
            TrainingActivityAssignment assignment,
            ActiveAcademicContext studentContext,
            ActiveAcademicContextResolver contextResolver,
            AtomicReference<SafeBrowserSession> session,
            SafeBrowserSessionRepository sessionRepository,
            SafeBrowserEventRepository eventRepository,
            SafeBrowserAlertRepository alertRepository) {}
}
