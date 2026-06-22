# Socratic Tutor System Specification

# Instructions

<p>
Build and maintain Socratic Tutor as a Spring Boot + Vaadin Flow academic tutoring web application. The system must support authenticated, role-based, academically scoped tutoring workflows for system admins, tenant admins, professors, assistants, and students.
</p>

<p>
The product must not be modeled as a generic anonymous chat application. It must be modeled as an academic tutor platform where every protected action happens inside an authenticated institutional and group-class context.
</p>

<p>
Use PostgreSQL as the database and Flyway as the source of truth for schema creation, constraints, indexes, and seed data. Hibernate must validate the schema; it must not create the schema for production-like execution.
</p>

The canonical identity chain is:

```text
account
  -> tenant_account
      -> group_class_member
```

The canonical academic chain is:

```text
tenant
  -> subject
  -> academic_period
  -> group_class
      -> group_class_member
```

The canonical tutor activity chain is:

```text
group_class_member
  -> conversation
      -> conversation_snapshot
```

The canonical grounding chain is:

```text
group_class
  -> grounding_collection
      -> grounding_document
          -> grounding_chunk
```

The canonical formative assessment chain is:

```text
group_class
  -> evaluation
      -> evaluation_assignment
```

Do not use `client_id`, browser cookies, legacy chat tables, legacy document ingestion tables, legacy evaluation-run tables, or old student-profile tables as the active source of identity, authorization, ownership, membership, or tutor activity.

---

## Purpose of This Specification

This file is the top-level always-readable specification for Socratic Tutor.

Use it to guide:

- future feature use cases,
- implementation prompts,
- schema migrations,
- architecture decisions,
- security implementation,
- UI planning,
- AI tutor behavior,
- verification and acceptance checks.

Individual use cases may define specific implementation stages, but they must not contradict this file. If a use case changes the project foundation, update this file and the related context documents in the same planning cycle.

Corrections caused by implementation drift or PR mistakes must be captured in a dedicated corrective use case, not mixed into this top-level specification.

---

## Core Application Requirements

Create a Spring Boot + Vaadin Flow application for Socratic tutoring in academic group classes.

The application must include:

- Login-first access.
- Application-managed authentication.
- Application-managed authorization.
- Persisted accounts.
- Persisted tenant membership through `tenant_account`.
- Persisted role assignment through `tenant_account_role`.
- Persisted resources, actions, permissions, and role-permission mappings.
- Role-based workspace routing.
- Tenant-level academic setup.
- Group-class-centered professor and student workflows.
- Socratic tutor chat through `conversation` and `conversation_snapshot`.
- Group-class grounding through `grounding_collection`, `grounding_document`, and `grounding_chunk`.
- Formative activities through `evaluation` and `evaluation_assignment`.
- Invitation-based access for tenant admins, professors, and students.
- Service-layer authorization and scope checks.
- Vaadin protected routes.
- Flyway-managed schema.
- PostgreSQL with pgvector support for grounding embeddings.
- AI tutor orchestration with guardrails, routing, grounding retrieval, and prompt composition.
- Clean package organization.
- Legacy isolation for obsolete persistence.

The application must not include or reintroduce:

- anonymous persisted tutor conversations as the target model,
- browser-cookie identity as academic identity,
- `chat`, `chat_transcript`, or `chat_message` as active target persistence,
- `conversation_message` unless a later ERD revision explicitly adds it,
- `evaluation_run` unless a later ERD revision explicitly adds it,
- old `student_profile` tables as active identity, authorization, membership, or context source,
- professor-owned tenants,
- hardcoded PUCMM, ICC-101, academic periods, group classes, professors, or students in the baseline.

---

## Product Vision

Socratic Tutor is a learning-first academic platform. Its goal is to help students reason, practice, and learn through guided dialogue instead of receiving direct answer dumps.

The system should feel:

- academically trustworthy,
- calm,
- rigorous,
- structured,
- transparent about context,
- safe across tenants and group classes,
- useful to both learners and educators.

The tutor must help students build reasoning skills by asking questions, giving hints, checking assumptions, and encouraging progressive problem solving.

The platform must help professors manage the learning context around that tutor: class membership, grounding material, and formative activities.

---

## Primary Actors

| Actor | Meaning |
|---|---|
| System Admin | Global platform operator who can create tenants and bootstrap institutional setup. |
| Tenant Admin | Institution-level operator who configures academic periods, subjects, group classes, and professor invitations inside one tenant. |
| Professor | Group-class educator who manages students, grounding material, conversations where permitted, and formative activities inside active group-class memberships. |
| Assistant | Reserved academic support role for future group-class support workflows. |
| Student | Learner who uses tutor chat and completes assigned formative activities inside invited group classes. |

Actors are not separate identity roots. Every authenticated person is an `account`.

---

## Role Model

Seed and support these roles:

```text
SYSTEM_ADMIN
TENANT_ADMIN
PROFESSOR
STUDENT
ASSISTANT
```

The conceptual hierarchy is:

```text
SYSTEM_ADMIN
  -> TENANT_ADMIN
      -> PROFESSOR
          -> STUDENT
```

This hierarchy expresses administrative breadth, not automatic inheritance of every lower-level operation. Permissions must still be granted explicitly through role-permission mappings and scoped through tenant and group-class rules.

### Role Responsibilities

| Role | Responsibility |
|---|---|
| `SYSTEM_ADMIN` | Global platform setup, tenant creation, tenant-admin invitation, platform-level visibility. |
| `TENANT_ADMIN` | Tenant academic setup: subjects, academic periods, group classes, professor invitations. |
| `PROFESSOR` | Group-class teaching operations: student management, grounding, formative activities, class-scoped tutor use. |
| `ASSISTANT` | Reserved support role. Must not receive broad capabilities until a future use case defines them. |
| `STUDENT` | Learner operations: own conversations, assigned formative activities, active group-class context. |

---

## Hierarchical Multi-Tenant Model

The system must use hierarchical academic multi-tenancy.

The hierarchy is:

```text
Platform
  -> Tenant / Institution
      -> Subject
      -> Academic Period
      -> Group Class
          -> Group Class Members
              -> Conversations
              -> Evaluation Assignments
          -> Grounding Collections
          -> Evaluations
```

### Tenant

A `tenant` represents a university, school, institution, or equivalent academic organization.

A tenant is not a professor workspace.

The tenant boundary answers:

```text
Which institution owns this academic structure?
```

### Tenant Account

A `tenant_account` connects an `account` to a `tenant`.

It answers:

```text
Which authenticated accounts are present inside this institution?
```

Roles are assigned to `tenant_account`, not directly to a global account.

### Group Class

A `group_class` is the operational workspace of the tutor.

It answers:

```text
Which concrete class section is this user operating inside?
```

Conversations, grounding, and formative activities are group-class scoped.

### Group Class Member

A `group_class_member` connects a tenant account to a group class with a class-level role such as:

```text
PROFESSOR
STUDENT
ASSISTANT
```

It answers:

```text
Who is this person inside this class section?
```

Most tutor activity is owned by or created through a `group_class_member`.

---

## Canonical Domain Vocabulary

| Term | Canonical meaning |
|---|---|
| `account` | Root authenticated identity for every signed-in person. |
| `tenant` | Institution/university boundary. |
| `tenant_account` | Account membership inside a tenant. |
| `tenant_account_role` | Role assignment inside a tenant. |
| `role` | Capability grouping such as `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, `STUDENT`, or `ASSISTANT`. |
| `resource` | User-facing or policy-relevant capability boundary. |
| `action` | Operation over a resource. |
| `permission` | Unique resource/action capability. |
| `subject` | Tenant-scoped academic subject/course. |
| `academic_period` | Tenant-scoped academic time period. |
| `group_class` | Concrete class section and operational tutor workspace. |
| `group_class_member` | Tenant account participating inside a group class. |
| `group_class_join_code` | Controlled access code for group-class joining where applicable. |
| `conversation` | Canonical tutor conversation root. Replaces old `chat`. |
| `conversation_snapshot` | Versioned/compacted conversation state and messages. Replaces transcripts/messages. |
| `grounding_collection` | Group-class collection of tutor grounding material. |
| `grounding_document` | Uploaded or text source document inside a grounding collection. |
| `grounding_chunk` | Chunked document content with optional vector embedding. |
| `evaluation` | Group-class formative activity definition. |
| `evaluation_assignment` | Student-facing assigned formative activity state. |

---

## Schema

The following ERD is the canonical baseline schema for the academic multi-tenant tutor model.

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

    GROUP_CLASS ||--o{ EVALUATION : has
    GROUP_CLASS_MEMBER ||--o{ EVALUATION : creates
    EVALUATION ||--o{ EVALUATION_ASSIGNMENT : assigns
    GROUP_CLASS_MEMBER ||--o{ EVALUATION_ASSIGNMENT : receives

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
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
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
        bigint role_id PK,FK
        uuid assigned_by_tenant_account_id FK
        timestamptz assigned_at
    }

    RESOURCE {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    ACTION {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        text code UK
        text name
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    PERMISSION {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
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
        text role "PROFESSOR | STUDENT | ASSISTANT"
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
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        uuid group_class_id FK
        uuid created_by_group_class_member_id FK
        text name
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_DOCUMENT {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        bigint collection_id FK
        text title
        text source_type "UPLOAD | TEXT"
        text storage_key
        text status "PROCESSING | READY | FAILED | INACTIVE"
        timestamptz created_at
        timestamptz updated_at
    }

    GROUNDING_CHUNK {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        bigint document_id FK
        int chunk_index
        text content
        vector embedding
        boolean active
        timestamptz created_at
    }

    EVALUATION {
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

    EVALUATION_ASSIGNMENT {
        uuid id PK
        uuid evaluation_id FK
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
        bigint current_snapshot_id FK
        text title
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    CONVERSATION_SNAPSHOT {
        bigint id PK "BIGINT GENERATED BY DEFAULT AS IDENTITY"
        uuid conversation_id FK
        bigint previous_snapshot_id FK
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

Use UUID identifiers for business/domain boundary records that may be referenced across academic or security boundaries:

```text
account.id
tenant.id
tenant_account.id
subject.id
academic_period.id
group_class.id
group_class_member.id
group_class_join_code.id
evaluation.id
evaluation_assignment.id
conversation.id
```

Use PostgreSQL identity `BIGINT` identifiers for internal catalog, snapshot, and implementation-detail records:

```sql
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
```

This applies to:

```text
role.id
resource.id
action.id
permission.id
grounding_collection.id
grounding_document.id
grounding_chunk.id
conversation_snapshot.id
```

Join tables without their own surrogate identity must use composite primary keys and foreign-key types that match the referenced table identifiers.

---

## PostgreSQL and Flyway Requirements

Use PostgreSQL.

Use Flyway from the start.

Hibernate must validate the schema instead of creating it.

Required application direction:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

The baseline migration must create the active target ERD and seed only foundation data.

### Required Baseline Migration

Recommended baseline file:

```text
src/main/resources/db/migration/V1__baseline.sql
```

The baseline must create:

```text
account
tenant
tenant_account
tenant_account_role
role
resource
action
permission
role_permission
subject
academic_period
group_class
group_class_member
group_class_join_code
conversation
conversation_snapshot
grounding_collection
grounding_document
grounding_chunk
evaluation
evaluation_assignment
```

The baseline must not create active legacy tables as part of the target model.

If old code remains in the repository, it must be excluded from active JPA startup.

### Seed Data

Seed only:

```text
admin@socratic-tutor.com
SYSTEM_ADMIN
TENANT_ADMIN
PROFESSOR
STUDENT
ASSISTANT
TENANT
SUBJECT
ACADEMIC_PERIOD
GROUP_CLASS
GROUP_CLASS_MEMBER
GROUP_CLASS_JOIN_CODE
GROUNDING
EVALUATION
EVALUATION_ASSIGNMENT
CONVERSATION
VIEW
CREATE
UPDATE
DELETE
INVITE
```

Do not seed:

```text
PUCMM
ICC-101
academic periods
group classes
tenant admins
professors
students
grounding documents
evaluations
evaluation assignments
conversations
```

Those records must be created by system-admin, tenant-admin, professor, and student workflows.

---

## Required Database Constraints

The database must enforce invariants that are safe to enforce at the database level.

Required constraints include:

- `account.email` unique.
- `account.username` unique.
- `tenant_account(tenant_id, account_id)` unique.
- `role.code` unique.
- `resource.code` unique.
- `action.code` unique.
- `permission.code` unique.
- `permission(resource_id, action_id)` unique.
- `role_permission(role_id, permission_id)` primary key.
- `tenant_account_role(tenant_account_id, role_id)` primary key.
- `subject(tenant_id, code)` unique.
- `academic_period(tenant_id, code)` unique.
- `group_class(tenant_id, code)` unique.
- `group_class_member(group_class_id, tenant_account_id, role)` unique.
- `group_class_join_code.code` unique.
- `grounding_chunk(document_id, chunk_index)` unique.
- `evaluation_assignment(evaluation_id, group_class_member_id)` unique.
- `conversation_snapshot(conversation_id, snapshot_no)` unique.

Allowed values must be constrained where appropriate:

```text
group_class_member.role = PROFESSOR | STUDENT | ASSISTANT
grounding_document.source_type = UPLOAD | TEXT
grounding_document.status = PROCESSING | READY | FAILED | INACTIVE
evaluation.status = DRAFT | PUBLISHED | CLOSED | ARCHIVED
evaluation_assignment.status = ASSIGNED | STARTED | SUBMITTED | SKIPPED | EXPIRED | EXCUSED
```

---

## Authorization Model

Use normalized RBAC.

A permission is an allowed operation expressed as:

```text
RESOURCE:ACTION
```

Do not store permissions as JSON arrays inside roles.

The persisted authorization chain is:

```text
tenant_account
  -> tenant_account_role
      -> role
          -> role_permission
              -> permission
                  -> resource
                  -> action
```

Authorization must also include scope checks:

```text
permission check
  -> tenant boundary check
      -> group-class membership check
          -> ownership check where required
```

A permission alone is never enough to access private or scoped data.

### Seeded Resources

Seed these resources:

| Resource | Meaning |
|---|---|
| `TENANT` | Institution administration. |
| `SUBJECT` | Tenant-scoped subject management. |
| `ACADEMIC_PERIOD` | Tenant-scoped academic period management. |
| `GROUP_CLASS` | Concrete class-section management. |
| `GROUP_CLASS_MEMBER` | Membership and invitation operations. |
| `GROUP_CLASS_JOIN_CODE` | Student access-code generation/control. |
| `GROUNDING` | Group-class grounding configuration. |
| `EVALUATION` | Group-class formative activity definition. |
| `EVALUATION_ASSIGNMENT` | Student-facing assigned activity state. |
| `CONVERSATION` | Tutor conversations owned by group-class members. |

Do not seed implementation tables as standalone authorization resources unless a later use case requires it.

Excluded as standalone resources in the baseline:

```text
TENANT_ACCOUNT
ROLE_PERMISSION
PERMISSION
RESOURCE
ACTION
CONVERSATION_SNAPSHOT
GROUNDING_COLLECTION
GROUNDING_DOCUMENT
GROUNDING_CHUNK
```

Access to those tables is controlled through higher-level resources.

### Seeded Actions

Seed these actions:

| Action | Meaning |
|---|---|
| `VIEW` | Read, list, or open a resource. |
| `CREATE` | Create a new resource instance. |
| `UPDATE` | Modify an existing resource or transition state. |
| `DELETE` | Remove, archive, disable, or logically delete a resource. |
| `INVITE` | Invite or assign another person into a tenant or group-class context. |

### Baseline Permission Direction

`SYSTEM_ADMIN`:

```text
TENANT:VIEW
TENANT:CREATE
TENANT:UPDATE
SUBJECT:VIEW
ACADEMIC_PERIOD:VIEW
GROUP_CLASS:VIEW
GROUP_CLASS_MEMBER:VIEW
GROUP_CLASS_JOIN_CODE:VIEW
GROUNDING:VIEW
EVALUATION:VIEW
EVALUATION_ASSIGNMENT:VIEW
CONVERSATION:VIEW
```

`TENANT_ADMIN`:

```text
SUBJECT:VIEW
SUBJECT:CREATE
SUBJECT:UPDATE
SUBJECT:DELETE
ACADEMIC_PERIOD:VIEW
ACADEMIC_PERIOD:CREATE
ACADEMIC_PERIOD:UPDATE
ACADEMIC_PERIOD:DELETE
GROUP_CLASS:VIEW
GROUP_CLASS:CREATE
GROUP_CLASS:UPDATE
GROUP_CLASS:DELETE
GROUP_CLASS_MEMBER:VIEW
GROUP_CLASS_MEMBER:INVITE
GROUP_CLASS_MEMBER:UPDATE
GROUP_CLASS_MEMBER:DELETE
```

`PROFESSOR`:

```text
GROUP_CLASS:VIEW
GROUP_CLASS:UPDATE
GROUP_CLASS_MEMBER:VIEW
GROUP_CLASS_MEMBER:INVITE
GROUP_CLASS_MEMBER:UPDATE
GROUP_CLASS_MEMBER:DELETE
GROUP_CLASS_JOIN_CODE:VIEW
GROUP_CLASS_JOIN_CODE:CREATE
GROUP_CLASS_JOIN_CODE:UPDATE
GROUP_CLASS_JOIN_CODE:DELETE
GROUNDING:VIEW
GROUNDING:CREATE
GROUNDING:UPDATE
GROUNDING:DELETE
EVALUATION:VIEW
EVALUATION:CREATE
EVALUATION:UPDATE
EVALUATION:DELETE
EVALUATION_ASSIGNMENT:VIEW
EVALUATION_ASSIGNMENT:CREATE
EVALUATION_ASSIGNMENT:UPDATE
EVALUATION_ASSIGNMENT:DELETE
CONVERSATION:VIEW
```

`STUDENT`:

```text
GROUP_CLASS:VIEW
CONVERSATION:VIEW
CONVERSATION:CREATE
CONVERSATION:UPDATE
CONVERSATION:DELETE
EVALUATION:VIEW
EVALUATION_ASSIGNMENT:VIEW
EVALUATION_ASSIGNMENT:UPDATE
```

`ASSISTANT`:

```text
Reserved. Do not grant broad capabilities until a dedicated use case defines the assistant behavior.
```

---

## Authentication and Spring Security Requirements

Use Spring Security with Vaadin security integration.

Public routes should be minimal.

Protected routes must require authentication.

Service-layer authorization remains mandatory even when UI routes are protected.

### Security Configuration Shape

Use a security configuration shaped like this:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain vaadinSecurityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers(
                "/styles/**",
                "/fonts/**",
                "/frontend/**",
                "/images/**",
                "/icons/**",
                "/line-awesome/**",
                "/VAADIN/**"
            ).permitAll()
            .requestMatchers(
                "/login",
                "/invitations/accept",
                "/onboarding/**"
            ).permitAll()
        );

        http.with(vaadin(), vaadinSecurity -> {
            vaadinSecurity.loginView(LoginView.class);
        });

        return http.build();
    }
}
```

The exact code may vary by Spring Boot and Vaadin version, but the intention must remain the same.

### Security Configuration Rationale

| Configuration area | Required behavior | Reason |
|---|---|---|
| Static asset matchers | Permit Vaadin/frontend assets without authentication. | Login and public pages must render correctly before authentication. |
| `/login` | Public route. | Unauthenticated users need a place to authenticate. |
| Invitation acceptance route | Public route with token validation. | Invited users may not have an authenticated session yet. |
| Onboarding routes | Temporarily public or guarded by onboarding context. | Invitation flow may span token validation, registration, and login. |
| Workspace routes | Authenticated only. | Academic data must never be shown to anonymous users. |
| Vaadin login view | Configure the actual login view. | Vaadin route protection should redirect unauthenticated users consistently. |
| Custom user details service | Load `account` and security state from the database. | The app database is the security source of truth. |
| Password encoder | Store password hashes only. | Raw passwords must never be persisted. |
| Authentication success routing | Resolve account context after login. | Users must land in the correct workspace based on roles and memberships. |
| Service-layer permission checker | Verify `RESOURCE:ACTION`. | UI route protection is not enough. |
| Scope authorizer | Verify tenant, group-class, and ownership boundaries. | Permissions alone do not determine data visibility. |
| Legacy exclusion | Prevent legacy repositories from active security flow. | Old `client_id` logic must not become an authorization bypass. |

### Login Requirements

The `LoginView` must:

- use Vaadin login components or a custom responsive layout,
- route unauthenticated users to login,
- display invalid-login feedback,
- avoid revealing whether a username or email exists,
- redirect authenticated users through workspace routing,
- not create anonymous academic identity.

### Registration Requirements

Open public self-signup is not allowed in the target model.

Registration must be invitation-based.

The invited registration flow must:

- validate a pending invitation,
- keep invited email read-only,
- require first name, last name, password, and confirm password,
- store only password hashes,
- create or reuse the correct `account`,
- create or reuse `tenant_account`,
- assign the correct role where needed,
- create `group_class_member` for professor/student invitations where needed,
- clear temporary onboarding context after completion.

---

## Workspace Routing Requirements

After authentication, the system must resolve:

```text
account
tenant_account records
tenant_account_role records
group_class_member records
last_tenant_account_id
last_group_class_member_id
```

Routing priority:

```text
SYSTEM_ADMIN -> system admin workspace
TENANT_ADMIN -> tenant admin workspace
PROFESSOR -> professor workspace
STUDENT -> student workspace
NO ROLE / NO MEMBERSHIP -> no-access state
```

If a user has multiple contexts, the system must choose a deterministic default and provide context switching where appropriate.

---

## UI Requirements

Use Vaadin Flow for all screens.

The UI must reflect role and context.

The UI must never be the only enforcement layer.

### Common UI Requirements

- Use protected routes for workspaces.
- Show only actions relevant to the current role/context.
- Provide safe empty states when the user has no tenant or group-class context.
- Do not create conversations, grounding documents, or formative activity records if no valid context exists.
- Use local UI state only for UI concerns.
- Use service-layer methods for all persisted actions.

### Local UI State

Local UI state may control:

- selected tenant,
- selected group class,
- active navigation item,
- sidebar visibility,
- selected row,
- filter text,
- form dirty state,
- confirmation dialog state.

Local UI state must not control:

- authentication,
- authorization,
- persisted membership,
- permissions,
- conversation ownership,
- assignment ownership,
- tenant or group-class access.

### System Admin Workspace

The system admin workspace must allow:

- viewing platform setup state,
- creating tenants,
- inviting tenant admins,
- seeing invitation status where supported,
- accessing platform-level administrative views.

The system admin must not be modeled as a normal professor or student.

### Tenant Admin Workspace

The tenant admin workspace must be tenant-centered.

It should include:

- tenant context selector,
- dashboard,
- academic periods,
- subjects,
- group classes,
- invitations/professors.

Tenant admins can operate only inside tenants where they have the correct role.

### Professor Workspace

The professor workspace must be group-class-centered.

It should include:

- group-class context selector,
- home/dashboard,
- new tutor conversation,
- formative activities,
- grounding/document ingestion,
- students.

Professors can operate only inside group classes where they are active professor members.

### Student Workspace

The student workspace must be learning-centered.

It should include:

- active group-class context,
- new tutor conversation,
- conversation history,
- assigned formative activities.

Students must not manage tenants, subjects, periods, group classes, professors, or other students.

---

## Conversation Requirements

The active conversation model is:

```text
conversation
conversation_snapshot
```

The old active model is obsolete:

```text
chat
chat_transcript
chat_message
```

### Conversation Rules

- A conversation belongs to exactly one `group_class_member`.
- A conversation has zero or one current snapshot.
- A conversation can have many snapshots.
- A current snapshot is referenced by `conversation.current_snapshot_id`.
- Snapshot history is linked by `conversation_snapshot.previous_snapshot_id`.
- Messages are stored in `conversation_snapshot.messages`.
- Compacted context is stored in `conversation_snapshot.carry_context`.
- Token count is stored in `conversation_snapshot.token_count`.
- Message count is stored in `conversation_snapshot.message_count`.

### Expected Relationship Shape

The target relationship shape is conceptually:

```text
GroupClassMember 1 -> * Conversation
Conversation 1 -> * ConversationSnapshot
Conversation 1 -> 1 currentSnapshot
ConversationSnapshot 0..1 -> previous ConversationSnapshot
```

A conversation's current snapshot is not the same as the collection of all snapshots.

### Conversation Behavior

The system must:

- create conversations only for a valid group-class member,
- list conversations only within allowed context,
- prevent students from reading other students' private conversations unless a future use case allows it,
- persist new conversation state through snapshots,
- avoid falling back to browser `client_id`,
- fail safely if no active group-class context exists.

---

## Grounding Requirements

Grounding is the mechanism that lets the tutor use class-specific learning material.

The active grounding model is:

```text
grounding_collection
grounding_document
grounding_chunk
```

The old active model is obsolete:

```text
ingested_document
document_ingestion_job
document_segment
vector_store
```

### Grounding Rules

- A grounding collection belongs to a group class.
- A grounding collection is created by a group-class member.
- A grounding document belongs to a grounding collection.
- A grounding chunk belongs to a grounding document.
- A grounding chunk may store an embedding in `grounding_chunk.embedding`.
- Retrieval must be scoped to the active group class.
- Students must not manage grounding documents in the baseline.
- Professors can manage grounding only inside group classes where they are active professor members.

### Grounding Document Status

Allowed statuses:

```text
PROCESSING
READY
FAILED
INACTIVE
```

The tutor may only retrieve from documents/chunks that are usable according to the current grounding service rules.

---

## Formative Activity Requirements

The schema names this area:

```text
evaluation
evaluation_assignment
```

The user-facing product concept may be presented as formative activities.

### Evaluation Definition

An `evaluation` represents a group-class formative activity definition.

Rules:

- It belongs to a group class.
- It is created by a valid group-class member.
- It has a lifecycle status.
- It may open and close by date/time.
- It is managed by professors or authorized roles inside the group class.

Allowed statuses:

```text
DRAFT
PUBLISHED
CLOSED
ARCHIVED
```

### Evaluation Assignment

An `evaluation_assignment` represents a student's assigned activity state.

Rules:

- It belongs to an evaluation.
- It targets a group-class member.
- It must target a student group-class member.
- Students can view and update only their own assignments.
- Professors can create/manage assignments only inside allowed group classes.

Allowed statuses:

```text
ASSIGNED
STARTED
SUBMITTED
SKIPPED
EXPIRED
EXCUSED
```

Expected basic student lifecycle:

```text
ASSIGNED -> STARTED -> SUBMITTED
```

`evaluation_run` is not part of the active target model.

---

## AI Tutor Requirements

The AI tutor must support Socratic learning behavior.

The tutor should:

- ask guiding questions,
- avoid unnecessary direct answer dumping,
- give hints progressively,
- ask students to explain their reasoning,
- correct misconceptions gently,
- use examples appropriate to the class context,
- remain in academic scope,
- use grounding material when available and authorized,
- clearly avoid exposing other students' data,
- keep safety and guardrails active.

### AI Configuration Requirements

AI configuration belongs to application configuration and AI orchestration packages.

It should compose:

- ChatClient or equivalent chat model client,
- message memory or conversation context adapter,
- tutor guard advisors,
- pedagogical routing advisors,
- grounding/retrieval tools,
- document catalog/context advisors when backed by target grounding data,
- logging or observability advisors where appropriate.

AI configuration must not:

- decide authorization,
- bypass tenant/group-class checks,
- query legacy persistence as active source of truth,
- hardcode obsolete prompt vocabulary,
- become a replacement for domain services.

AI services may only consume authorized domain data supplied by service-layer components.

---

## Spring Configuration Requirements

Use application configuration with environment-variable overrides.

Required configuration areas:

```yaml
spring:
  application:
    name: socratic-tutor

  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:${POSTGRES_PORT:4321}/socratic-tutor}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:postgres}

  flyway:
    enabled: true
    locations: classpath:db/migration

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

AI/model configuration must remain explicit and centralized.

PostgreSQL/pgvector configuration must remain available when grounding embeddings depend on vector retrieval.

Security configuration must remain centralized in the Spring Security/Vaadin security area.

Do not introduce disconnected configuration objects that duplicate old behavior without a clear active purpose.

---

## Package Organization

Use a clean package organization that separates active code from legacy code.

Recommended structure:

```text
com.wornux
├── Application.java
├── config
├── security
├── data
│   ├── entities
│   │   ├── account
│   │   ├── tenant
│   │   ├── authorization
│   │   ├── academic
│   │   ├── conversation
│   │   ├── grounding
│   │   └── evaluation
│   └── repositories
├── services
│   ├── account
│   ├── tenant
│   ├── authorization
│   ├── academic
│   ├── conversation
│   ├── grounding
│   └── evaluation
├── ai
├── ui
│   ├── auth
│   ├── onboarding
│   ├── admin
│   ├── tenant
│   ├── professor
│   ├── student
│   ├── chat
│   ├── grounding
│   └── evaluation
├── infrastructure
└── legacy
    ├── chat
    ├── student_profile
    ├── document_ingestion
    └── evaluation_run
```

Active packages must not depend on `com.wornux.legacy`.

Legacy packages may remain for reference or later migration work, but they must not participate in active JPA repository scanning, component scanning, authorization, or AI prompt context.

---

## Business Rules

### Identity and Membership

- Every authenticated person is an `account`.
- Account email must be unique.
- Account username must be unique.
- `tenant_account` connects an account to a tenant.
- An account cannot be added to the same tenant twice.
- Roles are assigned to `tenant_account`, not directly to global accounts.
- A user cannot receive the same tenant-account role twice.
- A group-class member must reference a valid group class and tenant account.
- A tenant account cannot receive duplicate group-class membership for the same role in the same group class.
- Locked accounts, tenant accounts, or group-class members must not receive normal active access.

### Tenant and Academic Structure

- A tenant represents an institution.
- A subject belongs to one tenant.
- An academic period belongs to one tenant.
- A group class belongs to one tenant, one subject, and one academic period.
- Subject code must be unique inside a tenant.
- Academic period code must be unique inside a tenant.
- Group-class code must be unique inside a tenant.
- Group classes must not be created without a valid subject and academic period.

### Authorization

- Roles must have stable unique codes.
- Resources must have stable unique codes.
- Actions must have stable unique codes.
- A permission must be a unique resource/action pair.
- A role cannot receive the same permission twice.
- Authorization resources must represent capability boundaries, not every database table.
- UI hiding is not authorization.
- Service-layer checks are required for protected operations.
- Tenant, group-class, and ownership checks must be applied after permission checks.

### Conversation

- A conversation must belong to one group-class member.
- A conversation snapshot must belong to one conversation.
- Conversation messages must be stored in `conversation_snapshot.messages`.
- Conversation carry context must be stored in `conversation_snapshot.carry_context`.
- A student can access only their own conversations unless a later use case explicitly expands access.
- Anonymous conversations are not part of the target model.
- `conversation_message` must not be added unless a later ERD revision explicitly adds it.

### Grounding

- A grounding collection must belong to a group class.
- A grounding document must belong to a grounding collection.
- A grounding chunk must belong to a grounding document.
- Embeddings belong on `grounding_chunk.embedding`.
- Grounding retrieval must be group-class scoped.
- Students do not manage grounding in the baseline.

### Evaluation / Formative Activities

- An evaluation must belong to a group class.
- An evaluation must be created by a valid authorized group-class member.
- An evaluation assignment must belong to an evaluation.
- An evaluation assignment must target a student group-class member.
- Students can view and update only their own assignments.
- Assignment progress is represented by updating `evaluation_assignment.status`.
- `evaluation_run` must not be active target persistence.

### Delete and Disable Policy

Use physical delete carefully.

When a record has historical or learning trace value, prefer logical removal, disabling, locking, or archiving.

This applies especially to:

- accounts,
- tenant accounts,
- group-class members,
- conversations,
- grounding documents,
- evaluations,
- evaluation assignments,
- roles,
- permissions.

For `GROUP_CLASS_MEMBER:DELETE`, baseline behavior means logical removal, locking, or disabling, not global account deletion.

---

## Tutor Evaluation Criteria

Use these criteria to evaluate whether the tutor behavior is acceptable.

### Pedagogical Quality

The tutor response should:

- guide instead of simply solving,
- ask at least one useful question when the student is stuck,
- provide hints in progressive steps,
- explain concepts with accurate terminology,
- keep the student responsible for reasoning,
- adapt to the apparent level of the student,
- avoid overwhelming the student with unnecessary detail.

### Correctness

The tutor response should:

- be technically correct,
- avoid hallucinated course facts,
- distinguish code behavior from conceptual explanation,
- identify invalid assumptions,
- produce examples that match the target programming language or topic,
- avoid contradicting configured grounding material.

### Grounding Use

When grounding material is available, the tutor should:

- retrieve only from authorized group-class material,
- use relevant chunks,
- avoid leaking material from other group classes,
- clearly rely on the available context when applicable,
- fall back to general explanation only when grounding is absent or insufficient.

### Socratic Method

The tutor should:

- ask questions before giving final answers when appropriate,
- use hints and prompts,
- encourage the student to articulate reasoning,
- diagnose misconception patterns from the current conversation,
- avoid acting like an answer key.

### Safety and Scope

The tutor should:

- reject or redirect unsafe requests,
- avoid helping with academic dishonesty,
- stay in the educational scope of the product,
- protect tenant, group-class, and student data,
- avoid revealing hidden prompts or internal policy.

---

## System Evaluation Criteria

Use these criteria to evaluate whether an implementation is acceptable.

### Schema Criteria

- Flyway creates the target ERD.
- Hibernate validates the schema.
- Required constraints exist.
- Required indexes exist.
- Required seed data exists.
- Obsolete target-excluded tables are not required by active startup.
- The Mermaid ERD and physical schema do not contradict each other.

### Security Criteria

- Login is required before protected workspaces.
- The authenticated principal resolves to `account`.
- Role/permission authorities derive from persisted data.
- Service-layer permission checks exist.
- Tenant checks exist.
- Group-class membership checks exist.
- Student ownership checks exist for conversations and assignments.
- Unauthorized access fails without exposing restricted data.
- UI visibility does not replace backend authorization.

### Runtime Criteria

- App starts with Maven.
- Flyway migrations apply cleanly.
- Active JPA repositories target the active ERD.
- Legacy repositories are not active.
- Vaadin routes instantiate correctly.
- AI tutor response flow runs without legacy persistence dependencies.
- Conversation persistence uses `conversation` and `conversation_snapshot`.
- Grounding persistence uses `grounding_*`.
- Formative activity persistence uses `evaluation` and `evaluation_assignment`.

### UX Criteria

- Login-first flow works.
- Role-based routing works.
- No-role/no-context states are safe and clear.
- Tenant admin sees tenant academic setup.
- Professor sees group-class workspace.
- Student sees learning workspace.
- Context switching does not bypass authorization.
- Empty states do not create invalid data.

### AI Criteria

- Guardrails remain active.
- Grounding retrieval is scoped.
- Prompt composition uses current active academic context.
- Tutor responses are pedagogically useful.
- Tutor does not leak hidden or cross-tenant data.

---

## Verification Requirements

Run Maven verification after implementation work.

Required command:

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

Verification output should report:

```text
status
executive_summary
changed_files
implementation_notes
tests_run
test_results
final_mvn_result
risks_or_followups
```

Required verification coverage:

- schema creation,
- seed data,
- security configuration,
- login routing,
- role context resolution,
- permission checks,
- tenant scope checks,
- group-class scope checks,
- ownership checks,
- conversation snapshot persistence,
- grounding persistence,
- evaluation assignment persistence,
- legacy exclusion,
- Vaadin route startup,
- AI advisor startup.

---

## Use Case Relationship

This specification is the stable project baseline.

Use cases should be treated as implementation slices of this specification.

Current foundation:

```text
UC-001: schema and academic multi-tenant ERD
UC-002: active runtime adaptation to the target ERD
UC-003: role-based onboarding and workspace setup
```

Future use cases must remain atomic enough to implement and verify without overwhelming the project context.

---

## Non-Goals

Do not implement or imply these unless a future approved use case adds them:

- Keycloak-managed authorization as the tutor authority source.
- Anonymous browser identity as target persistence.
- Professor-owned tenants.
- Open public self-signup.
- Hardcoded PUCMM, ICC-101, academic periods, group classes, professors, or students.
- Global professor access across all tenants.
- `conversation_message`.
- `evaluation_run`.
- Active old student profile/misconception tracking.
- Schools, departments, pensums, or deeper institutional hierarchy.
- Payment or subscription management.
- Institution-wide SIS integration.
- Production-only infrastructure assumptions in the baseline spec.

---

## Final Expected Result

<p>
Generate and maintain a complete Spring Boot + Vaadin Flow Socratic Tutor application based on this specification. The system must start locally, connect to PostgreSQL, run Flyway migrations, validate the academic multi-tenant schema, authenticate users, route them to role-appropriate workspaces, and enforce application-managed authorization through persisted roles, permissions, tenant scope, group-class membership, and ownership rules.
</p>

<p>
The final application must preserve Socratic learning behavior while operating on the target model centered on `account`, `tenant_account`, `group_class_member`, `conversation`, `conversation_snapshot`, `grounding`, `evaluation`, and `evaluation_assignment`.
</p>

<p>
The final application must not reactivate obsolete `client_id`, old chat, old document ingestion, old evaluation-run, or old student-profile persistence as active runtime.
</p>
