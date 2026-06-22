# Project Context

> High-level context for Socratic Tutor: the problem being solved, who it is for, what is in scope, and what constraints must guide future use cases, architecture, data model work, and implementation.

---

## 1. Vision

Socratic Tutor is an academic tutoring platform for programming and algorithm learning. Its purpose is to help students reason through concepts and exercises using guided Socratic dialogue, class-specific learning material, and formative activities.

The application must not behave like a generic anonymous chat application. It must behave like a structured academic platform where every protected action happens inside an authenticated institutional and group-class context.

Success means:

- students can enter an assigned class context and receive useful Socratic tutoring,
- professors can configure the learning context for their group classes,
- tenant admins can create the academic structure that professors and students operate inside,
- system admins can bootstrap institutions safely,
- tutor conversations, grounding material, and formative activities remain scoped to the right tenant and group class,
- authorization is explicit, persisted, and enforced beyond the UI.

---

## 2. Product Problem

Students often need help learning programming and algorithms without being given full answers immediately. Professors need a way to provide class-specific tutor context, formative activities, and guided support while keeping student data and academic resources separated by institution and class.

The core product problem is:

```text
How can an academic institution offer a guided AI tutor that helps students reason,
while preserving class boundaries, professor control, and trustworthy access rules?
```

Socratic Tutor solves this through:

- authenticated accounts,
- institution-level tenancy,
- group-class membership,
- role-based permissions,
- class-scoped conversations,
- class-scoped grounding material,
- class-scoped formative activities,
- service-layer authorization and ownership checks,
- AI guardrails that keep the tutor learning-first.

---

## 3. Core Users

### System Admin

Global platform operator.

Responsibilities:

- create tenants/institutions,
- invite tenant admins,
- inspect global platform setup where allowed,
- bootstrap the academic hierarchy.

The system admin is not a normal professor or student. This role has global administrative responsibility.

### Tenant Admin

Institution-level academic operator.

Responsibilities:

- manage academic periods inside the tenant,
- manage subjects inside the tenant,
- create group classes,
- invite professors into group classes,
- operate only inside assigned tenant boundaries.

A tenant admin does not own the whole platform.

### Professor

Group-class educator.

Responsibilities:

- operate inside group classes where they are active professor members,
- configure group-class information where allowed,
- manage students inside their own group classes,
- configure grounding material,
- create and manage formative activities,
- use tutor chat in the selected class context,
- review class-related information only when a use case explicitly allows it.

A professor is not globally attached to all tenants or all group classes.

### Assistant

Reserved academic support role.

The role is part of the foundation, but broad assistant capabilities should not be implemented until a dedicated use case defines what assistants can do.

### Student

Learner.

Responsibilities:

- access invited/assigned group classes,
- create and continue their own tutor conversations,
- use class-specific grounding through the tutor,
- view and update their own formative activity assignments,
- remain inside their own class and ownership boundaries.

Students do not manage tenants, subjects, academic periods, group classes, professors, students, or grounding material in the baseline.

---

## 4. Product Scope

The current product scope includes:

- Spring Boot + Vaadin Flow academic web application.
- PostgreSQL database with Flyway migrations.
- Application-managed authentication and authorization.
- Account-based identity.
- Tenant/institution hierarchy.
- Tenant account membership.
- Tenant-scoped roles and permissions.
- Academic structure: subjects, academic periods, group classes.
- Group-class membership.
- Tutor conversations through `conversation` and `conversation_snapshot`.
- Grounding material through `grounding_collection`, `grounding_document`, and `grounding_chunk`.
- Formative activities through `evaluation` and `evaluation_assignment`.
- Role-based onboarding and workspace routing.
- Service-layer permission, tenant, group-class, and ownership checks.
- AI tutor orchestration with guardrails and authorized grounding retrieval.
- Legacy isolation for obsolete persistence.

---

## 5. Out of Scope

Do not implement or imply these unless a future approved use case adds them:

- anonymous browser identity as target persistence,
- open public self-signup,
- professor-owned tenants,
- global professor access across all tenants,
- hardcoded PUCMM/ICC-101/periods/classes/professors/students in the baseline,
- Keycloak-managed authorization as the tutor authority source,
- `conversation_message`,
- `evaluation_run`,
- old `student_profile` as active identity, authorization, membership, or tutor context source,
- schools, departments, pensums, or deeper institutional hierarchy,
- payment/subscription features,
- production SIS integration.

---

## 6. Canonical Model

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

The canonical formative activity chain is:

```text
group_class
  -> evaluation
      -> evaluation_assignment
```

---

## 7. Key Domain Decisions

### Tenant means institution

A `tenant` represents a university, school, or academic institution.

It does not represent a professor workspace.

### Group class is the operational workspace

A `group_class` is the concrete class section where professors and students interact with tutor resources.

Conversations, grounding material, evaluations, and assignments are scoped to group classes.

### Account is the only authenticated identity root

Students, professors, assistants, tenant admins, and system admins are all `account` records.

Role and membership decide what an account can do.

### Roles are tenant-scoped

Roles are assigned through `tenant_account_role`, not directly to a global account.

This allows a person to have different responsibilities in different tenants.

### Permissions are not enough by themselves

Access requires:

```text
permission
  + tenant boundary
  + group-class membership
  + ownership check where required
```

For example, a student may have `CONVERSATION:VIEW`, but can still only view their own conversations inside an allowed group class.

### AI is downstream of authorization

AI advisors, prompts, memory, and retrieval must only receive authorized data.

The AI layer must not decide permissions or bypass service-layer checks.

---

## 8. Tutor Learning Principles

The tutor should:

- ask guiding questions,
- provide progressive hints,
- avoid dumping final answers when the student should reason,
- help students explain their thinking,
- correct misconceptions gently,
- use examples appropriate to the course and group-class context,
- stay academically scoped,
- use authorized grounding material when available,
- clearly avoid exposing other students' work or restricted material.

---

## 9. Constraints

- **Platform:** Web application using Vaadin Flow and Spring Boot.
- **Database:** PostgreSQL with Flyway migrations.
- **Authorization:** Application-managed RBAC with persisted roles, resources, actions, and permissions.
- **Tenancy:** Hierarchical academic multi-tenancy.
- **Operational workspace:** `group_class`.
- **Persistence identity:** `account`, not browser `client_id`.
- **Schema source of truth:** Flyway SQL migrations.
- **ORM behavior:** Hibernate validates schema; it must not create the production-like schema.
- **AI:** Spring AI/Ollama-style orchestration may be used, but it must remain downstream of authorization.
- **Vector search:** pgvector-backed grounding may be used for embeddings and retrieval.
- **UI:** Vaadin Flow protected routes and role-specific workspaces.
- **Security:** Password hashes only; no raw passwords.
- **Legacy:** Obsolete persistence must be isolated from active startup.

---

## 10. Related Documents

- [Spec README](README.md) — spec folder workflow and reading order.
- [Architecture](architecture.md) — technology stack, internal boundaries, and runtime architecture.
- [Design Context](design_context.md) — UX, navigation, layouts, and design intent for Socratic Tutor.
- [Data Model](datamodel/datamodel.md) — detailed schema, relationships, constraints, and seed data.
- [Use Case Template](use-cases/use-case-template.md) — template for feature-level use cases.
