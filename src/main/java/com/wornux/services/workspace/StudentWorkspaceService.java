package com.wornux.services.workspace;

import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.evaluation.EvaluationAssignment;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.evaluation.EvaluationAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentWorkspaceService {

    private final WorkspaceRoutingService workspaceRoutingService;
    private final EvaluationAssignmentRepository evaluationAssignmentRepository;

    public StudentWorkspaceService(
            WorkspaceRoutingService workspaceRoutingService,
            EvaluationAssignmentRepository evaluationAssignmentRepository) {
        this.workspaceRoutingService = workspaceRoutingService;
        this.evaluationAssignmentRepository = evaluationAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AccessibleClass> listStudentClasses(Account account) {
        return workspaceRoutingService.listAccessibleClasses(account, GroupClassMemberRole.STUDENT);
    }

    @Transactional(readOnly = true)
    public List<EvaluationAssignment> listAssignments(Account account) {
        var membership = workspaceRoutingService.currentClassMembership(account, GroupClassMemberRole.STUDENT)
                .orElseThrow(() -> new SecurityException("An active student class context is required."));
        return evaluationAssignmentRepository.findByGroupClassMember_IdOrderByUpdatedAtDesc(membership.getId());
    }

    @Transactional
    public void switchClass(Account account, UUID groupClassMemberId) {
        workspaceRoutingService.switchGroupClass(account, groupClassMemberId);
    }
}
