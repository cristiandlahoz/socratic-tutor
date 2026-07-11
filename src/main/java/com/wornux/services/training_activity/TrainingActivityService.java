package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewCoordinator;
import com.wornux.services.training_activity.instruction_review.InstructionReviewExecutionStatus;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.services.training_activity.instruction_review.InstructionReviewUnavailableException;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewOverrideAction;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TrainingActivityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingActivityService.class);

    private final TrainingActivityRepository trainingActivityRepository;
    private final TrainingActivityAssignmentRepository trainingActivityAssignmentRepository;
    private final com.wornux.data.repositories.training_activity.OutboxEventRepository outboxEventRepository;
    private final com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository outboxRecipientDeliveryRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final ApplicationProperties.Email emailProperties;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingActivityLaunchedBus activityLaunchedBus;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final InstructionReviewCoordinator instructionReviewCoordinator;
    private final AdvisoryInstructionReviewService advisoryInstructionReviewService;
    private final TrainingActivityService self;

    @Autowired
    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository,
            com.wornux.data.repositories.training_activity.OutboxEventRepository outboxEventRepository,
            com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository outboxRecipientDeliveryRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            ApplicationProperties applicationProperties,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivityLaunchedBus activityLaunchedBus,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            InstructionReviewCoordinator instructionReviewCoordinator,
            AdvisoryInstructionReviewService advisoryInstructionReviewService,
            @Lazy TrainingActivityService self) {
        this.trainingActivityRepository = trainingActivityRepository;
        this.trainingActivityAssignmentRepository = trainingActivityAssignmentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxRecipientDeliveryRepository = outboxRecipientDeliveryRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
        this.emailProperties = applicationProperties.getEmail();
        this.contextResolver = contextResolver;
        this.activityLaunchedBus = activityLaunchedBus;
        this.assignmentStateBus = assignmentStateBus;
        this.instructionReviewCoordinator = instructionReviewCoordinator;
        this.advisoryInstructionReviewService = advisoryInstructionReviewService;
        this.self = self;
    }

    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            ApplicationProperties applicationProperties,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivityLaunchedBus activityLaunchedBus,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            InstructionReviewCoordinator instructionReviewCoordinator,
            AdvisoryInstructionReviewService advisoryInstructionReviewService,
            @Lazy TrainingActivityService self) {
        this(trainingActivityRepository, trainingActivityAssignmentRepository, null, null, groupClassMemberRepository,
                emailService, emailTemplateService, applicationProperties, contextResolver, activityLaunchedBus,
                assignmentStateBus, instructionReviewCoordinator, advisoryInstructionReviewService, self);
    }

    /** Compatibility constructor retained for existing isolated unit fixtures. */
    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            ApplicationProperties applicationProperties,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivityLaunchedBus activityLaunchedBus,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            InstructionReviewCoordinator instructionReviewCoordinator,
            @Lazy TrainingActivityService self) {
        this(trainingActivityRepository, trainingActivityAssignmentRepository, groupClassMemberRepository,
                emailService, emailTemplateService, applicationProperties, contextResolver, activityLaunchedBus,
                assignmentStateBus, instructionReviewCoordinator, null, self);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_CREATE)
    public TrainingActivity createPending(String title, String instruction, boolean safeBrowserEnabled) {
        return createPending(new TrainingActivitySaveCommand(title, instruction, safeBrowserEnabled));
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_CREATE)
    public TrainingActivity createPending(TrainingActivitySaveCommand command) {
        LOGGER.info(
                "createPending started: titleLength={} instructionLength={} safeBrowserEnabled={}",
                command.title() == null ? 0 : command.title().trim().length(),
                command.instructions() == null ? 0 : command.instructions().trim().length(),
                command.safeBrowserEnabled());
        var context = requireProfessorContext();
        LOGGER.info(
                "createPending resolved professor context: groupClassId={} tenantAccountId={} groupClassMemberId={}",
                context.groupClassId(),
                context.tenantAccountId(),
                context.groupClassMemberId());
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(new GroupClass());
        activity.getGroupClass().setId(context.groupClassId());
        activity.setCreatedByTenantAccount(new TenantAccount());
        activity.getCreatedByTenantAccount().setId(context.tenantAccountId());
        activity.setCreatedByGroupClassMember(new GroupClassMember());
        activity.getCreatedByGroupClassMember().setId(context.groupClassMemberId());
        activity.setTitle(command.title());
        activity.setInstructions(command.instructions());
        activity.setSafeBrowserEnabled(command.safeBrowserEnabled());
        activity.setStatus(TrainingActivityLifecycleStatus.DRAFT);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        LOGGER.info("createPending built draft entity: activityId={}", activity.getId());
        validateRequiredFields(command);
        LOGGER.info("createPending passed required-fields validation: activityId={}", activity.getId());
        if (advisoryInstructionReviewService == null) {
            var decision = reviewAdvisory(null, command.title(), command.instructions());
            ensureReviewAllowsSave(command, decision);
            instructionReviewCoordinator.applyPersistedReview(activity, decision.snapshot(), decision.reviewResult());
            return trainingActivityRepository.save(activity);
        }
        var saved = trainingActivityRepository.save(activity);
        if (advisoryInstructionReviewService != null) {
            var candidateId = command.reviewCandidateId() == null ? saved.getId() : command.reviewCandidateId();
            var snapshot = advisoryInstructionReviewService.request(
                    candidateId, saved, context.groupClassId(), context.groupClassMemberId(), command.title(), command.instructions());
            if (command.confirmedReviewHash() != null && !command.confirmedReviewHash().isBlank()) {
                ensureAsyncReviewAllowsSave(command, snapshot);
                advisoryInstructionReviewService.recordOverride(saved, context.groupClassMemberId(), command.instructions(), InstructionReviewOverrideAction.SAVE_DRAFT);
            }
        }
        LOGGER.info("createPending saved draft successfully: activityId={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_VIEW)
    public List<TrainingActivity> listAll() {
        return trainingActivityRepository.findByGroupClass_IdOrderByUpdatedAtDesc(requireProfessorContext().groupClassId());
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_VIEW)
    public TrainingActivity get(UUID activityId) {
        var context = requireProfessorContext();
        return trainingActivityRepository.findById(activityId)
                .filter(
                    activity -> activity.getGroupClass() != null
                            && context.groupClassId().equals(activity.getGroupClass().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training activity %s".formatted(activityId)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_VIEW)
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
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_DELETE)
    public void delete(UUID activityId) {
        trainingActivityRepository.delete(self.get(activityId));
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public TrainingActivity update(UUID activityId, String title, String instruction) {
        var activity = self.get(activityId);
        return update(activityId, new TrainingActivitySaveCommand(title, instruction, activity.isSafeBrowserEnabled()));
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public TrainingActivity update(UUID activityId, String title, String instruction, boolean safeBrowserEnabled) {
        return update(activityId, new TrainingActivitySaveCommand(title, instruction, safeBrowserEnabled));
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public TrainingActivity update(UUID activityId, TrainingActivitySaveCommand command) {
        var activity = self.get(activityId);
        if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft training activities can be updated.");
        }
        validateRequiredFields(command);
        if (advisoryInstructionReviewService == null) {
            var decision = reviewAdvisory(activity, command.title(), command.instructions());
            ensureReviewAllowsSave(command, decision);
            activity.setTitle(command.title());
            activity.setInstructions(command.instructions());
            activity.setSafeBrowserEnabled(command.safeBrowserEnabled());
            activity.setUpdatedAt(Instant.now());
            instructionReviewCoordinator.applyPersistedReview(activity, decision.snapshot(), decision.reviewResult());
            return trainingActivityRepository.save(activity);
        }
        var context = requireProfessorContext();
        var snapshot = advisoryInstructionReviewService.request(
                command.reviewCandidateId() == null ? activity.getId() : command.reviewCandidateId(),
                activity,
                context.groupClassId(),
                context.groupClassMemberId(),
                command.title(),
                command.instructions());
        ensureAsyncReviewAllowsSave(command, snapshot);
        activity.setTitle(command.title());
        activity.setInstructions(command.instructions());
        activity.setSafeBrowserEnabled(command.safeBrowserEnabled());
        activity.setUpdatedAt(Instant.now());
        var saved = trainingActivityRepository.save(activity);
        if (!snapshot.isSaveableGoodReview()) {
            advisoryInstructionReviewService.recordOverride(
                    saved, context.groupClassMemberId(), command.instructions(), InstructionReviewOverrideAction.SAVE_DRAFT);
        }
        return saved;
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public int launch(UUID activityId) {
        return launch(activityId, get(activityId).getVersion(), false);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public int launch(UUID activityId, boolean publishAnyway) {
        return launch(activityId, get(activityId).getVersion(), publishAnyway);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public int launch(UUID activityId, long expectedVersion, boolean publishAnyway) {
        var activity = get(activityId);
        if (activity.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED) {
            return (int) trainingActivityAssignmentRepository.countByTrainingActivity_Id(activityId);
        }
        if (activity.getVersion() != expectedVersion) {
            throw new IllegalStateException("The activity changed. Refresh the publication confirmation and try again.");
        }
        if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft training activities can be launched.");
        }
        if (advisoryInstructionReviewService == null) {
            refreshInstructionReviewAdvisory(activity);
            ensureInstructionReviewAllowsLaunch(activity);
        }
        else {
            var snapshot = advisoryInstructionReviewService.current(activity.getId(), activity.getInstructions());
            if (!snapshot.isSaveableGoodReview() && !publishAnyway) {
                throw new IllegalStateException("AI review is advisory. Confirm Publish anyway to continue.");
            }
            if (!snapshot.isSaveableGoodReview()) {
                advisoryInstructionReviewService.recordOverride(activity, requireProfessorContext().groupClassMemberId(),
                        activity.getInstructions(), InstructionReviewOverrideAction.PUBLISH);
            }
        }
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
        activity.setPublishedAt(now);
        activity.setUpdatedAt(now);
        trainingActivityRepository.save(activity);

        persistPublicationOutbox(activity, students, now);

        var notification = new TrainingActivityLaunchedBus.Notification(
                activity.getId(),
                activity.getGroupClass().getId(),
                students.stream().map(GroupClassMember::getId).collect(Collectors.toUnmodifiableSet()));
        publishAfterCommit(notification);
        return students.size();
    }

    @Transactional(readOnly = true)
    public int eligibleStudentCount(UUID activityId) {
        var activity = get(activityId);
        return (int) groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId())
                .stream().filter(member -> member.getMemberKind() == GroupClassMemberKind.STUDENT).count();
    }

    private void persistPublicationOutbox(TrainingActivity activity, List<GroupClassMember> students, Instant now) {
        if (outboxEventRepository == null || outboxRecipientDeliveryRepository == null) {
            return;
        }
        var event = new com.wornux.data.entities.training_activity.OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("TRAINING_ACTIVITY");
        event.setAggregateId(activity.getId());
        event.setEventType("ACTIVITY_PUBLISHED");
        event.setDeduplicationKey("activity-published:" + activity.getId());
        event.setStatus(com.wornux.data.entities.training_activity.OutboxEventStatus.PENDING);
        event.setAvailableAt(now);
        event.setCreatedAt(now);
        outboxEventRepository.save(event);
        var deliveries = students.stream().map(student -> {
            var delivery = new com.wornux.data.entities.training_activity.OutboxRecipientDelivery();
            delivery.setId(UUID.randomUUID());
            delivery.setOutboxEvent(event);
            delivery.setGroupClassMember(student);
            delivery.setIdempotencyKey("activity-published:" + activity.getId() + ":" + student.getId());
            delivery.setStatus(com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus.PENDING);
            delivery.setAvailableAt(now);
            delivery.setCreatedAt(now);
            return delivery;
        }).toList();
        outboxRecipientDeliveryRepository.saveAll(deliveries);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
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
        var saved = trainingActivityRepository.save(activity);
        publishAssignmentStateAfterCommit(trainingActivityAssignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activity.getId()));
        return saved;
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

    private void publishAssignmentStateAfterCommit(List<TrainingActivityAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        var notifications = assignments.stream()
                .map(assignment -> new SafeBrowserAssignmentStateBus.Notification(
                        assignment.getTrainingActivity().getId(),
                        assignment.getId(),
                        assignment.getGroupClassMember().getId(),
                        assignment.isSafeBrowserLocked(),
                        assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED))
                .toList();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifications.forEach(assignmentStateBus::publish);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifications.forEach(assignmentStateBus::publish);
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

    @Transactional(readOnly = true)
    public InstructionReviewSnapshotDto getInstructionReviewSnapshot(UUID activityId) {
        var activity = get(activityId);
        return advisoryInstructionReviewService == null
                ? instructionReviewCoordinator.snapshot(activity)
                : advisoryInstructionReviewService.current(activityId, activity.getInstructions());
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_CREATE)
    public InstructionReviewSnapshotDto reviewDraft(TrainingActivitySaveCommand command) {
        validateRequiredFields(command);
        var context = requireProfessorContext();
        if (advisoryInstructionReviewService != null) {
            var candidateId = command.reviewCandidateId() == null ? UUID.randomUUID() : command.reviewCandidateId();
            return advisoryInstructionReviewService.request(candidateId, null, context.groupClassId(), context.groupClassMemberId(), command.title(), command.instructions());
        }
        return reviewAdvisory(null, command.title(), command.instructions()).snapshot();
    }

    private ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SetupRequiredException(
                    "An active professor class context is required before managing training activities.");
        }
        return context;
    }

    private void validateRequiredFields(TrainingActivitySaveCommand command) {
        if (command.title() == null || command.title().isBlank() || command.instructions() == null
                || command.instructions().isBlank()) {
            throw new IllegalArgumentException("Title and instructions are required.");
        }
    }

    private InstructionReviewCoordinator.ReviewBeforeSaveDecision reviewAdvisory(
            TrainingActivity currentActivity,
            String title,
            String instructions) {
        try {
            return instructionReviewCoordinator.reviewBeforeSave(currentActivity, title, instructions);
        }
        catch (InstructionReviewUnavailableException exception) {
            var snapshot = instructionReviewCoordinator.unavailableSnapshot(currentActivity, exception.getReviewResult());
            return new InstructionReviewCoordinator.ReviewBeforeSaveDecision(snapshot, exception.getReviewResult(), false);
        }
    }

    private void ensureReviewAllowsSave(
            TrainingActivitySaveCommand command,
            InstructionReviewCoordinator.ReviewBeforeSaveDecision decision) {
        var snapshot = decision.snapshot();
        if (snapshot != null && snapshot.canSave()) {
            return;
        }
        if (confirmedCachedGoodReview(command, decision)) {
            return;
        }
        throw new InstructionQualityReviewException(blockingReviewMessage(snapshot), decision.reviewResult(), snapshot);
    }

    private void ensureAsyncReviewAllowsSave(
            TrainingActivitySaveCommand command,
            InstructionReviewSnapshotDto snapshot) {
        if (snapshot.isSaveableGoodReview()) {
            return;
        }
        var explicitCurrentOverride = command.confirmedReviewHash() != null
                && command.confirmedReviewHash().equals(snapshot.reviewHash())
                && snapshot.reviewStatus() != InstructionReviewStatus.IDLE
                && snapshot.reviewStatus() != InstructionReviewStatus.REVIEWING
                && snapshot.reviewStatus() != InstructionReviewStatus.LOCAL_INVALID
                && snapshot.reviewStatus() != InstructionReviewStatus.STALE;
        if (!explicitCurrentOverride) {
            throw new InstructionQualityReviewException(
                    "A current GOOD review or an explicit confirmation for this reviewed instruction is required.",
                    null,
                    snapshot);
        }
    }

    private boolean confirmedCachedGoodReview(
            TrainingActivitySaveCommand command,
            InstructionReviewCoordinator.ReviewBeforeSaveDecision decision) {
        var snapshot = decision.snapshot();
        var reviewResult = decision.reviewResult();
        return snapshot != null
                && reviewResult != null
                && snapshot.requiresVisibleReviewConfirmation()
                && snapshot.reviewHash() != null
                && snapshot.reviewHash().equals(command.confirmedReviewHash())
                && Boolean.TRUE.equals(reviewResult.validInstruction())
                && reviewResult.qualityStatus() == InstructionQualityStatus.GOOD;
    }

    private String blockingReviewMessage(InstructionReviewSnapshotDto snapshot) {
        if (snapshot != null && snapshot.message() != null && !snapshot.message().isBlank()) {
            return snapshot.message();
        }
        return "Estas instrucciones no se pueden guardar todavía.";
    }

    private void ensureInstructionReviewAllowsLaunch(TrainingActivity activity) {
        if (activity.getInstructionReviewStatus() == InstructionReviewStatus.UNAVAILABLE) {
            throw new IllegalStateException(
                    activity.getInstructionReviewMessage() == null || activity.getInstructionReviewMessage().isBlank()
                            ? "No pudimos completar la revisión automática de instrucciones. Intenta guardar de nuevo."
                            : activity.getInstructionReviewMessage());
        }
        if (!instructionReviewCoordinator.hasCurrentGoodInstructionReview(activity)) {
            throw new IllegalStateException(
                    activity.getInstructionReviewMessage() == null || activity.getInstructionReviewMessage().isBlank()
                            ? "Lanzar la actividad requiere una revisión actual GOOD para las instrucciones guardadas."
                            : activity.getInstructionReviewMessage());
        }
    }

    private void refreshInstructionReviewAdvisory(TrainingActivity activity) {
        try {
            var decision = instructionReviewCoordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());
            if (decision == null) {
                return;
            }
            instructionReviewCoordinator.applyPersistedReview(activity, decision.snapshot(), decision.reviewResult());
            trainingActivityRepository.save(activity);
        }
        catch (InstructionReviewUnavailableException exception) {
            if (exception.getReviewResult() != null) {
                var snapshot = instructionReviewCoordinator.unavailableSnapshot(activity, exception.getReviewResult());
                instructionReviewCoordinator.applyPersistedReview(activity, snapshot, exception.getReviewResult());
                trainingActivityRepository.save(activity);
            }
            LOGGER.warn("Launch review refresh failed because the instruction review is unavailable: activityId={}", activity.getId(), exception);
        }
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
