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
    TRAINING_ACTIVITY ||--o{ TRAINING_INSTRUCTION_REVIEW : receives_reviews
    GROUP_CLASS_MEMBER ||--o{ TRAINING_INSTRUCTION_REVIEW : requests
    TRAINING_INSTRUCTION_REVIEW ||--o{ TRAINING_INSTRUCTION_REVIEW_OVERRIDE : may_be_overridden
    TRAINING_ACTIVITY ||--o{ TRAINING_INSTRUCTION_REVIEW_OVERRIDE : records_override
    GROUP_CLASS_MEMBER ||--o{ TRAINING_INSTRUCTION_REVIEW_OVERRIDE : confirms
    TRAINING_ACTIVITY ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : assigns
    GROUP_CLASS_MEMBER ||--o{ TRAINING_ACTIVITY_ASSIGNMENT : receives
    TRAINING_ACTIVITY_ASSIGNMENT ||--o{ TRAINING_ACTIVITY_TURN : records
    TRAINING_ACTIVITY_ASSIGNMENT ||--o| TRAINING_ACTIVITY_REPORT : produces
    TRAINING_ACTIVITY_ASSIGNMENT ||--o{ SAFE_BROWSER_SESSION : protects
    SAFE_BROWSER_SESSION ||--o{ SAFE_BROWSER_EVENT : records
    TRAINING_ACTIVITY ||--o{ TRAINING_ACTIVITY_AI_JOB : schedules
    TRAINING_INSTRUCTION_REVIEW ||--o{ TRAINING_ACTIVITY_AI_JOB : schedules_review
    TRAINING_ACTIVITY_ASSIGNMENT ||--o{ TRAINING_ACTIVITY_AI_JOB : schedules_runtime

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
        boolean safe_browser_required
        timestamptz opens_at
        timestamptz closes_at
        timestamptz published_at
        timestamptz closed_at
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    TRAINING_INSTRUCTION_REVIEW {
        uuid id PK
        uuid candidate_id
        uuid training_activity_id FK
        uuid group_class_id FK
        uuid requested_by_group_class_member_id FK
        text title_snapshot
        text instructions_snapshot
        text instructions_hash
        text execution_status "PENDING | SUCCEEDED | FAILED"
        text outcome "GOOD | NEEDS_IMPROVEMENT | INVALID"
        text summary
        jsonb issues
        text improved_instructions
        text model_name
        text rubric_version
        text failure_code
        timestamptz requested_at
        timestamptz completed_at
    }

    TRAINING_INSTRUCTION_REVIEW_OVERRIDE {
        uuid id PK
        uuid training_activity_id FK
        uuid training_instruction_review_id FK
        uuid actor_group_class_member_id FK
        text instructions_hash
        text action "SAVE_DRAFT | PUBLISH"
        timestamptz created_at
    }

    TRAINING_ACTIVITY_ASSIGNMENT {
        uuid id PK
        uuid training_activity_id FK
        uuid group_class_member_id FK
        text status "ASSIGNED | STARTING | WAITING_FOR_ANSWER | WAITING_FOR_TUTOR | SUBMITTED | SKIPPED | EXPIRED | EXCUSED"
        text evidence_status
        text completion_reason
        bigint version
        timestamptz assigned_at
        timestamptz started_at
        timestamptz submitted_at
        timestamptz updated_at
    }

    TRAINING_ACTIVITY_TURN {
        uuid id PK
        uuid training_activity_assignment_id FK
        int sequence_number
        text question_text
        timestamptz question_created_at
        text answer_text
        uuid answer_submission_id
        timestamptz answer_submitted_at
        text decision_type
        text answer_quality
        text evidence_status
        text coverage_status
        text pedagogical_move
        jsonb decision_metadata
        timestamptz created_at
        timestamptz updated_at
    }

    TRAINING_ACTIVITY_REPORT {
        uuid id PK
        uuid training_activity_assignment_id FK,UK
        text status "PENDING | GENERATING | READY | FAILED"
        text evidence_status
        text summary
        jsonb strengths
        jsonb weaknesses
        jsonb observations
        jsonb recommendations
        text model_name
        text prompt_version
        int attempt_count
        text last_error_code
        bigint version
        timestamptz requested_at
        timestamptz completed_at
        timestamptz updated_at
    }

    SAFE_BROWSER_SESSION {
        uuid id PK
        uuid training_activity_assignment_id FK
        text token_hash
        text status "PENDING | ACTIVE | VIOLATED | EXPIRED | ENDED"
        bigint version
        timestamptz started_at
        timestamptz last_heartbeat_at
        timestamptz ended_at
        timestamptz created_at
        timestamptz updated_at
    }

    SAFE_BROWSER_EVENT {
        uuid id PK
        uuid safe_browser_session_id FK
        uuid training_activity_assignment_id FK
        uuid client_event_id
        text event_type
        timestamptz client_occurred_at
        timestamptz received_at
        jsonb metadata
    }

    TRAINING_ACTIVITY_AI_JOB {
        uuid id PK
        text job_type "INSTRUCTION_REVIEW | FIRST_QUESTION | NEXT_DECISION | FINAL_REPORT"
        int priority
        uuid training_activity_id FK
        uuid training_instruction_review_id FK
        uuid training_activity_assignment_id FK
        uuid training_activity_turn_id FK
        uuid training_activity_report_id FK
        bigint input_version
        text semantic_key
        int generation
        text status "PENDING | RUNNING | SUCCEEDED | RETRYABLE | FAILED"
        int attempt_count
        int max_attempts
        timestamptz available_at
        timestamptz lease_until
        text last_error_code
        timestamptz created_at
        timestamptz updated_at
    }

    OUTBOX_EVENT {
        uuid id PK
        text aggregate_type
        uuid aggregate_id
        text event_type
        text deduplication_key UK
        jsonb payload
        text status "PENDING | PROCESSING | PUBLISHED | FAILED"
        int attempt_count
        timestamptz available_at
        timestamptz lease_until
        text last_error_code
        timestamptz created_at
        timestamptz published_at
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
- Title, instructions, Safe Browser setting, and schedule are editable only in `DRAFT`.
- `published_at` and `closed_at` record lifecycle transitions; `version` provides optimistic concurrency.
- Published historical activity definitions are closed/archived, not cascade-deleted.

### `training_instruction_review` and `training_instruction_review_override`

- A review is an immutable advisory result for one exact instructions hash and rubric version.
- `candidate_id` correlates an unsaved editor candidate; `training_activity_id` may be null until the candidate is saved, then may be attached once without changing review content.
- `title_snapshot` is model context; freshness is based on `instructions_snapshot`/`instructions_hash`, not title-only edits.
- An override records explicit professor confirmation for `SAVE_DRAFT` or `PUBLISH` and never bypasses deterministic validation or authorization; its review id may be null when the review is missing or unavailable.
- Abandoned unassociated candidate reviews may be removed by a documented retention policy; attached review/override history is retained.

### `training_activity_assignment`

- Product-facing name: formative activity assignment.
- Targets a student group-class member.
- Status: `ASSIGNED | STARTING | WAITING_FOR_ANSWER | WAITING_FOR_TUTOR | SUBMITTED | SKIPPED | EXPIRED | EXCUSED`.
- `(training_activity_id, group_class_member_id)` is unique.
- Assignment owns runtime state and optimistic version, but does not embed transcript, report, review, or Safe Browser history.

### `training_activity_turn`

- Authoritative ordered question-and-answer evidence for an assignment.
- `(training_activity_assignment_id, sequence_number)` and `(training_activity_assignment_id, answer_submission_id)` are unique when an answer submission id exists.
- Question text and non-null answer text must contain non-whitespace characters.
- One turn accepts at most one authoritative answer.
- Stores backend-validated decision metadata only; hidden model reasoning is prohibited.

### `training_activity_report`

- Exactly one structured report exists per assignment.
- Status: `PENDING | GENERATING | READY | FAILED`.
- Question-answer evidence remains in turns and is not duplicated in generated Markdown.
- Report failure never changes a submitted assignment back to an active state.

### `training_activity_ai_job`

- Durable internal work for review, first question, next decision, and report generation.
- Job foreign keys are nullable according to job type; an instruction-review job references its review candidate, while runtime/report jobs reference the applicable assignment/turn/report.
- `(semantic_key, generation)` is unique, and a partial uniqueness rule prevents more than one live generation for the same semantic input.
- Claims use finite leases; external calls run after the claim transaction ends.
- Result application validates expected `input_version` in a new short transaction.
- Student tutor jobs have higher priority than review and report jobs.

### `safe_browser_session` and `safe_browser_event`

- At most one pending/active session exists per assignment.
- Opaque session tokens are stored hashed and validated with authenticated assignment ownership.
- `VIOLATED`, `EXPIRED`, and `ENDED` sessions are terminal.
- Events are append-only and idempotent by `(safe_browser_session_id, client_event_id)`.
- Professor allowance creates audit history and permits a new session; it never reactivates a terminal session.

### `outbox_event`

- Stores post-commit integration work such as published-activity email notification.
- `deduplication_key` is unique and delivery uses finite leases and bounded retries.
- Domain mutation and outbox insert commit together; provider delivery occurs afterward.

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
