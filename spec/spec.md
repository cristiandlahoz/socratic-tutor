# Socratic Tutor System Specification

This document is the top-level specification baseline for Socratic Tutor. It defines the confirmed product and system scope that future use cases, design decisions, data migrations, and implementation work must follow.

## Quick path

1. Treat Socratic Tutor as a learning-first academic tutor, not a generic chat product.
2. Treat `account` as the shared identity aggregate for every authenticated person.
3. Treat `tenant` as the first-class workspace boundary in a hierarchical multi-tenant model.
4. Treat authorization as application-managed and persisted through roles, resources, actions, and permissions.
5. Keep the protected tutor resource scope centered on `chat`, `document`, and `evaluation`.

## System purpose

Socratic Tutor supports academically trustworthy learning workflows for students and educators. Its purpose is to help users reason, practice, and work with learning material through guided tutor conversations, document ingestion, and evaluations, while keeping the experience calm, rigorous, and focused on learning.

## Confirmed product scope

| Area | Baseline decision |
|------|-------------------|
| Primary users | Students and professors / educators |
| Default legacySubject scope | `Introduction to Algorithms` |
| Product posture | Learning-first, academically trustworthy, calm, and focused on reasoning |
| Core tutor capabilities | Chat, document ingestion and management, evaluation management and learner runs |
| Shared identity | Every signed-in person is an `account` |
| Workspace model | Hierarchical multi-tenancy with professor-owned tenant spaces |
| Security model | Authentication and authorization are application-managed inside the application |
| Authorization vocabulary | Roles, resources, actions, permissions, and ownership boundaries |

## Actors and roles

The current foundation supports two confirmed role types:

| Role | Baseline responsibility |
|------|-------------------------|
| `STUDENT` | Uses tutor chat and runs evaluations within learner scope |
| `PROFESSOR` | Uses tutor chat and manages documents and evaluations |

These roles change capabilities, not identity type. Both students and professors are authenticated through the same `account` aggregate.

## Subject and tenant baseline

- The project default legacySubject scope is **Introduction to Algorithms**.
- Each professor owns a tenant space within that legacySubject scope.
- Students belong to a professor tenant.
- `chat`, `document`, and `evaluation` are tenant-scoped resources.
- Professor permissions apply inside professor-owned tenants; they do **not** imply global access across every tenant in the system.

## Identity and access baseline

### Identity

- `account` is the canonical authenticated identity aggregate.
- `tenant` is the canonical workspace boundary for tutor data and membership.
- New specification work must describe authenticated people as accounts, not as separate student/professor identity roots.
- Legacy identifiers such as `client_id` are transitional implementation history, not the target product model.

### Authentication

- Authentication is application-managed.
- The confirmed direction excludes Keycloak as the source of truth for tutor authorization.
- Security components load accounts and derive authorities from application data.

### Authorization

Authorization is defined through the persisted chain:

`account` → `account_role` → `role_permission` → `permission` (`resource` + `action`)

This means tutor access is granted through application data, not through hardcoded UI assumptions, anonymous chat claims, or external role-server mappings. Resource access must also respect tenant boundaries.

### Ownership

Permissions are necessary but not always sufficient.

- `chat` access for students is ownership-scoped to the authenticated `account`.
- `chat`, `document`, and `evaluation` are tenant-scoped.
- `document` and `evaluation` management are primarily role-scoped in the current foundation, but only inside the relevant tenant.
- Future shared-access rules must be introduced explicitly through use cases, not through ad hoc exceptions.

## Protected tutor resources

### `chat`

Chat is the tutor conversation workspace used for guided reasoning and continuity across tutor interactions.

Baseline rules:

- It is a protected tutor resource.
- Students and professors may have chat permissions according to the seeded role matrix.
- Student access must remain scoped to chats owned by the authenticated account inside the student's tenant.
- Anonymous conversations are out of scope for this foundation.

### `document`

Document represents ingested academic material used by the tutor.

Baseline rules:

- It is a protected tutor resource.
- In the current scope, document capabilities are professor-managed inside professor-owned tenants.
- Students do not receive document permissions in the current foundation.

### `evaluation`

Evaluation represents structured assessment artifacts and learner runs.

Baseline rules:

- It is a protected tutor resource.
- Professors manage evaluation definitions inside their own tenants.
- Students are limited to learner-facing evaluation execution through `evaluation:run` inside their assigned tenants.

## Authorization matrix baseline

| Role | Resource | Allowed actions |
|------|----------|-----------------|
| `STUDENT` | `chat` | `view`, `create`, `update`, `delete` |
| `STUDENT` | `evaluation` | `run` |
| `STUDENT` | `document` | None |
| `PROFESSOR` | `chat` | `view`, `create`, `update`, `delete` |
| `PROFESSOR` | `document` | `view`, `create`, `update`, `delete` |
| `PROFESSOR` | `evaluation` | `create`, `view`, `update`, `delete` |

This matrix is the current confirmed baseline. It should expand only when a future approved use case requires it.

## Data and implementation baseline

The current system specification assumes:

- a persisted `account` aggregate for authenticated people,
- a persisted `tenant` aggregate and tenant membership relationships,
- persisted `role`, `resource`, `action`, `permission`, `account_role`, and `role_permission` relationships,
- tenant and ownership fields that link protected tutor records back to tenants and accounts,
- application-managed Spring Security wiring that derives authorities from persisted data,
- service-level authorization, tenant boundary, and ownership checks for protected tutor workflows.

This baseline aligns product intent, architecture, and data model. Future implementation should migrate legacy ownership fields toward tenant-linked and account-linked ownership without broadening scope.

## Current use-case anchor

UC-001 is the active foundation use case for this specification baseline. It establishes that:

- authenticated people share one `account` identity model,
- the default legacySubject scope is `Introduction to Algorithms` with professor-owned tenant spaces,
- newly registered people receive `STUDENT` by default,
- existing professor accounts retain `PROFESSOR`,
- each professor is the owner of its tenant, 1 to 1,
- each professors can see and manage tutor resources inside their tenant, but not across every tenant,
- tutor capabilities are granted only through persisted roles and permissions inside tenant boundaries,
- unauthorized access must fail without exposing restricted tutor data.

## Non-goals at this stage

This baseline does not define or approve:

- additional product resource families beyond `chat`, `document`, and `evaluation`,
- Keycloak-managed authorization,
- separate identity roots for students and professors,
- anonymous or browser-only ownership as the long-term tutor model,
- global professor access across every tenant,
- expanded role catalogs beyond the currently confirmed `STUDENT` and `PROFESSOR` roles.

## Specification rules for future work

Use this document as the baseline when writing or reviewing new use cases, designs, or migrations.

### Checklist

- [ ] New specs use `account` as the shared identity term.
- [ ] New specs use `tenant` as the shared workspace boundary term.
- [ ] New specs keep authorization expressed as roles, resources, actions, permissions, and ownership.
- [ ] New tutor features are mapped to `chat`, `document`, or `evaluation`, or explicitly justify a scope expansion.
- [ ] New access rules do not reintroduce Keycloak assumptions or `client_id` as the target model.
- [ ] New access rules do not imply cross-tenant professor access unless a use case explicitly adds it.
- [ ] New role or permission additions are justified by a concrete approved use case.

## Next step

Use this system specification together with `project-context.md`, `architecture.md`, `datamodel/datamodel.md`, and UC-001 as the baseline for future feature use cases and for implementation planning that realizes the application-managed account, tenant, and authorization model.
