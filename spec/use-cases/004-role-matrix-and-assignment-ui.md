# Spec 004 — Role Matrix and Assignment UI

**Status:** Implemented
**Date:** 2026-07-02  
**Depends on:** Specs 001, 002, and 003  
**Do not:** start the app, use Playwright, or run full tests. Manual UI verification happens later.

## Goal

Create maintainable UI and services for custom role creation, permission matrix editing, and role assignment. Roles are permission bundles; membership identity and ownership remain separate.

## Surfaces

Create or replace these Vaadin views:

```text
RoleMatrixView
TenantMemberRoleAssignmentView
GroupClassMemberRoleAssignmentView
```

Routes may be adjusted to project conventions, but must be reachable from MainLayout only when the user has the required permission.

Required annotations:

```text
RoleMatrixView -> @RequiresPermission(ROLE_VIEW)
Assignment views -> @RequiresPermission(ROLE_ASSIGN)
```

All mutating service methods also require `@RequiresPermission`.

## Role matrix behavior

The matrix is contextual.

Platform context:

```text
Rows: platform roles in platform namespace.
Columns: AppPermission values valid for platform-level management.
Visible role kind: PLATFORM only.
```

Tenant context:

```text
Rows: tenant namespace roles.
Role kind selector: TENANT or GROUP_CLASS.
TENANT roles may contain tenant-wide permissions and group-class admin permissions.
GROUP_CLASS roles may contain class-local permissions only.
```

Group-class context:

```text
Do not create roles here.
Show a read-only explanation or link back to tenant role management if user has access.
```

There is no `Tenant Role` or `Platform Role` resource. The protected resource is `ROLE`; scope comes from active context and role namespace.

## Create role flow

Dialog fields:

```text
name
description
assignment level
priority
permissions matrix
```

The assignment-level field decides where the role can be assigned:

```text
PLATFORM -> account_platform_role
TENANT -> tenant_account_role
GROUP_CLASS -> group_class_member_role
```

Service command:

```text
CreateRoleCommand(name, description, assignmentLevel, priority, permissions)
```

Validation:

```text
Name required.
Role code generated from name and unique in namespace.
Permissions must be known AppPermission codes.
Permissions must be valid for assignment level.
Actor must have every permission they are granting.
New role priority must be lower than actor highest manageable priority.
```

## Edit role flow

Editable fields:

```text
name
description
active
assignable
priority
permissions
```

Rules:

```text
Cannot edit roles at or above actor priority.
Cannot add permissions actor does not have.
Cannot change assignment_level after creation.
Cannot edit system_defined roles unless actor has a platform role with sufficient priority.
```

Any successful change increments the namespace `rbac_version` and publishes `RbacChangedEvent`.

## Tenant member assignment matrix

Rows:

```text
tenant_account members in active tenant
```

Columns:

```text
active tenant roles with assignment_level = TENANT and assignable = true
```

Rules:

```text
Actor needs ROLE_ASSIGN.
Actor cannot assign or remove roles at or above actor priority.
Tenant admin is tied to one tenant; no tenant switcher is introduced here.
```

## Group-class member assignment matrix

Rows:

```text
group_class_member rows for selected group class
```

Columns:

```text
active tenant roles with assignment_level = GROUP_CLASS and assignable = true
```

Rules:

```text
Actor needs ROLE_ASSIGN.
Actor must have access to the selected group class through active tenant context or real class membership.
Changing RBAC roles does not change group_class_member.member_kind.
Professor/student roster identity is edited through membership management, not this matrix.
```

## Permission grouping in UI

Group columns by resource:

```text
Tenant
Account
Role
Subject
Academic Period
Group Class
Group Class Member
Group Class Join Code
Grounding
Conversation
Training Activity
Training Activity Assignment
Course Material
```

Show actions as checkboxes inside each resource group. Disabled cells must explain why:

```text
actor lacks permission
invalid for role assignment level
target role priority is too high
system-defined role is locked
```

## Ownership reminders

Do not create `view-own` permissions.

Ownership filtering remains in services:

```text
conversation:view -> service returns only owned conversations where required
training-activity:view -> service returns only owned/assigned activity data where required
```

The role matrix only edits permission bundles.

## Minimal integration tests

Use Testcontainers PostgreSQL.

Cover:

- Tenant admin creates a GROUP_CLASS role with only permissions they have.
- Tenant admin cannot create a role containing an unknown permission string.
- Tenant admin cannot grant a permission they do not have.
- Professor cannot open role creation service because they lack ROLE_CREATE.
- Assigning a group-class role changes permissions but not `member_kind`.
- Role change increments namespace version and invalidates snapshot.

## Acceptance checks

- Role creation UI always asks for assignment level.
- Assignment tables are selected from role.assignment_level, not from role name.
- No UI path creates DB resource/action/permission rows.
- No UI path edits `group_class_member.member_kind` through RBAC assignment.
- Matrix cells are disabled instead of silently omitted when the actor cannot grant them.
- All role mutations publish RBAC change events and refresh active sessions through the global broadcaster.
