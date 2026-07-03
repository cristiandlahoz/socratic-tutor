# Spec 003 — Login Context Selection, Navbar Switching, and Route Security

**Status:** Implemented  
**Date:** 2026-07-02  
**Depends on:** Specs 001 and 002  
**Do not:** start the app, use Playwright, or run full tests. UI behavior is implemented now and manually verified later.

## Goal

Replace workspace routing with context selection. Users enter the UI only after a valid active context is selected. Main navigation is rendered from permissions, and route access uses the shared `@RequiresPermission` annotation.

## Remove old routing behavior

Replace or retire active use of:

```text
services/workspace/*WorkspaceService
WorkspaceRoutingService
WorkspaceDecision
WorkspaceDestination
workspace-specific landing views when they exist only to choose a workspace
```

Do not delete unrelated UI code unless the new context workflow makes it unreachable.

## Context discovery

Create:

```text
AvailableContextOption
ContextDiscoveryService
ContextSelectionService
```

`AvailableContextOption` fields:

```text
level
tenantId nullable
classId nullable
label
subtitle
identityLabel nullable
```

Discovery rules:

```text
System admin/platform role -> PLATFORM option.
Tenant admin -> one TENANT option tied to the account's active tenant membership.
Professor/student -> GROUP_CLASS options only for classes where they have group_class_member.member_kind.
Tenant admin who is also professor/student -> TENANT option plus class options for real memberships only.
Tenant admin administrative reach does not create class switch options.
```

Tenant admins are tied to one active tenant. Do not add a tenant switcher for tenant admins.

## Login workflow

After successful authentication:

```text
1. Resolve available contexts.
2. If none, route to NoAccessView.
3. If one, select it automatically.
4. If multiple, show card-based ContextSelectionView.
5. Persist the selected context to account_context_preference.
6. Store ActiveContext in VaadinSession.
7. Build snapshot lazily through AuthorizationService.
8. Navigate to the default route for that context.
```

Recurrent login:

```text
1. Read account_context_preference.
2. Validate that the context is still available.
3. Restore it when valid.
4. Fall back to card selection when invalid or ambiguous.
```

## No access UI

`NoAccessView` must handle two cases:

```text
Authenticated but no available context.
Authenticated but route permission denied.
```

It must show a clear message and a logout button. It must not expose admin links or fallback workspace routes.

## Navbar context switcher

MainLayout contains one context switcher area.

Rules:

```text
Platform user -> tenant selector for platform-managed tenants.
Tenant admin -> fixed tenant badge, no tenant switcher.
Professor/student -> group-class selector from real memberships only.
Tenant admin who is professor/student -> fixed tenant badge plus class selector for real class memberships only.
```

Selecting a context:

```text
1. Validates availability.
2. Updates ActiveContextHolder.
3. Persists account_context_preference.
4. Invalidates current snapshot.
5. Rebuilds MainLayout navigation.
6. Navigates to context default route.
```

## Navigation registry

Create a single registry of navigation entries.

Each entry defines:

```text
label
route target
minimum context level
required AppPermission
menu group/order
```

MainLayout renders only entries whose required permission is present in the snapshot. Hiding a menu item is not security; route and service checks still apply.

## Vaadin route-level security

Use Vaadin navigation access control with a custom checker. Do not use the deprecated `ViewAccessChecker` path.

Create:

```text
PermissionNavigationAccessChecker
NavigationAccessControlConfigurer bean
```

Behavior:

```text
Routes with @AnonymousAllowed remain public.
Routes with @PermitAll require authentication but no app permission.
App routes should have @RequiresPermission.
Missing @RequiresPermission on protected app routes denies navigation by default.
Denied routes reroute to NoAccessView.
```

Do not mix custom allow/deny decisions with the built-in annotated checker in a way that creates conflicting decisions.

## UI migration targets

Update these surfaces to use active context and permissions:

```text
MainLayout
LoginView
NoAccessView
SystemAdminWorkspaceView / replacement
TenantAdminWorkspaceView / replacement
ProfessorWorkspaceView / replacement
StudentWorkspaceView / replacement
WorkspaceDrawerNavigation
```

The result should not depend on role names such as `TENANT_ADMIN`, `PROFESSOR`, or `STUDENT` for authorization. Roster labels may still use `member_kind`.

## Minimal tests

Use small Spring integration tests only. No browser tests now.

Cover:

- no context -> NoAccess decision
- one class membership -> auto-selected GROUP_CLASS context
- multiple class memberships -> selection required
- tenant admin -> TENANT context, no tenant switcher
- tenant admin with professor membership -> class switcher contains only professor membership classes

## Acceptance checks

- Login does not route directly by role name.
- Active context is selected before protected UI is entered.
- Tenant-admin admin reach does not appear as class membership.
- MainLayout items come from permission checks, not role checks.
- Protected routes without `@RequiresPermission` are denied by default.
- Route checks and service checks use the same annotation and `AuthorizationService`.
