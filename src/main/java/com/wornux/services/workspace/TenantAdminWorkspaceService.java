package com.wornux.services.workspace;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.AcademicPeriod;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.Subject;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.academic.AcademicPeriodRepository;
import com.wornux.data.repositories.academic.GroupClassRepository;
import com.wornux.data.repositories.academic.SubjectRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.onboarding.InvitationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminWorkspaceService {

    private final WorkspaceRoutingService workspaceRoutingService;
    private final TenantAccountRepository tenantAccountRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SubjectRepository subjectRepository;
    private final GroupClassRepository groupClassRepository;
    private final InvitationService invitationService;
    private final TenantAdminWorkspaceService self;

    public TenantAdminWorkspaceService(
            WorkspaceRoutingService workspaceRoutingService,
            TenantAccountRepository tenantAccountRepository,
            AcademicPeriodRepository academicPeriodRepository,
            SubjectRepository subjectRepository,
            GroupClassRepository groupClassRepository,
            InvitationService invitationService,
            @Lazy TenantAdminWorkspaceService self) {
        this.workspaceRoutingService = workspaceRoutingService;
        this.tenantAccountRepository = tenantAccountRepository;
        this.academicPeriodRepository = academicPeriodRepository;
        this.subjectRepository = subjectRepository;
        this.groupClassRepository = groupClassRepository;
        this.invitationService = invitationService;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public List<AccessibleTenant> listAccessibleTenants(Account account) {
        return workspaceRoutingService.listAccessibleTenants(account)
                .stream()
                .filter(tenant -> tenant.roleCodes().contains("TENANT_ADMIN"))
                .toList();
    }

    @Transactional(readOnly = true)
    public UUID requireActiveTenantAccount(Account account) {
        return listAccessibleTenants(account).stream()
                .findFirst()
                .map(AccessibleTenant::tenantAccountId)
                .orElseThrow(() -> new SecurityException("A tenant admin tenant context is required."));
    }

    @Transactional(readOnly = true)
    public List<AcademicPeriod> listPeriods(UUID tenantId) {
        return academicPeriodRepository.findByTenant_IdOrderByStartsAtAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Subject> listSubjects(UUID tenantId) {
        return subjectRepository.findByTenant_IdOrderByCodeAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<GroupClass> listGroupClasses(UUID tenantId) {
        return groupClassRepository.findByTenant_IdOrderByNameAsc(tenantId);
    }

    @Transactional
    public AcademicPeriod createPeriod(
            Account account,
            String code,
            String name,
            LocalDate startsAt,
            LocalDate endsAt) {
        var tenantAccount =
                tenantAccountRepository.findByIdAndAccount_Id(self.requireActiveTenantAccount(account), account.getId())
                        .orElseThrow(() -> new SecurityException("A tenant admin tenant context is required."));
        if (academicPeriodRepository.findByTenant_IdAndCode(tenantAccount.getTenant().getId(), code.trim())
                .isPresent()) {
            throw new IllegalArgumentException("An academic period with that code already exists in this tenant.");
        }
        var period = new AcademicPeriod();
        period.setId(UUID.randomUUID());
        period.setTenant(tenantAccount.getTenant());
        period.setCode(code.trim());
        period.setName(name.trim());
        period.setStartsAt(startsAt);
        period.setEndsAt(endsAt);
        period.setActive(true);
        period.setCreatedAt(Instant.now());
        period.setUpdatedAt(Instant.now());
        return academicPeriodRepository.save(period);
    }

    @Transactional
    public Subject createSubject(Account account, String code, String name) {
        var tenantAccount =
                tenantAccountRepository.findByIdAndAccount_Id(self.requireActiveTenantAccount(account), account.getId())
                        .orElseThrow(() -> new SecurityException("A tenant admin tenant context is required."));
        if (subjectRepository.findByTenant_IdAndCode(tenantAccount.getTenant().getId(), code.trim()).isPresent()) {
            throw new IllegalArgumentException("A subject with that code already exists in this tenant.");
        }
        var subject = new Subject();
        subject.setId(UUID.randomUUID());
        subject.setTenant(tenantAccount.getTenant());
        subject.setCode(code.trim());
        subject.setName(name.trim());
        subject.setActive(true);
        subject.setCreatedAt(Instant.now());
        subject.setUpdatedAt(Instant.now());
        return subjectRepository.save(subject);
    }

    @Transactional
    public GroupClass createGroupClass(
            Account account,
            UUID subjectId,
            UUID academicPeriodId,
            String code,
            String name) {
        var tenantAccount =
                tenantAccountRepository.findByIdAndAccount_Id(self.requireActiveTenantAccount(account), account.getId())
                        .orElseThrow(() -> new SecurityException("A tenant admin tenant context is required."));
        if (groupClassRepository.findByTenant_IdAndCode(tenantAccount.getTenant().getId(), code.trim()).isPresent()) {
            throw new IllegalArgumentException("A group class with that code already exists in this tenant.");
        }
        var subject = subjectRepository.findById(subjectId)
                .filter(value -> value.getTenant().getId().equals(tenantAccount.getTenant().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Select a subject from the active tenant."));
        var period = academicPeriodRepository.findById(academicPeriodId)
                .filter(value -> value.getTenant().getId().equals(tenantAccount.getTenant().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Select an academic period from the active tenant."));
        var groupClass = new GroupClass();
        groupClass.setId(UUID.randomUUID());
        groupClass.setTenant(tenantAccount.getTenant());
        groupClass.setSubject(subject);
        groupClass.setAcademicPeriod(period);
        groupClass.setCreatedByTenantAccount(tenantAccount);
        groupClass.setCode(code.trim());
        groupClass.setName(name.trim());
        groupClass.setActive(true);
        groupClass.setCreatedAt(Instant.now());
        groupClass.setUpdatedAt(Instant.now());
        return groupClassRepository.save(groupClass);
    }

    @Transactional
    public void inviteProfessor(Account account, UUID groupClassId, String email) {
        var tenantAccount =
                tenantAccountRepository.findByIdAndAccount_Id(self.requireActiveTenantAccount(account), account.getId())
                        .orElseThrow(() -> new SecurityException("A tenant admin tenant context is required."));
        var groupClass = groupClassRepository.findById(groupClassId)
                .filter(value -> value.getTenant().getId().equals(tenantAccount.getTenant().getId()))
                .orElseThrow(() -> new SecurityException("You cannot invite a professor to that class."));
        invitationService.createInvitation(
            InvitationTargetRole.PROFESSOR,
            tenantAccount.getTenant().getId(),
            groupClass.getId(),
            email,
            account,
            tenantAccount,
            null);
    }
}
