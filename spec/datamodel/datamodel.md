# Data Model

This document defines the target relational model for Socratic Tutor's identity, tenancy, authorization, and ownership foundation. It is the reference for future Flyway migrations that move the product from legacy `client_id` ownership toward an `account`- and `tenant`-centered, application-managed security model.

## Quick path

1. Treat `account` as the canonical identity aggregate for every authenticated person.
2. Treat `tenant` as the first-class workspace aggregate under the default legacySubject scope **Introduction to Algorithms**.
3. Model authorization as persisted roles, resources, actions, permissions, and join tables.
4. Keep tutor resources centered on `chat`, `document`, and `evaluation`, with explicit tenant and account boundaries where required.

## Scope of this model

This foundation covers:

- shared identity through `account`,
- hierarchical workspace boundaries through `tenant`,
- authorization through role and permission relationships,
- ownership boundaries for tutor resources,
- seeded reference data required for UC-001.

It does **not** define every tutor-supporting table in detail. Existing learning-profile, transcript, ingestion, and legacySubject tables may remain, but future schema work should anchor security and ownership on `account` and `tenant` instead of expanding legacy `client_id` usage.

## Model summary

| Area | Target decision |
|------|-----------------|
| Identity root | `account` is the single authenticated aggregate for students and professors. |
| Workspace root | `tenant` is the first-class professor-owned workspace inside the default legacySubject scope `Introduction to Algorithms`. |
| Authorization | Authorities derive from `account` → `account_role` → `role_permission` → `permission`. |
| Protected resources | The initial protected resource set is `chat`, `document`, and `evaluation`. |
| Ownership | All three protected resources are tenant-scoped; chat is also ownership-scoped to an account; document and evaluation management are professor-scoped within tenant boundaries in the current foundation. |
| Security source of truth | The application database stores accounts, tenants, membership, role assignments, permissions, and policy seed data. |
| Legacy context | `client_id` is transitional migration context only, not the target identity model. |

## Domain entities

These tables represent business concepts with their own lifecycle.

### `account`

Represents the authenticated person.

Suggested columns:

- `id` (UUID, PK)
- `email` (unique)
- `password_hash`
- `display_name`
- `status` (for example `ACTIVE`, `DISABLED`, `PENDING_VERIFICATION` if needed later)
- `created_at`, `updated_at`

Rules:

- Every signed-in student or professor is an `account`.
- New self-service registrations receive the `STUDENT` role by default through `account_role`.
- Authentication and Spring Security principal loading should resolve from this table, not from anonymous cookies or external role claims.

### `tenant`

Represents a professor-owned academic workspace inside the default legacySubject scope.

Suggested columns:

- `id` (UUID, PK)
- `subject_code` or `subject_slug` (seeded initially to `introduction-to-algorithms`)
- `name`
- `owner_professor_account_id` (FK → `account.id`)
- `status`
- `created_at`, `updated_at`

Rules:

- A tenant belongs to exactly one professor owner account.
- The current foundation assumes the project legacySubject root is **Introduction to Algorithms**.
- Tenant ownership does not imply cross-tenant professor access.
- Chat, document, and evaluation records must reference a tenant.

### `tenant_membership`

Represents account participation in a tenant.

Suggested columns:

- `tenant_id` (FK → `tenant.id`)
- `account_id` (FK → `account.id`)
- `membership_type` (for example `PROFESSOR_OWNER`, `STUDENT_MEMBER`)
- `created_at`
- composite unique key on (`tenant_id`, `account_id`)

Rules:

- Every professor owner must also be represented as a tenant membership.
- Students belong to a professor tenant through this table.
- Membership is the schema anchor for future tenant-bound authorization checks and data filtering.

### `chat`

Represents a tutor conversation workspace.

Target ownership columns:

- `id` (UUID, PK)
- `tenant_id` (FK → `tenant.id`)
- `owner_account_id` (FK → `account.id`)
- `title`
- `current_transcript_id`
- `created_at`, `updated_at`

Rules:

- A chat belongs to exactly one tenant and one owning account.
- Student access to a chat requires `chat:{action}` permission, tenant membership alignment, and ownership match on `owner_account_id`.
- `chat_transcript` and `chat_message` stay downstream of `chat` and inherit access through the parent chat.

### `document`

Represents an ingested academic document managed by the tutor. `document` is the **target model name** used throughout this specification; the currently deployed table name `ingested_document` is legacy schema vocabulary that future Flyway work should map into this target aggregate rather than treat as a separate domain concept.

Target ownership columns:

- `id` (UUID, PK)
- `tenant_id` (FK → `tenant.id`)
- `owner_account_id` (FK → `account.id`)
- file and ingestion metadata
- catalog and review fields
- `created_at`, `updated_at`

Rules:

- In the current foundation, document management is professor-scoped through permissions inside the owning tenant.
- Ownership should still be explicit so the product can later distinguish which professor uploaded or manages a document.
- `document_ingestion_job` and `document_segment` remain child records of `document`.

### `evaluation`

Represents an evaluation definition or managed assessment artifact.

Suggested target columns:

- `id` (UUID, PK)
- `tenant_id` (FK → `tenant.id`)
- `owner_account_id` (FK → `account.id`)
- `title`
- `instruction`
- structured question / answer payload fields
- `status`
- `created_at`, `updated_at`

Rules:

- Evaluation management belongs to professor-authorized accounts in this foundation and remains tenant-scoped.
- Ownership should be explicit for provenance and future auditing even when access is primarily role-scoped.

### `evaluation_run`

Represents a learner execution of an evaluation.

Target ownership columns:

- `id` (UUID, PK)
- `evaluation_id` (FK → `evaluation.id`)
- `tenant_id` (FK → `tenant.id`)
- `student_account_id` (FK → `account.id`)
- learner response / report fields
- `status`
- `created_at`, `updated_at`

Rules:

- A run belongs to one tenant, one learner account, and one evaluation.
- `evaluation:run` is the only evaluation permission granted to `STUDENT` in UC-001.
- Within this foundation, `evaluation:run` covers starting a learner run and reading that learner's own `evaluation_run` state/results for the same `student_account_id` inside the same tenant; it does **not** grant access to evaluation-definition management or to another learner's runs.
- Future access checks should ensure learners only access their own runs unless a later use case adds educator review access.

## Relationship entities / tables

These tables exist to express many-to-many or policy relationships cleanly.

### `account_role`

Assigns roles to accounts.

Suggested columns:

- `account_id` (FK → `account.id`)
- `role_id` (FK → `role.id`)
- `assigned_at`
- `assigned_by_account_id` (nullable FK → `account.id`, optional but useful)
- composite unique key on (`account_id`, `role_id`)

This table is the default-role entry point for new registrations.

Note: role assignment remains global to the account identity, while tenant membership determines which tenant context the account can act within.

### `permission`

Represents an allowed `resource:action` pair.

Suggested columns:

- `id` (PK)
- `resource_id` (FK → `resource.id`)
- `action_id` (FK → `action.id`)
- unique key on (`resource_id`, `action_id`)

This table is the authority vocabulary consumed by security components.

### `role_permission`

Assigns permissions to roles.

Suggested columns:

- `role_id` (FK → `role.id`)
- `permission_id` (FK → `permission.id`)
- composite unique key on (`role_id`, `permission_id`)

This is the policy matrix that drives derived Spring Security authorities.

## Seeded reference data

These tables contain stable reference values that should be seeded by Flyway and treated as controlled vocabulary.

### `role`

Seed at least:

| Code | Purpose |
|------|---------|
| `STUDENT` | Default self-service learner role. |
| `PROFESSOR` | Educator role with document and evaluation management capabilities. |

### `resource`

Seed exactly this foundation set:

| Code | Meaning |
|------|---------|
| `chat` | Tutor conversation workspace and its child records. |
| `document` | Ingested academic material and its review / segmentation flow. |
| `evaluation` | Evaluation definitions and learner runs. |

### `action`

Seed at least:

| Code | Meaning |
|------|---------|
| `view` | Read or list a protected resource. |
| `create` | Create a new protected resource. |
| `update` | Modify an existing protected resource. |
| `delete` | Delete or remove a protected resource. |
| `run` | Execute a learner-facing evaluation action. |

### `permission` seed matrix

Seed only the tutor-specific combinations required by UC-001.

| Permission | Granted to |
|------------|------------|
| `chat:view` | `STUDENT`, `PROFESSOR` |
| `chat:create` | `STUDENT`, `PROFESSOR` |
| `chat:update` | `STUDENT`, `PROFESSOR` |
| `chat:delete` | `STUDENT`, `PROFESSOR` |
| `document:view` | `PROFESSOR` |
| `document:create` | `PROFESSOR` |
| `document:update` | `PROFESSOR` |
| `document:delete` | `PROFESSOR` |
| `evaluation:create` | `PROFESSOR` |
| `evaluation:view` | `PROFESSOR` |
| `evaluation:update` | `PROFESSOR` |
| `evaluation:delete` | `PROFESSOR` |
| `evaluation:run` | `STUDENT` |

## Ownership boundaries

| Resource | Ownership rule | Authorization implication |
|----------|----------------|---------------------------|
| `chat` | Each chat row belongs to one `tenant_id` and one `owner_account_id`. | Permission check is necessary but not sufficient; student chat access must also match tenant boundary and owner account. |
| `document` | Each document belongs to one `tenant_id` and should record its owning or creating account. | Current foundation is professor role-scoped within tenant boundaries; ownership mainly supports provenance and future policy evolution. |
| `evaluation` | Each evaluation belongs to one `tenant_id` and should record its owning professor account. | Management actions are professor-only in the current foundation and must stay inside the owning tenant. |
| `evaluation_run` | Each run belongs to one `tenant_id` and one `student_account_id`. | `evaluation:run` allows a student to create and read only their own run records inside the same tenant; broader evaluation management remains professor-only unless a later use case extends visibility. |

## Relationship to existing tutor tables

The current schema already contains tutor-domain tables such as `chat_transcript`, `chat_message`, `document_ingestion_job`, `document_segment`, `student_profile`, `student_misconception`, and `student_profile_signal`.

Future migrations should treat them like this:

- child records of `chat` and `document` continue to inherit access from their parent aggregate,
- learner-profile tables should eventually reference `account` as the owning identity,
- tenant-aware tutor tables should eventually reference `tenant` as the primary workspace boundary,
- new tables must not introduce fresh `client_id`-based ownership.

## Transitional legacy note

The current implementation still stores legacy identifiers such as:

- `chat.client_id`
- `ingested_document.client_id` (`ingested_document` is the current legacy table name for the target `document` aggregate)
- `evaluation_run.student_client_id`
- `student_profile.client_id` and related profile tables

These fields describe the **current schema state**, not the target design. Future Flyway work should migrate toward tenant-linked and account-linked ownership columns such as `tenant_id`, `owner_account_id`, or `student_account_id`, with compatibility steps only where necessary to preserve existing data.

## Migration guidance

When translating this document into Flyway migrations:

1. Create the security and tenancy foundation first: `account`, `tenant`, `tenant_membership`, `role`, `resource`, `action`, `permission`, `account_role`, `role_permission`.
2. Seed the reference tables and role-permission matrix before enabling protected tutor flows.
3. Add `tenant_id` plus account ownership columns to `chat`, `document`, `evaluation`, and `evaluation_run`, then backfill from legacy data where possible.
4. Move service and security code to derive authorities from persisted permissions while enforcing tenant membership boundaries.
5. Retire legacy `client_id` as an authorization key once account- and tenant-based ownership is fully enforced.

## Next step

Use this data model as the schema baseline for the first Flyway migrations and security implementation that realize UC-001 without reintroducing `client_id`, anonymous conversations, Keycloak assumptions, or unrelated resource families.
