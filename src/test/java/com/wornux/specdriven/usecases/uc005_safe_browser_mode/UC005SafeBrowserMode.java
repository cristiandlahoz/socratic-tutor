package com.wornux.specdriven.usecases.uc005_safe_browser_mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserAlertStatus;
import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import com.wornux.data.entities.training_activity.SafeBrowserEventSeverity;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
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
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.services.training_activity.TrainingAssignmentTutorService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UC005SafeBrowserMode {

    @Test
    void mainFlow_safeBrowserViolationLocksOnlyAffectedAssignmentAndProfessorCanUnlock() {
        var fixture = fixture();
        var notifications = new ArrayList<SafeBrowserAssignmentStateBus.Notification>();
        fixture.assignmentStateBus.subscribe(notifications::add);
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);
        when(fixture.contextResolver.resolveCurrent()).thenReturn(Optional.of(fixture.studentContext));

        fixture.safeBrowserModeService.startSession(fixture.assignment.getId());
        var locked = fixture.safeBrowserModeService.reportViolation(
                fixture.assignment.getId(), SafeBrowserEventType.TAB_HIDDEN);

        assertThat(locked.isSafeBrowserLocked()).isTrue();
        assertThat(locked.getSafeBrowserLockReason()).isEqualTo(SafeBrowserEventType.TAB_HIDDEN.name());
        assertThat(fixture.otherAssignment.isSafeBrowserLocked()).isFalse();
        assertThat(notifications).extracting(SafeBrowserAssignmentStateBus.Notification::locked).contains(true);
        verify(fixture.alertRepository).save(any(SafeBrowserAlert.class));

        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.professorContext);
        when(fixture.contextResolver.resolveCurrent()).thenReturn(Optional.of(fixture.professorContext));
        var unlocked = fixture.safeBrowserModeService.unlockAssignment(fixture.assignment.getId());

        assertThat(unlocked.isSafeBrowserLocked()).isFalse();
        assertThat(notifications).extracting(SafeBrowserAssignmentStateBus.Notification::locked).contains(false);
        verify(fixture.eventRepository, times(3)).save(any(SafeBrowserEvent.class));
    }

    @Test
    void br15_answerRejectedWhenSafeBrowserSessionIsNotActive() {
        var fixture = fixture();
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);

        assertThatThrownBy(() -> fixture.evaluationService.answer(fixture.assignment.getId(), "My answer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Safe Browser Mode must be active before answering.");
    }

    @Test
    void br30_backendRejectsAnswerAfterSafeBrowserLockEvenIfUiFails() {
        var fixture = fixture();
        fixture.assignment.setSafeBrowserLocked(true);
        fixture.assignment.setSafeBrowserSessionActive(true);
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);

        assertThatThrownBy(() -> fixture.evaluationService.answer(fixture.assignment.getId(), "My answer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Safe Browser Mode was interrupted. Ask your professor to review this assignment.");
    }

    @Test
    void br11_closedParentActivityOverridesSafeBrowserUnlock() {
        var fixture = fixture();
        fixture.activity.setStatus(TrainingActivityLifecycleStatus.CLOSED);
        fixture.assignment.setSafeBrowserSessionActive(true);
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);

        assertThatThrownBy(() -> fixture.evaluationService.answer(fixture.assignment.getId(), "My answer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The evaluation window has ended.");
    }

    @Test
    void af22_repeatedViolationsKeepSingleOpenGroupedAlert() {
        var fixture = fixture();
        var existingAlert = new SafeBrowserAlert();
        existingAlert.setId(UUID.randomUUID());
        existingAlert.setTrainingActivity(fixture.activity);
        existingAlert.setProfessorTenantAccount(fixture.professorTenantAccount);
        existingAlert.setStatus(SafeBrowserAlertStatus.OPEN);
        existingAlert.setIncidentCount(1);
        existingAlert.setLastEventAt(Instant.now());
        existingAlert.setCreatedAt(Instant.now());
        existingAlert.setUpdatedAt(Instant.now());
        when(fixture.alertRepository.findByProfessorTenantAccount_IdAndTrainingActivity_IdAndStatus(
                fixture.professorTenantAccount.getId(), fixture.activity.getId(), SafeBrowserAlertStatus.OPEN))
                .thenReturn(Optional.empty(), Optional.of(existingAlert));
        when(fixture.contextResolver.requireCurrent()).thenReturn(fixture.studentContext);
        when(fixture.contextResolver.resolveCurrent()).thenReturn(Optional.of(fixture.studentContext));

        fixture.safeBrowserModeService.reportViolation(fixture.assignment.getId(), SafeBrowserEventType.TAB_HIDDEN);
        fixture.safeBrowserModeService.reportViolation(fixture.assignment.getId(), SafeBrowserEventType.WINDOW_BLUR);

        assertThat(existingAlert.getIncidentCount()).isEqualTo(2);
        verify(fixture.alertRepository, times(2)).save(any(SafeBrowserAlert.class));
    }

    private static Fixture fixture() {
        var groupClassId = UUID.randomUUID();
        var professorMemberId = UUID.randomUUID();
        var studentMemberId = UUID.randomUUID();
        var otherStudentMemberId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var assignmentId = UUID.randomUUID();
        var otherAssignmentId = UUID.randomUUID();

        var groupClass = new GroupClass();
        groupClass.setId(groupClassId);

        var professorTenantAccount = new TenantAccount();
        professorTenantAccount.setId(UUID.randomUUID());

        var professorMember = new GroupClassMember();
        professorMember.setId(professorMemberId);

        var activity = new TrainingActivity();
        activity.setId(activityId);
        activity.setGroupClass(groupClass);
        activity.setCreatedByTenantAccount(professorTenantAccount);
        activity.setCreatedByGroupClassMember(professorMember);
        activity.setTitle("Safe Browser activity");
        activity.setInstructions("Stay in the protected session.");
        activity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        activity.setSafeBrowserEnabled(true);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());

        var studentMember = new GroupClassMember();
        studentMember.setId(studentMemberId);
        studentMember.setGroupClass(groupClass);

        var assignment = assignment(assignmentId, activity, studentMember);

        var otherStudentMember = new GroupClassMember();
        otherStudentMember.setId(otherStudentMemberId);
        otherStudentMember.setGroupClass(groupClass);
        var otherAssignment = assignment(otherAssignmentId, activity, otherStudentMember);

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(TrainingActivityAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        var eventRepository = mock(SafeBrowserEventRepository.class);
        when(eventRepository.save(any(SafeBrowserEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var alertRepository = mock(SafeBrowserAlertRepository.class);
        when(alertRepository.findByProfessorTenantAccount_IdAndTrainingActivity_IdAndStatus(
                professorTenantAccount.getId(), activityId, SafeBrowserAlertStatus.OPEN))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(SafeBrowserAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var studentContext = new ActiveAcademicContext(
                UUID.randomUUID(), UUID.randomUUID(), studentMemberId, groupClassId, GroupClassMemberKind.STUDENT);
        var professorContext = new ActiveAcademicContext(
                UUID.randomUUID(), professorTenantAccount.getId(), professorMemberId, groupClassId, GroupClassMemberKind.PROFESSOR);
        var assignmentStateBus = new SafeBrowserAssignmentStateBus();

        var safeBrowserModeService = new SafeBrowserModeService(
                assignmentRepository,
                activityRepository,
                eventRepository,
                alertRepository,
                contextResolver,
                assignmentStateBus);
        var evaluationService = new TrainingAssignmentEvaluationService(
                assignmentRepository, activityRepository, contextResolver, new TrainingAssignmentTutorService(), new JsonMapper());

        return new Fixture(
                safeBrowserModeService,
                evaluationService,
                assignmentRepository,
                eventRepository,
                alertRepository,
                assignmentStateBus,
                contextResolver,
                studentContext,
                professorContext,
                professorTenantAccount,
                activity,
                assignment,
                otherAssignment);
    }

    private static TrainingActivityAssignment assignment(
            UUID assignmentId, TrainingActivity activity, GroupClassMember studentMember) {
        var assignment = new TrainingActivityAssignment();
        assignment.setId(assignmentId);
        assignment.setTrainingActivity(activity);
        assignment.setGroupClassMember(studentMember);
        assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
        assignment.setAssignedAt(Instant.now());
        assignment.setStartedAt(Instant.now());
        assignment.setCurrentQuestion("What is your initial understanding?");
        assignment.setQuestionCount(1);
        assignment.setEvaluationTranscript("[]");
        assignment.setUpdatedAt(Instant.now());
        return assignment;
    }

    private record Fixture(
            SafeBrowserModeService safeBrowserModeService,
            TrainingAssignmentEvaluationService evaluationService,
            TrainingActivityAssignmentRepository assignmentRepository,
            SafeBrowserEventRepository eventRepository,
            SafeBrowserAlertRepository alertRepository,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            ActiveAcademicContextResolver contextResolver,
            ActiveAcademicContext studentContext,
            ActiveAcademicContext professorContext,
            TenantAccount professorTenantAccount,
            TrainingActivity activity,
            TrainingActivityAssignment assignment,
            TrainingActivityAssignment otherAssignment) {}
}
