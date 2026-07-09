# Data Model

> Current entity definitions, relationships, identifiers, constraints, and authorization boundaries for Socratic Tutor. Update this document directly when the persistence baseline changes.

---

## ERD

<schema>
~~~mermaid
erDiagram
    ROLE_NAMESPACE ||--o{ ROLE : contains
    ROLE_NAMESPACE ||--o| PLATFORM_SETTINGS : configures_platform
    ROLE_NAMESPACE ||--o| TENANT : scopes_tenant_roles

    ACCOUNT ||--o{ ACCOUNT_PLATFORM_ROLE : receives_platform_roles
    ROLE ||--o{ ACCOUNT_PLATFORM_ROLE : assigned_at_platform

    ACCOUNT ||--o{ TENANT_ACCOUNT : joins_tenant
    TENANT ||--o{ TENANT_ACCOUNT : has_members
    TENANT_ACCOUNT ||--o{ TENANT_ACCOUNT_ROLE : receives_tenant_roles
    ROLE ||--o{ TENANT_ACCOUNT_ROLE : assigned_at_tenant

    TENANT ||--o{ SUBJECT : offers
    TENANT ||--o{ ACADEMIC_PERIOD : defines
    TENANT ||--o{ GROUP_CLASS : owns
    SUBJECT ||--o{ GROUP_CLASS : categorizes
    ACADEMIC_PERIOD ||--o{ GROUP_CLASS : schedules
    TENANT_ACCOUNT ||--o{ GROUP_CLASS : creates

    ACCOUNT ||--o| ACCOUNT_CONTEXT_PREFERENCE : stores_last_context
    TENANT ||--o{ ACCOUNT_CONTEXT_PREFERENCE : preferred_tenant
    GROUP_CLASS ||--o{ ACCOUNT_CONTEXT_PREFERENCE : preferred_class

    GROUP_CLASS ||--o{ GROUP_CLASS_MEMBER : has_members
    TENANT_ACCOUNT ||--o{ GROUP_CLASS_MEMBER : participates_as
    GROUP_CLASS_MEMBER ||--o{ GROUP_CLASS_MEMBER_ROLE : receives_class_roles
    ROLE ||--o{ GROUP_CLASS_MEMBER_ROLE : assigned_at_class

    GROUP_CLASS ||--o{ CONVERSATION : contains
    TENANT_ACCOUNT ||--o{ CONVERSATION : creates
    GROUP_CLASS_MEMBER ||--o{ CONVERSATION : creates_as_class_member
    CONVERSATION ||--o| AI_SESSION : uses_uuid_as_session_id
    AI_SESSION ||--o{ AI_SESSION_EVENT : records

    GROUP_CLASS ||--o{ TRAINING_ACTIVITY : has
    TENANT_ACCOUNT ||--o{ TRAINING_ACTIVITY : creates
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY : creates_as_class_member
    TRAINING_ACTIVITY ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : assigns
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : receives

    TENANT ||--o{ INVITATION : scopes
    GROUP_CLASS ||--o{ INVITATION : optionally_targets
    ACCOUNT ||--o{ INVITATION : sends
    TENANT_ACCOUNT ||--o{ INVITATION : sends_as_tenant_member
    GROUP_CLASS_MEMBER ||--o{ INVITATION : sends_as_class_member

    ACCOUNT {
        uuid id PK
        text first_name
        text last_name
        text email UK
        text password_hash
        boolean locked
        timestamptz created_at
        timestamptz updated_at
    }

    ROLE_NAMESPACE {
        uuid id PK
        text code UK
        bigint rbac_version
        timestamptz created_at
        timestamptz updated_at
    }

    PLATFORM_SETTINGS {
        boolean id PK
        uuid role_namespace_id FK,UK
    }

    TENANT {
        uuid id PK
        uuid role_namespace_id FK,UK
        uuid created_by_account_id FK
        text name
        boolean locked
        timestamptz created_at
        timestamptz updated_at
    }

    TENANT_ACCOUNT {
        uuid id PK
        uuid tenant_id FK
        uuid account_id FK
        boolean locked
        timestamptz joined_at
        timestamptz updated_at
    }

    ROLE {
        uuid id PK
        uuid role_namespace_id FK
        text code
        text name
        text description
        text assignment_level "PLATFORM | TENANT | GROUP_CLASS"
        text_array permissions
        int priority
        boolean system_defined
        boolean assignable
        boolean active
        uuid created_by_account_id FK
        timestamptz created_at
        timestamptz updated_at
    }

    ACCOUNT_PLATFORM_ROLE {
        uuid account_id PK,FK
        uuid role_id PK,FK
        uuid assigned_by_account_id FK
        timestamptz assigned_at
    }

    TENANT_ACCOUNT_ROLE {
        uuid tenant_account_id PK,FK
        uuid role_id PK,FK
        uuid assigned_by_tenant_account_id FK
        timestamptz assigned_at
    }

    SUBJECT {
        uuid id PK
        uuid tenant_id FK
        text code
        text name
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACADEMIC_PERIOD {
        uuid id PK
        uuid tenant_id FK
        text code
        text name
        date starts_at
        date ends_at
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    GROUP_CLASS {
        uuid id PK
        uuid tenant_id FK
        uuid subject_id FK
        uuid academic_period_id FK
        uuid created_by_tenant_account_id FK
        text code
        text name
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACCOUNT_CONTEXT_PREFERENCE {
        uuid account_id PK,FK
        text context_level "PLATFORM | TENANT | GROUP_CLASS"
        uuid tenant_id FK
        uuid group_class_id FK
        timestamptz updated_at
    }

    GROUP_CLASS_MEMBER {
        uuid id PK
        uuid group_class_id FK
        uuid tenant_account_id FK
        text member_kind "PROFESSOR | STUDENT | ASSISTANT"
        boolean locked
        timestamptz joined_at
        timestamptz updated_at
    }

    GROUP_CLASS_MEMBER_ROLE {
        uuid group_class_member_id PK,FK
        uuid role_id PK,FK
        uuid assigned_by_group_class_member_id FK
        timestamptz assigned_at
    }

    TRAINING_ACTIVITY {
        uuid id PK
        uuid group_class_id FK
        uuid created_by_tenant_account_id FK
        uuid created_by_group_class_member_id FK
        text title
        text instructions
        text status "DRAFT | PUBLISHED | CLOSED | ARCHIVED"
        timestamptz opens_at
        timestamptz closes_at
        timestamptz created_at
        timestamptz updated_at
    }

    TRAINING_ACTIVITY_ASSIGNMENT {
        uuid id PK
        uuid training_activity_id FK
        uuid group_class_member_id FK
        text status "ASSIGNED | STARTED | SUBMITTED | SKIPPED | EXPIRED | EXCUSED"
        timestamptz assigned_at
        timestamptz started_at
        timestamptz submitted_at
        timestamptz updated_at
    }

    CONVERSATION {
        uuid id PK
        uuid group_class_id FK
        uuid created_by_tenant_account_id FK
        uuid created_by_group_class_member_id FK
        text title
        int last_prompt_tokens
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    AI_SESSION {
        varchar id PK
        varchar user_id
        timestamp created_at
        timestamp expires_at
        text metadata
        bigint event_version
    }

    AI_SESSION_EVENT {
        bigint seq
        varchar id PK
        varchar session_id FK
        timestamp timestamp
        varchar message_type
        text message_content
        text message_data
        boolean synthetic
        boolean archived
        varchar branch
        text metadata
    }

    INVITATION {
        bigint id PK
        uuid tenant_id FK
        uuid group_class_id FK
        text invited_email
        text target_role "TENANT_ADMIN | PROFESSOR | STUDENT"
        text token_hash UK
        text status "PENDING | ACCEPTED | EXPIRED | REVOKED | DELIVERY_FAILED"
        text delivery_error
        timestamptz expires_at
        timestamptz accepted_at
        uuid invited_by_account_id FK
        uuid invited_by_tenant_account_id FK
        uuid invited_by_group_class_member_id FK
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_VECTOR_STORE {
        uuid id PK
        text content
        json metadata
        vector embedding
    }
~~~
</schema>

---

## Identifier Strategy

Domain entities use UUID primary keys except `invitation.id`, which uses `BIGINT GENERATED BY DEFAULT AS IDENTITY`.

Java types: UUID database IDs map to `java.util.UUID`. bigint identity IDs map to `Long`.

Spring AI Session owns `ai_session` and `ai_session_event`. Their identifiers and column types follow the official library schema. `conversation.id` maps to `ai_session.id` by string value, without a database foreign key, because the domain UUID and library `VARCHAR` identifier types intentionally remain separate.

---

## Entity Rules

### `account`

- Every authenticated user is an `account`.
- `email` is unique and is the only login identifier.
- Passwords stored only as hashes.
- Global platform roles are assigned through `account_platform_role`, not boolean columns.
- `locked` prevents access.
- The last selected runtime context is stored in `account_context_preference`, not on the account row.

### `role_namespace`

- Partitions roles into one platform namespace and one namespace per tenant.
- `rbac_version` changes whenever roles or assignments in the namespace change.
- Access snapshots include the namespace id and version in their cache key.

### `tenant`

- Represents an institution/university.
- Not a professor workspace.
- Records the global account that created it for provenance.
- Tenant administration is determined through `tenant_account_role`, not an owner pointer.
- Owns a tenant-specific `role_namespace`.
- Locked tenants block normal operations.

### `tenant_account`

- Connects one account to one tenant. `(tenant_id, account_id)` unique.
- Tenant-scoped roles are assigned to this entity through `tenant_account_role`.
- The same account can have different tenant roles in different tenants.

### `role`

- A role is a permission bundle inside a `role_namespace`.
- `(role_namespace_id, code)` is unique.
- `assignment_level` determines the assignment table: `PLATFORM`, `TENANT`, or `GROUP_CLASS`.
- `permissions text[]` stores stable permission codes owned by `AppPermission`; there are no resource/action/permission join tables.
- `priority` defines the management boundary for role creation, update, and assignment.
- `system_defined` identifies seeded roles; `assignable=false` prevents manual UI assignment.

### Role assignment scope

Authorization assignments are stored at the narrowest scope they govern:

| Assignment level | Assignment table | Scope |
|---|---|---|
| `PLATFORM` | `account_platform_role` | Global platform |
| `TENANT` | `tenant_account_role` | Tenant membership |
| `GROUP_CLASS` | `group_class_member_role` | Class membership |

`account_platform_role` is reserved for global roles that are not bound to a tenant. Tenant roles attach to `tenant_account`. Class roles attach to `group_class_member`. Academic identity remains in `group_class_member.member_kind`; RBAC roles grant capabilities for that identity and context.

### `account_context_preference`

- Stores the account's last selected `ActiveContext`.
- `context_level` is `PLATFORM`, `TENANT`, or `GROUP_CLASS`.
- Tenant context stores `tenant_id`; group-class context stores both `tenant_id` and `group_class_id`.
- Login restores the saved context only when it is still discoverable for the account.

### `group_class_member`

- `member_kind` is constrained to `PROFESSOR | STUDENT | ASSISTANT`.
- `(group_class_id, tenant_account_id)` is unique.
- Locked members blocked from normal access.
- Use locking/disabling over deletion.
- Group-class scoped RBAC roles are assigned through `group_class_member_role`.

### `grounding_vector_store`

- Flat pgvector-backed retrieval rows used by the active grounding service.
- Row content is the indexed text payload.
- Metadata carries the group-class scope and ingestion hints.
- Embeddings are stored in the `embedding` column.
- Class ownership is enforced by metadata and service-layer filtering rather than foreign keys.

### `conversation`

- Belongs to one group class.
- Records the tenant account that created it and optionally the class membership used to create it.
- Students access their own conversations through service-layer ownership checks.
- Owns the title, listing order, access control, and domain metadata.
- `last_prompt_tokens` is nullable and updated only from provider response metadata.
- Its UUID string is the Spring AI Session id.

### `ai_session` and `ai_session_event`

- Use the official Spring AI Session JDBC PostgreSQL schema embedded in V1.
- Store Session lifecycle metadata and the append-only conversation event log.
- `ai_session.user_id` stores the application principal/session owner string used by Spring AI Session.
- Spring AI Session owns event persistence, synthetic summaries, and compaction archiving.
- Application code reads display history from real root user and assistant events after enforcing domain conversation ownership.
- Synthetic, tool, branched, blank, and tool-call assistant events are not normal user-visible history.
- These integration tables are controlled through the domain `CONVERSATION` resource, not exposed as standalone authorization resources.

### `training_activity`

- Product-facing name: formative activity.
- Belongs to one group class.
- Records the tenant account that created it and optionally the class membership used to create it.
- Status: `DRAFT | PUBLISHED | CLOSED | ARCHIVED`.

### `training_activity_assignment`

- Product-facing name: formative activity assignment.
- Targets a student group-class member.
- Status: `ASSIGNED | STARTED | SUBMITTED | SKIPPED | EXPIRED | EXCUSED`.

### `invitation`

- Represents pending onboarding into a tenant or group class.
- `target_role` is `TENANT_ADMIN`, `PROFESSOR`, or `STUDENT`.
- Tenant-admin invitations target a tenant; professor/student invitations target a group class.
- Stores hashed tokens only.

---

## Seed Data

Baseline seeds only foundational authorization data and the system admin account.

**Account:** `admin@wornux.com`, with `SYSTEM_ADMIN` assigned through `account_platform_role`.

**Platform namespace:** `platform`, connected through `platform_settings`.

**Role:** `SYSTEM_ADMIN`, assigned through `account_platform_role`.

Tenant-scoped and group-class-scoped default roles are created when tenants are created or by dev seed data, depending on the environment.

Do not seed: PUCMM, ICC-101, academic periods, group classes, professors, students, grounding, activities, or conversations.
