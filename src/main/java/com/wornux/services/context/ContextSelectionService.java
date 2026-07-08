package com.wornux.services.context;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.AccountContextPreference;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.repositories.academic.GroupClassRepository;
import com.wornux.data.repositories.identity.AccountContextPreferenceRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.security.authorization.AccessSnapshotService;
import com.wornux.security.authorization.ActiveContext;
import com.wornux.security.authorization.ActiveContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextSelectionService {

    private final ContextDiscoveryService contextDiscoveryService;
    private final AccountContextPreferenceRepository accountContextPreferenceRepository;
    private final TenantRepository tenantRepository;
    private final GroupClassRepository groupClassRepository;
    private final AccountRepository accountRepository;
    private final ActiveContextHolder activeContextHolder;
    private final AccessSnapshotService accessSnapshotService;

    public ContextSelectionService(
            ContextDiscoveryService contextDiscoveryService,
            AccountContextPreferenceRepository accountContextPreferenceRepository,
            TenantRepository tenantRepository,
            GroupClassRepository groupClassRepository,
            AccountRepository accountRepository,
            ActiveContextHolder activeContextHolder,
            AccessSnapshotService accessSnapshotService) {
        this.contextDiscoveryService = contextDiscoveryService;
        this.accountContextPreferenceRepository = accountContextPreferenceRepository;
        this.tenantRepository = tenantRepository;
        this.groupClassRepository = groupClassRepository;
        this.accountRepository = accountRepository;
        this.activeContextHolder = activeContextHolder;
        this.accessSnapshotService = accessSnapshotService;
    }

    @Transactional
    public ContextSelectionResult resolveLoginContext(Account account) {
        var options = contextDiscoveryService.discover(account);
        if (options.isEmpty()) {
            activeContextHolder.clear();
            return new ContextSelectionResult.NoAccess();
        }
        var preferred = readPreferredContext(account)
                .flatMap(context -> options.stream().filter(option -> option.matches(context)).findFirst());
        if (preferred.isPresent()) {
            select(account, preferred.get());
            return new ContextSelectionResult.Selected(preferred.get());
        }
        if (options.size() == 1) {
            var selected = options.getFirst();
            select(account, selected);
            return new ContextSelectionResult.Selected(selected);
        }
        activeContextHolder.clear();
        return new ContextSelectionResult.SelectionRequired(options);
    }

    @Transactional
    public AvailableContextOption select(Account account, AvailableContextOption requested) {
        var option = contextDiscoveryService.discover(account)
                .stream()
                .filter(
                    candidate -> sameContext(candidate, requested.level(), requested.tenantId(), requested.classId()))
                .findFirst()
                .orElseThrow(() -> new SecurityException("The requested context is not available for this account."));
        persist(account, option);
        activeContextHolder.set(option.toActiveContext());
        accessSnapshotService.invalidateAccount(account.getId());
        return option;
    }

    @Transactional
    public AvailableContextOption select(Account account, ContextLevel level, UUID tenantId, UUID classId) {
        return select(account, new AvailableContextOption(level, tenantId, classId, "", "", null));
    }

    public String defaultRoute(AvailableContextOption option) {
        return switch (option.level()) {
            case PLATFORM -> "admin";
            case TENANT -> "tenant";
            case GROUP_CLASS -> "threads";
        };
    }

    private Optional<ActiveContext> readPreferredContext(Account account) {
        return accountContextPreferenceRepository.findById(account.getId())
                .filter(preference -> preference.getContextLevel() != null)
                .map(preference -> switch (preference.getContextLevel()) {
                    case PLATFORM -> ActiveContext.platform();
                    case TENANT -> preference.getTenant() == null
                            ? null
                            : ActiveContext.tenant(preference.getTenant().getId());
                    case GROUP_CLASS -> preference.getTenant() == null || preference.getGroupClass() == null
                            ? null
                            : ActiveContext
                                    .groupClass(preference.getTenant().getId(), preference.getGroupClass().getId());
                });
    }

    private boolean sameContext(AvailableContextOption option, ContextLevel level, UUID tenantId, UUID classId) {
        return option.level() == level
                && Objects.equals(option.tenantId(), tenantId)
                && Objects.equals(option.classId(), classId);
    }

    private void persist(Account account, AvailableContextOption option) {
        var preference = accountContextPreferenceRepository.findById(account.getId()).orElseGet(() -> {
            var created = new AccountContextPreference();
            created.setAccount(account);
            return created;
        });
        preference.setContextLevel(option.level());
        preference.setTenant(resolveTenant(option));
        preference.setGroupClass(resolveGroupClass(option));
        preference.setUpdatedAt(Instant.now());
        accountContextPreferenceRepository.save(preference);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
    }

    private Tenant resolveTenant(AvailableContextOption option) {
        if (option.tenantId() == null) {
            return null;
        }
        return tenantRepository.findById(option.tenantId()).orElseThrow();
    }

    private GroupClass resolveGroupClass(AvailableContextOption option) {
        if (option.classId() == null) {
            return null;
        }
        return groupClassRepository.findById(option.classId()).orElseThrow();
    }
}
