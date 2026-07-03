# Spec 001 — RBAC Schema and Domain Model Replacement

**Status:** Verified  
**Date:** 2026-07-02  
**Depends on:** none  
**Do not:** start the app, run migrations manually, or run the full test suite. The implementer may add small Testcontainers integration tests only where listed.

## Goal

Replace the current RBAC baseline with a simpler model where permissions are code-owned strings, roles are database rows inside role namespaces, classroom identity is separate from RBAC, and ownership is represented explicitly in domain tables that need it.

## Current state to remove

Edit the existing migrations instead of adding a new migration. This is greenfield.

Remove from `src/main/resources/db/migration/prod/V1__baseline.sql`:

- `resource`
- `action`
- `permission`
- `role_permission`
- old `role` definition
- old `account_role`
- old `tenant_account_role`
- `account.last_tenant_account_id`
- `account.last_group_class_member_id`
- `group_class_member.role`

Update `src/main/resources/db/migration/dev/V2__dev_dummy_data.sql` to match the new schema.

Remove or replace these entity/repository classes:

- `data/entities/authorization/Resource.java`
- `data/entities/authorization/Action.java`
- `data/entities/authorization/Permission.java`
- `data/entities/authorization/RolePermission.java`
- their ID classes and repositories
- old role-assignment entity shapes that depend on `bigint role_id`

## Permission catalog

Create code-owned permission metadata. Do not create DB tables for resources, actions, or permissions.

Required package target:

```text
com.wornux.security.permission
```

Required types:

```text
AppResource
AppAction
AppPermission
```

Resources:

```text
TENANT, ACCOUNT, ROLE, SUBJECT, ACADEMIC_PERIOD, GROUP_CLASS,
GROUP_CLASS_MEMBER, GROUP_CLASS_JOIN_CODE, GROUNDING,
CONVERSATION, TRAINING_ACTIVITY, TRAINING_ACTIVITY_ASSIGNMENT,
COURSE_MATERIAL
```

Actions:

```text
VIEW, CREATE, UPDATE, DELETE, ASSIGN, INVITE, LOCK, EXPORT
```

`AppPermission` must store explicit stable codes. Do not derive persisted codes from enum names.

Examples:

```text
tenant:view
role:create
role:assign
group-class-member:invite
conversation:create
training-activity:create
```

There is no `tutor-chat` resource. In this domain, tutor usage is protected by `conversation:create` and conversation access by `conversation:view`.

## New schema

Create `role_namespace` before `tenant`:

```text
role_namespace
- id uuid primary key default gen_random_uuid()
- code text not null unique
- rbac_version bigint not null default 0
- created_at timestamptz not null
- updated_at timestamptz not null
```

Create platform singleton:

```text
platform_settings
- id boolean primary key default true check(id = true)
- role_namespace_id uuid not null unique references role_namespace(id)
```

Add to `tenant`:

```text
role_namespace_id uuid not null unique references role_namespace(id)
```

Replace `role`:

```text
role
- id uuid primary key default gen_random_uuid()
- role_namespace_id uuid not null references role_namespace(id) on delete cascade
- code text not null
- name text not null
- description text null
- assignment_level text not null check in ('PLATFORM','TENANT','GROUP_CLASS')
- permissions text[] not null default '{}'
- priority integer not null default 0
- system_defined boolean not null default false
- assignable boolean not null default true
- active boolean not null default true
- created_by_account_id uuid null references account(id) on delete set null
- created_at timestamptz not null
- updated_at timestamptz not null
- unique(role_namespace_id, code)
```

Assignment tables:

```text
account_platform_role(account_id, role_id, assigned_by_account_id, assigned_at)
tenant_account_role(tenant_account_id, role_id, assigned_by_tenant_account_id, assigned_at)
group_class_member_role(group_class_member_id, role_id, assigned_by_group_class_member_id, assigned_at)
```

All three assignment tables use composite primary keys on their owner id and `role_id`.

Replace classroom identity:

```text
group_class_member
- id uuid primary key default gen_random_uuid()
- group_class_id uuid not null references group_class(id) on delete cascade
- tenant_account_id uuid not null references tenant_account(id) on delete cascade
- member_kind text not null check in ('PROFESSOR','STUDENT','ASSISTANT')
- locked boolean not null default false
- joined_at timestamptz not null
- updated_at timestamptz not null
- unique(group_class_id, tenant_account_id)
```

`member_kind` is roster identity, not RBAC. A tenant admin with tenant-wide class permissions is not a professor unless this table says so.

Replace account last-context columns with:

```text
account_context_preference
- account_id uuid primary key references account(id) on delete cascade
- context_level text null check in ('PLATFORM','TENANT','GROUP_CLASS')
- tenant_id uuid null references tenant(id) on delete set null
- group_class_id uuid null references group_class(id) on delete set null
- updated_at timestamptz not null
```

## Ownership table adjustments

Any table that currently requires `group_class_member_id` as creator must allow tenant-admin authorship without class roster membership.

Change these columns:

```text
conversation:
- add group_class_id uuid not null references group_class(id)
- add created_by_tenant_account_id uuid not null references tenant_account(id)
- make created_by_group_class_member_id uuid null, or rename old group_class_member_id to this nullable column

training_activity:
- add created_by_tenant_account_id uuid not null references tenant_account(id)
- make created_by_group_class_member_id uuid null

group_class_join_code:
- add created_by_tenant_account_id uuid not null references tenant_account(id)
- make created_by_group_class_member_id uuid null
```

Ownership for user-visible conversation rows is by `created_by_tenant_account_id`, not by role or permission. `created_by_group_class_member_id` only records classroom identity when it exists.

## Seed rules

Seed one platform namespace and one platform `System Admin` role.

For each seeded tenant, create one tenant role namespace and seed:

```text
Tenant Admin: assignment_level TENANT, high priority, tenant-wide role permissions and group-class admin permissions.
Professor: assignment_level GROUP_CLASS, professor classroom permissions.
Student: assignment_level GROUP_CLASS, student classroom permissions.
```

Assign default Professor/Student roles through `group_class_member_role`, not through `group_class_member.member_kind` alone.

## Entity/service sync

Create or update JPA entities for the new schema. Use UUID for `role.id`.

Required services:

```text
RoleNamespaceService
RoleSeedService
RoleRepository
RoleAssignmentRepository variants
```

Tenant creation must create a tenant role namespace and seed tenant default roles.

## Minimal integration tests

Use Testcontainers PostgreSQL only. Do not start Vaadin or the full app manually.

Create one small test class for schema/domain persistence:

- creates a tenant and verifies a role namespace exists
- creates one tenant role with `permissions text[]`
- creates one group-class member with `member_kind = PROFESSOR`
- assigns a group-class role to that member
- verifies a tenant admin can exist without a group-class member row

## Acceptance checks

- No `resource`, `action`, `permission`, or `role_permission` table remains in migrations or active entities.
- No `tutor-chat` permission exists.
- `role.permissions` is `text[]`.
- `role.assignment_level` controls which assignment table may be used.
- `group_class_member.member_kind` exists and is not used as a permission source by itself.
- Tenant-admin authorship works without `group_class_member_id`.
- Dev seed data creates usable System Admin, Tenant Admin, Professor, and Student accounts.
