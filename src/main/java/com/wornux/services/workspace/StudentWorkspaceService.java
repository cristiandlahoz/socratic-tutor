package com.wornux.services.workspace;

import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentWorkspaceService {

    private final WorkspaceRoutingService workspaceRoutingService;
    private final TrainingActivityAssignmentRepository trainingActivityAssignmentRepository;

    public StudentWorkspaceService(
            WorkspaceRoutingService workspaceRoutingService,
            TrainingActivityAssignmentRepository trainingActivityAssignmentRepository) {
        this.workspaceRoutingService = workspaceRoutingService;
        this.trainingActivityAssignmentRepository = trainingActivityAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AccessibleClass> listStudentClasses(Account account) {
        return workspaceRoutingService.listAccessibleClasses(account, GroupClassMemberRole.STUDENT);
    }

    @Transactional(readOnly = true)
    public List<TrainingActivityAssignment> listAssignments(Account account) {
        var membership = workspaceRoutingService.currentClassMembership(account, GroupClassMemberRole.STUDENT)
                .orElseThrow(() -> new SecurityException("An active student class context is required."));
        return trainingActivityAssignmentRepository.findByGroupClassMember_IdOrderByUpdatedAtDesc(membership.getId());
    }

    @Transactional
    public void switchClass(Account account, UUID groupClassMemberId) {
        workspaceRoutingService.switchGroupClass(account, groupClassMemberId);
    }
}
