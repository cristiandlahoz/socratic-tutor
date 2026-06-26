# Data Model

> Current entity definitions, relationships, identifiers, constraints, and authorization boundaries for Socratic Tutor. Update this document directly when the persistence baseline changes.

---

## ERD

<schema>
~~~mermaid
erDiagram
    ACCOUNT ||--o{ ACCOUNT_ROLE : receives_global
    ACCOUNT ||--o{ TENANT_ACCOUNT : joins
    TENANT ||--o{ TENANT_ACCOUNT : has_members
    TENANT_ACCOUNT ||--o{ TENANT : owns

    ROLE ||--o{ ACCOUNT_ROLE : assigned_global
    TENANT_ACCOUNT ||--o{ TENANT_ACCOUNT_ROLE : receives_tenant
    ROLE ||--o{ TENANT_ACCOUNT_ROLE : assigned_tenant

    ROLE ||--o{ ROLE_PERMISSION : contains
    PERMISSION ||--o{ ROLE_PERMISSION : included_in

    RESOURCE ||--o{ PERMISSION : protects
    ACTION ||--o{ PERMISSION : defines

    TENANT ||--o{ SUBJECT : has
    TENANT ||--o{ ACADEMIC_PERIOD : has
    TENANT ||--o{ GROUP_CLASS : has

    SUBJECT ||--o{ GROUP_CLASS : groups
    ACADEMIC_PERIOD ||--o{ GROUP_CLASS : contains
    TENANT_ACCOUNT ||--o{ GROUP_CLASS : creates

    GROUP_CLASS ||--o{ GROUP_CLASS_MEMBER : has_members
    TENANT_ACCOUNT ||--o{ GROUP_CLASS_MEMBER : participates_in

    GROUP_CLASS ||--o{ GROUP_CLASS_JOIN_CODE : has_codes
    GROUP_CLASS_MEMBER ||--o{ GROUP_CLASS_JOIN_CODE : creates

    GROUP_CLASS ||--o{ TRAINING_ACTIVITY : has
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY : creates
    TRAINING_ACTIVITY ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : assigns
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : receives

    GROUP_CLASS_MEMBER ||--o{ CONVERSATION : starts
    CONVERSATION ||--o| AI_SESSION : maps_by_id
    AI_SESSION ||--o{ AI_SESSION_EVENT : records

    ACCOUNT {
        uuid id PK
        uuid last_tenant_account_id FK
        uuid last_group_class_member_id FK
        text first_name
        text last_name
        text email UK
        text password_hash
        boolean locked
        timestamptz created_at
        timestamptz updated_at
    }

    TENANT {
        uuid id PK
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
        bigint id PK
        text code UK
        text name
        text description
        boolean assignable
        int priority
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACCOUNT_ROLE {
        uuid account_id PK,FK
        bigint role_id PK,FK
        uuid assigned_by_account_id FK
        timestamptz assigned_at
    }

    TENANT_ACCOUNT_ROLE {
        uuid tenant_account_id PK,FK
        bigint role_id PK,FK
        uuid assigned_by_tenant_account_id FK
        timestamptz assigned_at
    }

    RESOURCE {
        bigint id PK
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACTION {
        bigint id PK
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    PERMISSION {
        bigint id PK
        bigint resource_id FK
        bigint action_id FK
        text code UK
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ROLE_PERMISSION {
        bigint role_id PK,FK
        bigint permission_id PK,FK
        timestamptz granted_at
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

    GROUP_CLASS_MEMBER {
        uuid id PK
        uuid group_class_id FK
        uuid tenant_account_id FK
        text role "PROFESSOR | STUDENT"
        boolean locked
        timestamptz joined_at
        timestamptz updated_at
    }

    GROUP_CLASS_JOIN_CODE {
        bigint id PK
        uuid group_class_id FK
        uuid created_by_group_class_member_id FK
        text code UK
        boolean active
        timestamptz expires_at
        int max_uses
        int used_count
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_VECTOR_STORE {
        uuid id PK
        text content
        json metadata
        vector embedding
    }

    TRAINING_ACTIVITY {
        uuid id PK
        uuid group_class_id FK
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
        uuid group_class_member_id FK
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
~~~
</schema>

---

## Identifier Strategy

Domain entities use UUID primary keys except `role.id`, `resource.id`, `action.id`, `permission.id`, and `group_class_join_code.id`, which use `BIGINT GENERATED BY DEFAULT AS IDENTITY`.

Java types: UUID database IDs map to `java.util.UUID`. bigint identity IDs map to `Long`.

Spring AI Session owns `ai_session` and `ai_session_event`. Their identifiers and column types follow the official library schema. `conversation.id` maps to `ai_session.id` by string value, without a database foreign key, because the domain UUID and library `VARCHAR` identifier types intentionally remain separate.

---

## Entity Rules

### `account`

- Every authenticated user is an `account`.
- `email` is unique and is the only login identifier.
- Passwords stored only as hashes.
- Global platform roles are assigned through `account_role`, not boolean columns.
- `locked` prevents access.
- `last_tenant_account_id` and `last_group_class_member_id` are navigation hints, not authorization sources.

### `tenant`

- Represents an institution/university.
- Not a professor workspace.
- Records the global account that created it for provenance.
- Tenant administration is determined through `tenant_account_role`, not an owner pointer.
- Locked tenants block normal operations.

### `tenant_account`

- Connects one account to one tenant. `(tenant_id, account_id)` unique.
- Tenant-scoped roles are assigned to this entity through `tenant_account_role`.

### `role`

- Codes are stable and unique: `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, `STUDENT`.
- `assignable=false` prevents manual UI assignment.

### Role assignment scope

Authorization assignments are stored at the narrowest scope they govern:

| Role | Assignment table | Scope |
|---|---|---|
| `SYSTEM_ADMIN` | `account_role` | Global platform |
| `TENANT_ADMIN` | `tenant_account_role` | Tenant |
| `PROFESSOR` | `group_class_member` | Group class |
| `STUDENT` | `group_class_member` | Group class |

`account_role` is reserved for global roles that are not bound to a tenant. Do not assign `SYSTEM_ADMIN` through `tenant_account_role`, because that would require a fake tenant context. Do not add role booleans to `account`; new global platform roles should be rows in `role` plus assignments in `account_role`.

### `group_class_member`

- Role constrained to `PROFESSOR | STUDENT`.
- `(group_class_id, tenant_account_id, role)` unique.
- Locked members blocked from normal access.
- Use locking/disabling over deletion.

### `grounding_vector_store`

- Flat pgvector-backed retrieval rows used by the active grounding service.
- Row content is the indexed text payload.
- Metadata carries the group-class scope and ingestion hints.
- Embeddings are stored in the `embedding` column.
- Class ownership is enforced by metadata and service-layer filtering rather than foreign keys.

### `conversation`

- Belongs to one group-class member.
- Students access only their own conversations.
- Owns the title, listing order, access control, and domain metadata.
- `last_prompt_tokens` is nullable and updated only from provider response metadata.
- Its UUID string is the Spring AI Session id.

### `ai_session` and `ai_session_event`

- Use the official Spring AI Session JDBC PostgreSQL schema embedded in V1.
- Store Session lifecycle metadata and the append-only conversation event log.
- `ai_session.user_id` is the owning `group_class_member.id` string.
- Spring AI Session owns event persistence, active-context selection, synthetic summaries, and compaction archiving.
- Application code reads display history from real root user and assistant events after enforcing domain conversation ownership.
- Synthetic, tool, branched, blank, and tool-call assistant events are not normal user-visible history.
- These integration tables are controlled through the domain `CONVERSATION` resource, not exposed as standalone authorization resources.

### `training_activity`

- Product-facing name: formative activity.
- Belongs to one group class.
- Status: `DRAFT | PUBLISHED | CLOSED | ARCHIVED`.

### `training_activity_assignment`

- Product-facing name: formative activity assignment.
- Targets a student group-class member.
- Status: `ASSIGNED | STARTED | SUBMITTED | SKIPPED | EXPIRED | EXCUSED`.

---

## Seed Data

Baseline seeds only foundational authorization data and the system admin account.

**Account:** `admin@wornux.com`, with `SYSTEM_ADMIN` assigned through `account_role`.

**Roles:** `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, `STUDENT`.

**Resources:** `TENANT`, `SUBJECT`, `ACADEMIC_PERIOD`, `GROUP_CLASS`, `GROUP_CLASS_MEMBER`, `GROUP_CLASS_JOIN_CODE`, `GROUNDING`, `TRAINING_ACTIVITY`, `TRAINING_ACTIVITY_ASSIGNMENT`, `CONVERSATION`.

**Actions:** `VIEW`, `CREATE`, `UPDATE`, `DELETE`, `INVITE`.

Do not seed: PUCMM, ICC-101, academic periods, group classes, professors, students, grounding, activities, or conversations.
