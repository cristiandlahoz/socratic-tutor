package com.wornux.services.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.onboarding.InvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfessorWorkspaceService {

    private final WorkspaceRoutingService workspaceRoutingService;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final InvitationService invitationService;

    public ProfessorWorkspaceService(
            WorkspaceRoutingService workspaceRoutingService,
            TenantAccountRepository tenantAccountRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            InvitationService invitationService) {
        this.workspaceRoutingService = workspaceRoutingService;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.invitationService = invitationService;
    }

    @Transactional(readOnly = true)
    public List<AccessibleClass> listProfessorClasses(Account account) {
        return workspaceRoutingService.listAccessibleClasses(account, GroupClassMemberRole.PROFESSOR);
    }

    @Transactional(readOnly = true)
    public GroupClassMember requireProfessorMembership(Account account) {
        return workspaceRoutingService.currentClassMembership(account, GroupClassMemberRole.PROFESSOR)
                .orElseThrow(() -> new SecurityException("An active professor class context is required."));
    }

    @Transactional(readOnly = true)
    public List<GroupClassMember> listStudents(Account account) {
        var professorMembership = requireProfessorMembership(account);
        return groupClassMemberRepository
                .findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(professorMembership.getGroupClass().getId())
                .stream()
                .filter(member -> member.getRole() == GroupClassMemberRole.STUDENT)
                .toList();
    }

    @Transactional
    public void inviteStudent(Account account, String email) {
        var professorMembership = requireProfessorMembership(account);
        invitationService.createInvitation(
            InvitationTargetRole.STUDENT,
            professorMembership.getGroupClass().getTenant().getId(),
            professorMembership.getGroupClass().getId(),
            email,
            account,
            professorMembership.getTenantAccount(),
            professorMembership);
    }

    @Transactional
    public void disableStudentMembership(Account account, UUID studentMembershipId) {
        var professorMembership = requireProfessorMembership(account);
        var studentMembership = groupClassMemberRepository.findById(studentMembershipId)
                .filter(member -> member.getGroupClass().getId().equals(professorMembership.getGroupClass().getId()))
                .filter(member -> member.getRole() == GroupClassMemberRole.STUDENT)
                .orElseThrow(() -> new SecurityException("You cannot manage that student membership."));
        studentMembership.setLocked(true);
        studentMembership.setUpdatedAt(Instant.now());
        groupClassMemberRepository.save(studentMembership);
    }

    @Transactional
    public void switchClass(Account account, UUID groupClassMemberId) {
        workspaceRoutingService.switchGroupClass(account, groupClassMemberId);
    }
}
