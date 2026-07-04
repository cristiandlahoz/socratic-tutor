package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.config.SocraticEmailProperties;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import org.springframework.context.annotation.Lazy;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TrainingActivityService {

    private final TrainingActivityRepository trainingActivityRepository;
    private final TrainingActivityAssignmentRepository trainingActivityAssignmentRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final SocraticEmailProperties emailProperties;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingActivityLaunchedBus activityLaunchedBus;
    private final TrainingActivityService self;

    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            SocraticEmailProperties emailProperties,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivityLaunchedBus activityLaunchedBus,
            @Lazy TrainingActivityService self) {
        this.trainingActivityRepository = trainingActivityRepository;
        this.trainingActivityAssignmentRepository = trainingActivityAssignmentRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
        this.emailProperties = emailProperties;
        this.contextResolver = contextResolver;
        this.activityLaunchedBus = activityLaunchedBus;
        this.self = self;
    }

    @Transactional
    public TrainingActivity createPending(String title, String instruction, boolean safeBrowserEnabled) {
        var context = requireProfessorContext();
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(new GroupClass());
        activity.getGroupClass().setId(context.groupClassId());
        activity.setCreatedByTenantAccount(new TenantAccount());
        activity.getCreatedByTenantAccount().setId(context.tenantAccountId());
        activity.setCreatedByGroupClassMember(new GroupClassMember());
        activity.getCreatedByGroupClassMember().setId(context.groupClassMemberId());
        activity.setTitle(title);
        activity.setInstructions(instruction);
        activity.setSafeBrowserEnabled(safeBrowserEnabled);
        activity.setStatus(TrainingActivityLifecycleStatus.DRAFT);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        return trainingActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<TrainingActivity> listAll() {
        return contextResolver.resolveCurrent()
                .map(
                    context -> trainingActivityRepository
                            .findByGroupClass_IdOrderByUpdatedAtDesc(context.groupClassId()))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public TrainingActivity get(UUID activityId) {
        var context = contextResolver.requireCurrent();
        return trainingActivityRepository.findById(activityId)
                .filter(
                    activity -> activity.getGroupClass() != null
                            && context.groupClassId().equals(activity.getGroupClass().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training activity %s".formatted(activityId)));
    }

    @Transactional(readOnly = true)
    public List<TrainingActivityAssignment> listAssignments(UUID activityId) {
        var activity = get(activityId);
        return trainingActivityAssignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activity.getId());
    }

    @Transactional
    public TrainingActivity saveQuestions(UUID activityId, String questionsJson) {
        throw new SetupRequiredException(
                "Question persistence is not supported because the training activity model does not define it yet.");
    }

    @Transactional
    public TrainingActivity markRunning(UUID activityId) {
        throw new SetupRequiredException("Training activity execution is not supported yet.");
    }

    @Transactional
    public TrainingActivity saveAnswers(UUID activityId, String answersJson) {
        throw new SetupRequiredException(
                "Answer persistence is not supported because the training activity assignment model does not define it yet.");
    }

    @Transactional
    public TrainingActivity completeReport(UUID activityId, String reportMarkdown) {
        throw new SetupRequiredException(
                "Report persistence is not supported because the training activity model does not define it yet.");
    }

    @Transactional
    public TrainingActivity markFailed(UUID activityId) {
        throw new SetupRequiredException(
                "Execution failure persistence is not supported because the training activity model does not define it yet.");
    }

    @Transactional
    public void delete(UUID activityId) {
        trainingActivityRepository.delete(self.get(activityId));
    }

    @Transactional
    public TrainingActivity update(UUID activityId, String title, String instruction) {
        var activity = self.get(activityId);
        return update(activityId, title, instruction, activity.isSafeBrowserEnabled());
    }

    @Transactional
    public TrainingActivity update(UUID activityId, String title, String instruction, boolean safeBrowserEnabled) {
        var activity = self.get(activityId);
        if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT
                && activity.isSafeBrowserEnabled() != safeBrowserEnabled) {
            throw new IllegalStateException("Safe Browser Mode can only be changed before launch.");
        }
        activity.setTitle(title);
        activity.setInstructions(instruction);
        activity.setSafeBrowserEnabled(safeBrowserEnabled);
        activity.setUpdatedAt(Instant.now());
        return trainingActivityRepository.save(activity);
    }

    @Transactional
    public int launch(UUID activityId) {
        var activity = get(activityId);
        if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft training activities can be launched.");
        }
        trainingActivityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(
                activity.getCreatedByTenantAccount().getId(), TrainingActivityLifecycleStatus.PUBLISHED)
                .filter(active -> !active.getId().equals(activity.getId()))
                .ifPresent(_ -> {
                    throw new IllegalStateException("A professor can evaluate only one group/activity at a time.");
                });

        var now = Instant.now();
        var students = groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(
                activity.getGroupClass().getId())
                .stream()
                .filter(member -> member.getMemberKind() == GroupClassMemberKind.STUDENT)
                .toList();

        var assignments = new ArrayList<TrainingActivityAssignment>(students.size());
        if (students.isEmpty()) {
            throw new IllegalStateException("There are no eligible students to assign.");
        }
        for (var student : students) {
            var assignment = new TrainingActivityAssignment();
            assignment.setId(UUID.randomUUID());
            assignment.setTrainingActivity(activity);
            assignment.setGroupClassMember(student);
            assignment.setStatus(TrainingActivityAssignmentStatus.ASSIGNED);
            assignment.setAssignedAt(now);
            assignment.setUpdatedAt(now);
            assignments.add(assignment);
        }
        if (!assignments.isEmpty()) {
            trainingActivityAssignmentRepository.saveAll(assignments);
        }

        activity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        activity.setOpensAt(now);
        activity.setUpdatedAt(now);
        trainingActivityRepository.save(activity);

        var messages = launchMessages(activity, students);
        var notification = new TrainingActivityLaunchedBus.Notification(
                activity.getId(),
                activity.getGroupClass().getId(),
                students.stream().map(GroupClassMember::getId).collect(Collectors.toUnmodifiableSet()));
        sendAfterCommit(messages);
        publishAfterCommit(notification);
        return students.size();
    }

    @Transactional
    public TrainingActivity close(UUID activityId) {
        var activity = get(activityId);
        if (activity.getStatus() != TrainingActivityLifecycleStatus.PUBLISHED) {
            throw new IllegalStateException("Only published training activities can be closed.");
        }
        var now = Instant.now();
        activity.setStatus(TrainingActivityLifecycleStatus.CLOSED);
        activity.setClosesAt(now);
        activity.setUpdatedAt(now);
        var nonSubmittedAssignments = trainingActivityAssignmentRepository.findByTrainingActivity_IdAndStatusNot(
                activity.getId(), TrainingActivityAssignmentStatus.SUBMITTED);
        for (var assignment : nonSubmittedAssignments) {
            if (!assignment.getStatus().isTerminal()) {
                assignment.setStatus(TrainingActivityAssignmentStatus.EXPIRED);
            }
            assignment.setSafeBrowserSessionActive(false);
            assignment.setUpdatedAt(now);
        }
        if (!nonSubmittedAssignments.isEmpty()) {
            trainingActivityAssignmentRepository.saveAll(nonSubmittedAssignments);
        }
        return trainingActivityRepository.save(activity);
    }

    private void publishAfterCommit(TrainingActivityLaunchedBus.Notification notification) {
        if (notification.groupClassMemberIds().isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            activityLaunchedBus.publish(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                activityLaunchedBus.publish(notification);
            }
        });
    }

    private List<EmailMessage> launchMessages(TrainingActivity activity, List<GroupClassMember> students) {
        var studentHomeUrl = "%s/student".formatted(emailProperties.getInvitationBaseUrl());
        var subject = "New formative activity: %s".formatted(activity.getTitle());
        var plainText = launchPlainText(activity, studentHomeUrl);
        return students.stream()
                .map(student -> launchMessage(activity, student, subject, plainText, studentHomeUrl))
                .toList();
    }

    private EmailMessage launchMessage(
            TrainingActivity activity,
            GroupClassMember student,
            String subject,
            String plainText,
            String studentHomeUrl) {
        var model = new HashMap<String, Object>();
        model.put("headline", "Complete %s".formatted(activity.getTitle()));
        model.put("intro", "A new formative activity is available for %s.".formatted(activity.getGroupClass().getName()));
        model.put("activityTitle", activity.getTitle());
        model.put("instructions", activity.getInstructions());
        model.put("activityUrl", studentHomeUrl);

        var toAddress = student.getTenantAccount().getAccount().getEmail();
        var html = emailTemplateService
                .render(new TemplatedEmailMessage(toAddress, subject, "training-activity-invitation", model));
        return new EmailMessage(toAddress, subject, plainText, html);
    }

    private void sendAfterCommit(List<EmailMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(messages);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(messages);
            }
        });
    }

    private void send(List<EmailMessage> messages) {
        for (var message : messages) {
            emailService.send(message);
        }
    }

    private ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SetupRequiredException(
                    "An active professor class context is required before managing training activities.");
        }
        return context;
    }

    private String launchPlainText(TrainingActivity activity, String studentHomeUrl) {
        return """
               Hello,

               A new formative activity is available for %s.

               Title: %s
               Instructions:
               %s

               Open your student home:
               %s
               """.formatted(activity.getGroupClass().getName(), activity.getTitle(), activity.getInstructions(), studentHomeUrl);
    }

}
