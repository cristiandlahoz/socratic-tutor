# RBAC Rewrite Specs

This folder stores implementation specifications for the Socratic Tutor RBAC rewrite.

These specs are not optional design notes. They define the required database shape, authorization model, context switching behavior, Vaadin route protection, cache invalidation, and role-management UI for the new RBAC foundation.

Implementation must follow the specs in order. Each spec depends on the previous one being completed.

## Quick path

1. Read the specs in numeric order.
2. Do not start a later spec before the previous one is implemented.
3. Do not introduce `resource`, `action`, `permission`, or `role_permission` tables.
4. Do not store contextual permissions in `UserDetails` or Spring authorities.
5. Do not create a separate `tutor-chat` resource; conversation is the protected feature.
6. Do not add generic polymorphic assignment tables.
7. Keep permission checks annotation-driven wherever possible.
8. Do not start the app or run broad test suites unless a spec explicitly asks for a small integration test.

## Spec index

| ID | Title | Status | Primary Target | Notes |
|----|-------|--------|----------------|-------|
| SPEC-001 | RBAC Schema and Domain Model | Verified | Database, entities, repositories | Replaces the current RBAC schema. Removes resource/action/permission tables and models roles, namespaces, assignments, class membership identity, and ownership boundaries. |
| SPEC-002 | Authorization Engine, Cache, and Annotations | Verified | Security services, Caffeine, annotations | Builds the runtime authorization engine, access snapshots, cache invalidation, and annotation-based checks for service-level permissions. |
| SPEC-003 | Login Context, Navigation, and Route Security | Implemented | Login workflow, Vaadin navigation, MainLayout | Adds login context selection, navbar context switching, custom Vaadin route authorization, NoAccessView, and permission-based navigation items. |
| SPEC-004 | Role Matrix and Assignment UI | Implemented | Vaadin RBAC administration UI | Adds contextual role matrix screens and assignment screens for platform, tenant, and group-class role management. |

## Dependency order

```text
SPEC-001
  ↓
SPEC-002
  ↓
SPEC-003
  ↓
SPEC-004
```

Do not skip the order. The UI specs depend on the schema, domain model, snapshot engine, and authorization annotations.

## Core model

The RBAC rewrite follows this model:

```text
Roles grant permissions.
Membership defines academic identity.
Ownership filters data.
Priority limits role management.
Active context decides which assignments are loaded.
```

A tenant admin may have administrative reach over group classes without being listed as a professor or student. A professor or student may only switch to classes where they have actual class membership.

## Permission model

Permissions are code-owned stable strings.

They are modeled in Java as:

```text
AppResource + AppAction -> AppPermission
```

They are stored in the database only as `role.permissions text[]`.

The database must not contain these tables:

```text
resource
action
permission
role_permission
```

The domain must not contain a separate tutor permission resource. Conversation permissions cover tutor usage.

## Runtime model

Authentication, context, and authorization must stay separate:

```text
UserDetails / Authentication
= who is logged in

ActiveContext
= where the user is acting now

UserAccessSnapshot
= what the user can do there
```

Contextual permissions must not be loaded into Spring authorities during login.

## UI model

MainLayout must be built from the active context and current access snapshot.

The navbar context switcher follows these rules:

```text
Platform user
- can switch tenants from the navbar.

Tenant admin
- is tied to one active tenant.
- does not switch tenants.
- may manage group classes through tenant authority.
- is not listed as professor/student unless explicitly assigned as a group-class member.

Professor
- can switch only to classes where member_kind = PROFESSOR.

Student
- can switch only to classes where member_kind = STUDENT.
```

## Testing rule

No broad app startup verification is required from these specs.

Only write small integration tests when the spec explicitly asks for them. Required integration tests must use Testcontainers with PostgreSQL and real schema behavior.

## Status legend

- **Pending** — drafted but not yet implemented.
- **In Progress** — implementation is underway.
- **Implemented** — code and required checks are complete.
- **Verified** — implementation has been manually reviewed against the spec.

## Maintenance rule

When adding, renaming, or replacing an RBAC spec:

1. Keep the numeric order.
2. Update the spec index in this README.
3. State the dependency relationship.
4. Do not leave architectural gaps for the implementation agent to infer.
