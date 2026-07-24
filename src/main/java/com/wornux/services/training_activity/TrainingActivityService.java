package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingActivityLaunchedBus activityLaunchedBus;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final AdvisoryInstructionReviewService advisoryInstructionReviewService;
    private final TrainingActivityService self;

    @Autowired
    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository,
            com.wornux.data.repositories.training_activity.OutboxEventRepository outboxEventRepository,
            com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository outboxRecipientDeliveryRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivityLaunchedBus activityLaunchedBus,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            AdvisoryInstructionReviewService advisoryInstructionReviewService,
            @Lazy TrainingActivityService self) {
        this.trainingActivityRepository = trainingActivityRepository;
        this.trainingActivityAssignmentRepository = trainingActivityAssignmentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxRecipientDeliveryRepository = outboxRecipientDeliveryRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.contextResolver = contextResolver;
        this.activityLaunchedBus = activityLaunchedBus;
        this.assignmentStateBus = assignmentStateBus;
        this.advisoryInstructionReviewService = advisoryInstructionReviewService;
        this.self = self;
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
        ensureReviewAllowsSave(command, context);
        var saved = trainingActivityRepository.save(activity);
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
        var context = requireProfessorContext();
        ensureReviewAllowsSave(command, context);
        activity.setTitle(command.title());
        activity.setInstructions(command.instructions());
        activity.setSafeBrowserEnabled(command.safeBrowserEnabled());
        activity.setUpdatedAt(Instant.now());
        return trainingActivityRepository.save(activity);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public int launch(UUID activityId) {
        return launch(activityId, get(activityId).getVersion());
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public int launch(UUID activityId, long expectedVersion) {
        var context = requireProfessorContext();
        var activity = get(activityId);
        if (!context.tenantAccountId().equals(activity.getCreatedByTenantAccount().getId())) {
            throw new IllegalArgumentException("No puedes publicar una actividad creada por otro profesor.");
        }
        if (activity.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED) {
            return (int) trainingActivityAssignmentRepository.countByTrainingActivity_Id(activityId);
        }
        if (activity.getVersion() != expectedVersion) {
            throw new IllegalStateException("La actividad cambió. Actualiza la página e inténtalo de nuevo.");
        }
        if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft training activities can be launched.");
        }
        if (trainingActivityRepository
                .findFirstByCreatedByTenantAccount_IdAndStatus(context.tenantAccountId(), TrainingActivityLifecycleStatus.PUBLISHED)
                .isPresent()) {
            throw new IllegalStateException("Ya tienes una actividad en ejecución. Ciérrala antes de publicar otra.");
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

        activity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        activity.setPublishedAt(now);
        activity.setUpdatedAt(now);
        try {
            trainingActivityRepository.saveAndFlush(activity);
        }
        catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Ya tienes una actividad en ejecución. Ciérrala antes de publicar otra.", exception);
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

        persistPublicationOutbox(activity, students, now);

        var notification = new TrainingActivityLaunchedBus.Notification(
                activity.getId(),
                activity.getGroupClass().getId(),
                students.stream().map(GroupClassMember::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
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
        var context = requireProfessorContext();
        var activity = get(activityId);
        if (!context.tenantAccountId().equals(activity.getCreatedByTenantAccount().getId())) {
            throw new IllegalArgumentException("No puedes cerrar una actividad creada por otro profesor.");
        }
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

    @Transactional(readOnly = true)
    public InstructionReviewSnapshotDto getInstructionReviewSnapshot(UUID activityId) {
        var activity = get(activityId);
        var context = requireProfessorContext();
        return advisoryInstructionReviewService.current(context.groupClassMemberId(), activity.getTitle(), activity.getInstructions());
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_CREATE)
    public InstructionReviewSnapshotDto reviewDraft(TrainingActivitySaveCommand command) {
        validateRequiredFields(command);
        var context = requireProfessorContext();
        return advisoryInstructionReviewService.request(
                context.groupClassMemberId(), command.title(), command.instructions());
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

    private void ensureReviewAllowsSave(TrainingActivitySaveCommand command, ActiveAcademicContext context) {
        var snapshot = advisoryInstructionReviewService.current(
                context.groupClassMemberId(), command.title(), command.instructions());
        if (snapshot == null) {
            snapshot = advisoryInstructionReviewService.request(
                    context.groupClassMemberId(), command.title(), command.instructions());
        }
        if (snapshot.isSaveableGoodReview()) {
            return;
        }
        var explicitCurrentOverride = command.saveDespiteReview()
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

}
