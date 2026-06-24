package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingActivityService {

    private final TrainingActivityRepository trainingActivityRepository;
    private final ActiveAcademicContextResolver contextResolver;

    public TrainingActivityService(
            TrainingActivityRepository trainingActivityRepository,
            ActiveAcademicContextResolver contextResolver) {
        this.trainingActivityRepository = trainingActivityRepository;
        this.contextResolver = contextResolver;
    }

    @Transactional
    public TrainingActivity createPending(String title, String instruction) {
        var context = requireProfessorContext();
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(new com.wornux.data.entities.academic.GroupClass());
        activity.getGroupClass().setId(context.groupClassId());
        activity.setCreatedByGroupClassMember(new GroupClassMember());
        activity.getCreatedByGroupClassMember().setId(context.groupClassMemberId());
        activity.setTitle(title);
        activity.setInstructions(instruction);
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
        trainingActivityRepository.delete(get(activityId));
    }

    @Transactional
    public TrainingActivity update(UUID activityId, String title, String instruction) {
        var activity = get(activityId);
        activity.setTitle(title);
        activity.setInstructions(instruction);
        activity.setUpdatedAt(Instant.now());
        return trainingActivityRepository.save(activity);
    }

    private ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassRole() != GroupClassMemberRole.PROFESSOR) {
            throw new SetupRequiredException(
                    "An active professor class context is required before managing training activities.");
        }
        return context;
    }
}
