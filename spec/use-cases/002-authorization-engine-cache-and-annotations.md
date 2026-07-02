# Spec 002 — Authorization Engine, Snapshot Cache, and Annotations

**Status:** Ready for implementation  
**Date:** 2026-07-02  
**Depends on:** Spec 001  
**Do not:** start the app or run full tests. Add only the small integration tests listed here.

## Goal

Create the authorization runtime: small authentication principal, active context, Caffeine-backed access snapshots, annotation-driven permission checks, and global RBAC change propagation for active Vaadin sessions.

## Authentication principal

Replace contextual authority loading in `CustomUserDetailsService`.

`UserDetails` must contain identity only:

```text
AppPrincipal
- accountId
- email
- locked/account status
```

Do not load tenant roles, group-class roles, or permission codes into Spring `GrantedAuthority`. Contextual permissions are computed after context selection.

## Active context

Create:

```text
ContextLevel: PLATFORM, TENANT, GROUP_CLASS
ActiveContext(level, tenantId, groupClassId)
ActiveContextHolder
```

`ActiveContextHolder` stores the selected context in `VaadinSession`. It does not persist snapshots.

## Access snapshot

Create:

```text
UserAccessSnapshot
- accountId
- activeContext
- tenantId
- tenantAccountId
- groupClassId nullable
- groupClassMemberId nullable
- memberKind nullable
- roleCodes Set<String>
- permissionCodes Set<String>
- roleNamespaceVersion long
```

For `GROUP_CLASS` context:

1. Resolve the tenant account.
2. Load tenant roles from `tenant_account_role`.
3. Resolve group-class membership if it exists.
4. If membership exists, load `group_class_member_role`.
5. Union tenant and class permissions.
6. Keep `groupClassMemberId = null` when access comes only from tenant admin reach.

For `TENANT` context, load only tenant-account roles. For `PLATFORM` context, load only account-platform roles.

## Caffeine cache

Create `AccessSnapshotService` backed by Caffeine.

Cache key:

```text
accountId + contextLevel + tenantId + groupClassId + roleNamespaceVersion
```

Use bounded cache settings. Do not create an unbounded cache.

Cache invalidation must support:

```text
invalidateNamespace(roleNamespaceId)
invalidateAccount(accountId)
invalidateContext(accountId, ActiveContext)
```

Caffeine is local-memory only. Do not add Redis, PostgreSQL LISTEN/NOTIFY, or cluster invalidation in this use case.

## Authorization service

Create:

```text
AuthorizationService.can(AppPermission)
AuthorizationService.check(AppPermission)
AuthorizationService.snapshot()
```

Permission checks use the active snapshot only. Ownership and roster checks remain domain logic and must not be encoded as permissions.

Role-management boundary checks:

```text
Actor must have role:create/update/delete/assign.
Actor cannot grant permissions not present in actor snapshot.
Actor cannot manage a role with priority >= actor highest priority for that namespace and assignment level.
```

Priority is only for role management. It is never used to allow normal feature access.

## Annotations

Every permission check must be possible through an annotation.

Create one annotation usable on service methods and Vaadin route classes:

```text
@RequiresPermission(AppPermission value)
```

Service method enforcement:

```text
PermissionMethodAspect
- intercepts @RequiresPermission on methods/classes
- delegates to AuthorizationService.check
```

Routes are handled in Spec 003 through Vaadin navigation access control, but the same annotation must be reused.

Manual `authz.check(...)` is allowed only when an operation needs dynamic permission selection. Static checks must prefer the annotation.

## RBAC change events and UI broadcast

Create:

```text
RbacChangedEvent(roleNamespaceId)
RbacUiRegistry
RbacUiBroadcaster
```

Any role or role-assignment mutation must:

1. mutate data,
2. increment `role_namespace.rbac_version`,
3. publish `RbacChangedEvent`,
4. invalidate Caffeine snapshots for that namespace.

Vaadin UI updates must not be performed directly from the Spring event thread. The broadcaster must call `ui.access(...)` for affected UIs. Enable Push in the app shell if real-time browser updates are required.

No view should register its own RBAC listener. Register each UI once at shell/layout level and refresh the shell through one global broadcaster.

## Minimal integration tests

Use Testcontainers PostgreSQL.

Create one small service test covering:

- Student in a class has `conversation:create` from group-class role.
- Tenant admin has tenant and group-class admin permissions without `groupClassMemberId`.
- Professor has class permissions only in classes where they have membership.
- Role update increments namespace version and causes a new snapshot key.
- Actor cannot grant a permission they do not have.

## Acceptance checks

- `CustomUserDetailsService` no longer loads contextual roles or permissions.
- Snapshot loading is the only runtime source of contextual permissions.
- `@RequiresPermission` works on service methods.
- Caffeine cache key includes namespace version.
- Any role/assignment mutation increments `rbac_version` and publishes `RbacChangedEvent`.
- UI broadcast uses `UI.access(...)`, not `UI.getCurrent()` from an event thread.
