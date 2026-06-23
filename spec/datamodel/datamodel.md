# Data Model

> Entity definitions, relationships, identifiers, constraints, and authorization boundaries for Socratic Tutor.

---

## ERD

<schema>
~~~mermaid
erDiagram
    ACCOUNT ||--o{ TENANT_ACCOUNT : joins
    TENANT ||--o{ TENANT_ACCOUNT : has_members
    TENANT_ACCOUNT ||--o{ TENANT : owns

    TENANT_ACCOUNT ||--o{ TENANT_ACCOUNT_ROLE : receives
    ROLE ||--o{ TENANT_ACCOUNT_ROLE : assigned_as

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

    GROUP_CLASS ||--o{ GROUNDING_COLLECTION : has
    GROUP_CLASS_MEMBER ||--o{ GROUNDING_COLLECTION : creates
    GROUNDING_COLLECTION ||--o{ GROUNDING_DOCUMENT : contains
    GROUNDING_DOCUMENT ||--o{ GROUNDING_CHUNK : splits_into

    GROUP_CLASS ||--o{ TRAINING_ACTIVITY : has
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY : creates
    TRAINING_ACTIVITY ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : assigns
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : receives

    GROUP_CLASS_MEMBER ||--o{ CONVERSATION : starts
    CONVERSATION ||--o{ CONVERSATION_SNAPSHOT : has
    CONVERSATION_SNAPSHOT ||--o| CONVERSATION_SNAPSHOT : previous

    ACCOUNT {
        uuid id PK
        uuid last_tenant_account_id FK
        uuid last_group_class_member_id FK
        text first_name
        text last_name
        text email UK
        text username UK
        text password_hash
        boolean system_admin
        boolean locked
        timestamptz created_at
        timestamptz updated_at
    }

    TENANT {
        uuid id PK
        uuid owner_tenant_account_id FK
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
        text code UK
        text name
        text description
        boolean assignable
        int priority
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    TENANT_ACCOUNT_ROLE {
        uuid tenant_account_id PK,FK
        uuid role_id PK,FK
        uuid assigned_by_tenant_account_id FK
        timestamptz assigned_at
    }

    RESOURCE {
        uuid id PK
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACTION {
        uuid id PK
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    PERMISSION {
        uuid id PK
        uuid resource_id FK
        uuid action_id FK
        text code UK
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ROLE_PERMISSION {
        uuid role_id PK,FK
        uuid permission_id PK,FK
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
        uuid id PK
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

    GROUNDING_COLLECTION {
        uuid id PK
        uuid group_class_id FK
        uuid created_by_group_class_member_id FK
        text name
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_DOCUMENT {
        uuid id PK
        uuid collection_id FK
        text title
        text source_type "UPLOAD | TEXT"
        text storage_key
        text status "PROCESSING | READY | FAILED | INACTIVE"
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_CHUNK {
        uuid id PK
        uuid document_id FK
        int chunk_index
        text content
        vector embedding
        boolean active
        timestamptz created_at
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
        uuid current_snapshot_id FK
        text title
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    CONVERSATION_SNAPSHOT {
        bigint id PK
        uuid conversation_id FK
        uuid previous_snapshot_id FK
        bigint snapshot_no
        jsonb carry_context
        jsonb messages
        int message_count
        int token_count
        bigint version
        timestamptz created_at
        timestamptz compacted_at
    }
~~~
</schema>

---

## Identifier Strategy

All entities use UUID primary keys except `conversation_snapshot.id` which uses `BIGINT GENERATED BY DEFAULT AS IDENTITY`.

Java types: UUID database IDs map to `java.util.UUID`. `conversation_snapshot.id` maps to `Long`.

---

## Entity Rules

### `account`

- Every authenticated user is an `account`.
- `email` and `username` are unique.
- Passwords stored only as hashes.
- `system_admin` identifies global platform operators.
- `locked` prevents access.
- `last_tenant_account_id` and `last_group_class_member_id` are navigation hints, not authorization sources.

### `tenant`

- Represents an institution/university.
- Not a professor workspace.
- May have an owner tenant account.
- Locked tenants block normal operations.

### `tenant_account`

- Connects one account to one tenant. `(tenant_id, account_id)` unique.
- Roles are assigned to this entity, not directly to accounts.

### `role`

- Codes are stable and unique: `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, `STUDENT`.
- `assignable=false` prevents manual UI assignment.

### `group_class_member`

- Role constrained to `PROFESSOR | STUDENT`.
- `(group_class_id, tenant_account_id, role)` unique.
- Locked members blocked from normal access.
- Use locking/disabling over deletion.

### `conversation`

- Belongs to one group-class member.
- Has zero or one current snapshot via `current_snapshot_id`.
- Students access only their own conversations.

### `conversation_snapshot`

- Uses BIGINT identity.
- Messages stored in `messages` JSONB.
- Compacted context in `carry_context` JSONB.
- Controlled through `CONVERSATION` resource.

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

**Account:** `admin@socratic-tutor.com`, `username=admin`, `system_admin=true`.

**Roles:** `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, `STUDENT`.

**Resources:** `TENANT`, `SUBJECT`, `ACADEMIC_PERIOD`, `GROUP_CLASS`, `GROUP_CLASS_MEMBER`, `GROUP_CLASS_JOIN_CODE`, `GROUNDING`, `TRAINING_ACTIVITY`, `TRAINING_ACTIVITY_ASSIGNMENT`, `CONVERSATION`.

**Actions:** `VIEW`, `CREATE`, `UPDATE`, `DELETE`, `INVITE`.

Do not seed: PUCMM, ICC-101, academic periods, group classes, professors, students, grounding, activities, or conversations.
