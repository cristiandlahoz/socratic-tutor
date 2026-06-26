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
  -> conversation (domain ownership and metadata)
      -> Spring AI Session (same id)
          -> session events
```

The canonical grounding chain is:

```text
group_class
  -> grounding_vector_store
```

The canonical formative activity chain is:

```text
group_class
  -> training_activity
      -> training_activity_assignment
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

Use cases must not contradict this file. If a use case affects the project foundation, update this file and the related context documents in the same change.

Corrections caused by implementation drift or PR mistakes must update the canonical specification instead of creating a parallel historical specification.

---

## Core Application Requirements

Create a Spring Boot + Vaadin Flow application for Socratic tutoring in academic group classes.

The application must include:

- Login-first access.
- Application-managed authentication.
- Application-managed authorization.
- Persisted accounts.
- Persisted tenant membership through `tenant_account`.
- Persisted global role assignment through `account_role`.
- Persisted tenant role assignment through `tenant_account_role`.
- Persisted resources, actions, permissions, and role-permission mappings.
- Role-based workspace routing.
- Tenant-level academic setup.
- Group-class-centered professor and student workflows.
- Socratic tutor chat through domain `conversation` records and Spring AI Session JDBC history events.
- Group-class grounding through pgvector-backed `grounding_vector_store` rows scoped by metadata.
- Formative activities through `training_activity` and `training_activity_assignment`.
- Invitation-based access for tenant admins, professors, and students.
- Service-layer authorization and scope checks.
- Vaadin protected routes.
- Flyway-managed schema.
- PostgreSQL with Spring AI PgVectorStore support for grounding embeddings.
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
| `ASSISTANT` | Reserved. Not a group_class_member role in the baseline. |
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

Tenant-scoped roles are assigned to `tenant_account`. Global platform roles are assigned directly to `account` through `account_role`.

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
| `account_role` | Global platform role assignment for an account. |
| `tenant_account_role` | Role assignment inside a tenant. |
| `role` | Capability grouping such as `SYSTEM_ADMIN`, `TENANT_ADMIN`, `PROFESSOR`, or `STUDENT`. |
| `resource` | User-facing or policy-relevant capability boundary. |
| `action` | Operation over a resource. |
| `permission` | Unique resource/action capability. |
| `subject` | Tenant-scoped academic subject/course. |
| `academic_period` | Tenant-scoped academic time period. |
| `group_class` | Concrete class section and operational tutor workspace. |
| `group_class_member` | Tenant account participating inside a group class. |
| `group_class_join_code` | Controlled access code for group-class joining where applicable. |
| `conversation` | Canonical tutor conversation root. Replaces old `chat`. |
| `ai_session` / `ai_session_event` | Spring AI Session-owned lifecycle metadata, message history, synthetic summaries, and compaction archive. |
| `grounding_vector_store` | Flat pgvector-backed retrieval row for class-scoped grounding material. |
| `training_activity` | Group-class formative activity definition. |
| `training_activity_assignment` | Student-facing assigned formative activity state. |

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
    locations: classpath:db/migration/prod

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

The baseline migration must create the active target ERD and seed only foundation data.

### Required Baseline Migration

Recommended baseline file:

```text
src/main/resources/db/migration/prod/V1__baseline.sql
```

The baseline must create:

```text
account
tenant
tenant_account
account_role
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
ai_session
ai_session_event
grounding_vector_store
training_activity
training_activity_assignment
```

The baseline must not create active legacy tables as part of the target model.

If old code remains in the repository, it must be excluded from active JPA startup.

### Seed Data

Seed only:

```text
admin@wornux.com
SYSTEM_ADMIN
TENANT_ADMIN
PROFESSOR
STUDENT
TENANT
SUBJECT
ACADEMIC_PERIOD
GROUP_CLASS
GROUP_CLASS_MEMBER
GROUP_CLASS_JOIN_CODE
GROUNDING
TRAINING_ACTIVITY
TRAINING_ACTIVITY_ASSIGNMENT
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
grounding rows
training activities
training activity assignments
conversations
```

Those records must be created by system-admin, tenant-admin, professor, and student workflows.

---

## Required Database Constraints

The database must enforce invariants that are safe to enforce at the database level.

Required constraints include:

- `account.email` unique.
- `tenant_account(tenant_id, account_id)` unique.
- `role.code` unique.
- `resource.code` unique.
- `action.code` unique.
- `permission.code` unique.
- `permission(resource_id, action_id)` unique.
- `role_permission(role_id, permission_id)` primary key.
- `account_role(account_id, role_id)` primary key.
- `tenant_account_role(tenant_account_id, role_id)` primary key.
- `subject(tenant_id, code)` unique.
- `academic_period(tenant_id, code)` unique.
- `group_class(tenant_id, code)` unique.
- `group_class_member(group_class_id, tenant_account_id, role)` unique.
- `group_class_join_code.code` unique.
- `training_activity_assignment(training_activity_id, group_class_member_id)` unique.

Spring AI Session table constraints and indexes must match its official JDBC PostgreSQL schema rather than an application approximation.

Allowed values must be constrained where appropriate:

```text
group_class_member.role = PROFESSOR | STUDENT
training_activity.status = DRAFT | PUBLISHED | CLOSED | ARCHIVED
training_activity_assignment.status = ASSIGNED | STARTED | SUBMITTED | SKIPPED | EXPIRED | EXCUSED
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
account
  -> account_role
      -> role
          -> role_permission
              -> permission
                  -> resource
                  -> action

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
| `TRAINING_ACTIVITY` | Group-class formative activity definition. |
| `TRAINING_ACTIVITY_ASSIGNMENT` | Student-facing assigned activity state. |
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
GROUNDING_VECTOR_STORE
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
TRAINING_ACTIVITY:VIEW
TRAINING_ACTIVITY_ASSIGNMENT:VIEW
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
TRAINING_ACTIVITY:VIEW
TRAINING_ACTIVITY:CREATE
TRAINING_ACTIVITY:UPDATE
TRAINING_ACTIVITY:DELETE
TRAINING_ACTIVITY_ASSIGNMENT:VIEW
TRAINING_ACTIVITY_ASSIGNMENT:CREATE
TRAINING_ACTIVITY_ASSIGNMENT:UPDATE
TRAINING_ACTIVITY_ASSIGNMENT:DELETE
CONVERSATION:VIEW
```

`STUDENT`:

```text
GROUP_CLASS:VIEW
CONVERSATION:VIEW
CONVERSATION:CREATE
CONVERSATION:UPDATE
CONVERSATION:DELETE
TRAINING_ACTIVITY:VIEW
TRAINING_ACTIVITY_ASSIGNMENT:VIEW
TRAINING_ACTIVITY_ASSIGNMENT:UPDATE
```

`ASSISTANT`:

```text
Reserved. Not a group_class_member role in the baseline.
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
- avoid revealing whether an email exists,
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
account_role records
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
- Do not create conversations, grounding rows, or formative activity records if no valid context exists.
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
conversation (domain ownership, title, listing, access control, metadata)
ai_session (Spring AI Session lifecycle metadata)
ai_session_event (Spring AI Session event log and compaction archive)
```

The old active model is obsolete:

```text
chat
chat_transcript
chat_message
```

### Conversation Rules

- A conversation belongs to exactly one `group_class_member`.
- A conversation owns the title, listing order, access rules, and domain metadata.
- `conversation.id` is passed as the Spring AI Session id.
- `group_class_member.id` is passed as the Spring AI Session user id on every model request.
- Spring AI Session owns user, assistant, tool, synthetic summary, and archived events.
- `SessionMemoryAdvisor` is the only normal user/assistant persistence path.
- Spring AI Session recursive summarization owns context compaction; application services must not implement a parallel compaction flow.
- `conversation.last_prompt_tokens` is nullable and updated only from actual provider metadata.

### Expected Relationship Shape

The target relationship shape is conceptually:

```text
GroupClassMember 1 -> * Conversation
Conversation 1 -> 0..1 SpringAiSession
SpringAiSession 1 -> * SessionEvent
```

The conversation-to-Session mapping uses the same identifier value without a database foreign key because the domain UUID and library string identifier types remain separate.

### Conversation Behavior

The system must:

- create conversations only for a valid group-class member,
- list conversations only within allowed context,
- prevent students from reading other students' private conversations unless a future use case allows it,
- verify domain ownership before reading or mutating Session data,
- load display history from the full Session event log, including archived real events,
- exclude synthetic, tool, branched, blank, and tool-call assistant events from normal user-visible history,
- avoid falling back to browser `client_id`,
- fail safely if no active group-class context exists.

---

## Grounding Requirements

Grounding is the mechanism that lets the tutor use class-specific learning material.

The active grounding model is:

```text
grounding_vector_store
```

The old active model is obsolete:

```text
ingested_document
document_ingestion_job
document_segment
vector_store
```

### Grounding Rules

- A grounding vector-store row is scoped to one group class through metadata.
- A grounding vector-store row is written by a group-class member.
- A grounding vector-store row stores the indexed content payload.
- A grounding vector-store row stores embeddings in `grounding_vector_store.embedding`.
- Retrieval must be scoped to the active group class and approved row status.
- Students must not manage grounding rows in the baseline.
- Professors can manage grounding only inside group classes where they are active professor members.

### Grounding Row Status

Allowed statuses:

```text
PROCESSING
READY
FAILED
INACTIVE
```

The tutor may only retrieve from rows that are usable according to the current grounding service rules.

---

## Formative Activity Requirements

The schema names this area:

```text
training_activity
training_activity_assignment
```

The user-facing product concept may be presented as formative activities.

### Training Activity Definition

A `training_activity` represents a group-class formative activity definition.

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

### Training Activity Assignment

A `training_activity_assignment` represents a student's assigned activity state.

Rules:

- It belongs to a training_activity.
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
- Spring AI Session's advisor-based memory and compaction flow,
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
    locations: classpath:db/migration/prod

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

AI/model configuration must remain explicit and centralized. The dev profile may add `classpath:db/migration/dev` after the prod location for local-only seed data.

Spring AI PgVectorStore configuration must remain available when grounding embeddings depend on vector retrieval.

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
│   │   └── training_activity
│   └── repositories
├── services
│   ├── account
│   ├── tenant
│   ├── authorization
│   ├── academic
│   ├── conversation
│   ├── grounding
│   └── training_activity
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
│   └── training_activity
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
- Account email must be unique and is the only supported login identifier.
- `tenant_account` connects an account to a tenant.
- An account cannot be added to the same tenant twice.
- Global platform roles are assigned through `account_role`; tenant roles are assigned through `tenant_account_role`.
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
- Conversation messages and compaction state must be stored as Spring AI Session events.
- The domain conversation and Session must share the conversation id value.
- Domain ownership must be checked before Session history access.
- A student can access only their own conversations unless a later use case explicitly expands access.
- Anonymous conversations are not part of the target model.
- `conversation_message` must not be added unless a later ERD revision explicitly adds it.

### Grounding

- A grounding collection must belong to a group class.
- A grounding document must belong to a grounding collection.
- A grounding chunk must belong to a grounding document.
- Embeddings belong on `grounding_vector_store.embedding`.
- Grounding retrieval must be group-class scoped.
- Students do not manage grounding in the baseline.

### Training Activity / Formative Activities

- A training_activity must belong to a group class.
- A training_activity must be created by a valid authorized group-class member.
- A training_activity_assignment must belong to a training_activity.
- A training_activity_assignment must target a student group-class member.
- Students can view and update only their own assignments.
- Assignment progress is represented by updating `training_activity_assignment.status`.
- `evaluation_run` must not be active target persistence.

### Delete and Disable Policy

Use physical delete carefully.

When a record has historical or learning trace value, prefer logical removal, disabling, locking, or archiving.

This applies especially to:

- accounts,
- tenant accounts,
- group-class members,
- conversations,
- grounding rows,
- training activities,
- training activity assignments,
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
- Conversation ownership uses `conversation`; history and compaction use Spring AI Session JDBC tables.
- Grounding persistence uses `grounding_*`.
- Formative activity persistence uses `training_activity` and `training_activity_assignment`.

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
- Spring AI Session advisor persistence, event filtering, and compaction,
- grounding persistence,
- training activity assignment persistence,
- legacy exclusion,
- Vaadin route startup,
- AI advisor startup.

---

## Use Case Relationship

This specification is the stable project baseline.

Future use cases are reviewable implementation slices of this specification. Completed pre-V1 use-case files are not retained as permanent architecture documentation.

New use cases must remain atomic enough to implement and verify without overwhelming the project context.

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
The final application must preserve Socratic learning behavior while operating on the target model centered on `account`, `tenant_account`, `group_class_member`, domain `conversation`, Spring AI Session history, `grounding`, `training_activity`, and `training_activity_assignment`.
</p>

<p>
The final application must not reactivate obsolete `client_id`, old chat, old document ingestion, old evaluation-run, or old student-profile persistence as active runtime.
</p>
