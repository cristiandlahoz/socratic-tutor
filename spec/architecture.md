# Architecture

> Technology stack, runtime architecture, application structure, and implementation boundaries for Socratic Tutor. `pom.xml` remains the source of truth for exact dependency versions.

---

## 1. Architectural Stance

Socratic Tutor is a Spring Boot + Vaadin Flow monolith with clear internal domain boundaries.

It is not a set of disconnected features and it is not an anonymous chat app. It is an academic tutor platform where identity, tenancy, authorization, academic context, tutor conversations, grounding, formative activities, and AI orchestration each have distinct responsibilities.

The architecture must optimize for:

- learning-first tutor workflows,
- application-managed security,
- hierarchical academic multi-tenancy,
- explicit tenant and group-class boundaries,
- service-layer authorization,
- safe AI grounding retrieval,
- clean UI routing by role and context,
- legacy isolation without deleting useful reusable logic.

---

## 2. Technology Stack

- **Web Framework:** Vaadin Flow — server-side Java UI.
- **Backend:** Spring Boot — dependency injection, configuration, embedded server, application lifecycle.
- **Language:** Java, version determined by `pom.xml`.
- **Build Tool:** Maven, Maven wrapper preferred.
- **Database:** PostgreSQL.
- **Vector Support:** Spring AI PgVectorStore on PostgreSQL/pgvector for grounding retrieval.
- **Database Migrations:** Flyway SQL migrations.
- **ORM:** Spring Data JPA with Hibernate validation.
- **Security:** Spring Security with Vaadin integration and database-backed authentication.
- **Authorization:** Application-managed RBAC with roles, resources, actions, and permissions.
- **Validation:** Jakarta Bean Validation and Vaadin Binder.
- **Password Hashing:** Spring Security password encoder, normally BCrypt unless changed deliberately.
- **AI:** Spring AI / ChatClient-oriented orchestration with guardrails, routing, retrieval, and prompt composition.
- **Testing:** JUnit 5, Spring Boot tests, repository/service integration tests, Vaadin route/view tests where applicable.

---

## 3. System Shape

The application is organized around these active bounded areas:

| Area | Responsibility |
|---|---|
| `account` | Authenticated identity and account lifecycle. |
| `tenant` | Institution boundary and tenant membership. |
| `authorization` | Roles, resources, actions, permissions, and authorization helpers. |
| `academic` | Subjects, academic periods, group classes, and group-class members. |
| `conversation` | Tutor conversations and snapshots. |
| `grounding` | PgVectorStore-backed grounding rows, embeddings, and retrieval. |
| `training_activity` | Formative activity definitions and student assignments. |
| `onboarding` | Invitation acceptance, registration, role assignment, and workspace routing. |
| `ai` | Tutor prompt composition, advisors, tools, guardrails, memory, and retrieval orchestration. |
| `ui` | Vaadin views, layouts, navigation, and role/context-specific workspaces. |
| `legacy` | Isolated obsolete code that must not participate in active runtime. |

---

## 4. Canonical Runtime Chains

Identity and context:

```text
account
  -> tenant_account
      -> tenant_account_role
      -> group_class_member
```

Academic hierarchy:

```text
tenant
  -> subject
  -> academic_period
  -> group_class
      -> group_class_member
```

Tutor conversation:

```text
group_class_member
  -> conversation
      -> conversation_snapshot
```

Grounding:

```text
group_class
  -> grounding_vector_store
```

Formative activities:

```text
group_class
  -> training_activity
      -> training_activity_assignment
```

---

## 5. Application Structure

Recommended structure:

```text
src/main/java/com/wornux/
  ├── Application.java
  ├── config/
  │   ├── SecurityConfig.java
  │   ├── AiConfig.java
  │   ├── ChatProperties.java
  │   ├── GroundingProperties.java
  │   └── ...
  ├── security/
  │   ├── AuthenticatedAccount.java
  │   ├── CustomUserDetailsService.java
  │   ├── PermissionChecker.java
  │   ├── ScopeAuthorizer.java
  │   └── WorkspaceContextResolver.java
  ├── data/
  │   ├── entities/
  │   │   ├── account/
  │   │   ├── tenant/
  │   │   ├── authorization/
  │   │   ├── academic/
  │   │   ├── conversation/
  │   │   ├── grounding/
  │   │   └── training_activity/
  │   └── repositories/
  │       ├── account/
  │       ├── tenant/
  │       ├── authorization/
  │       ├── academic/
  │       ├── conversation/
  │       ├── grounding/
│   └── training_activity/
  ├── services/
  │   ├── account/
  │   ├── tenant/
  │   ├── authorization/
  │   ├── academic/
  │   ├── conversation/
│   ├── grounding/
│   ├── training_activity/
│   └── onboarding/
  ├── ai/
  │   ├── advisor/
  │   ├── guard/
  │   ├── prompt/
  │   ├── retrieval/
  │   ├── routing/
  │   └── tools/
  ├── ui/
  │   ├── auth/
  │   ├── onboarding/
  │   ├── admin/
  │   ├── tenant/
  │   ├── professor/
  │   ├── student/
  │   ├── chat/
│   ├── grounding/
│   └── training_activity/
  ├── infrastructure/
  │   ├── document/
  │   ├── storage/
  │   └── observability/
  └── legacy/
      ├── chat/
      ├── student_profile/
      ├── document_ingestion/
      └── evaluation_run/
```

Active code must not depend on `legacy`.

Legacy packages may remain for reference or future migration, but they must be excluded from active JPA repository scanning, entity scanning, Spring component scanning, AI prompt context, and authorization logic.

---

## 6. Database Architecture

- **Database:** PostgreSQL.
- **Schema management:** Flyway.
- **Migration location:** `src/main/resources/db/migration/`.
- **Naming:** `V[N]__[description].sql`.
- **Hibernate behavior:** `ddl-auto=validate`.
- **Timestamps:** Java `Instant`; PostgreSQL `timestamptz`.
- **Business/domain identifiers:** UUID.
- **Internal/catalog identifiers:** `BIGINT GENERATED BY DEFAULT AS IDENTITY`.
- **Vector embeddings:** Stored on `grounding_vector_store.embedding` when vector retrieval is enabled.

### Identifier Split

UUID for:

```text
account
tenant
tenant_account
subject
academic_period
group_class
group_class_member
conversation
training_activity
training_activity_assignment
grounding_vector_store
```

BIGINT identity for:

```text
role
resource
action
permission
group_class_join_code
conversation_snapshot
```

This split keeps business/security boundary identifiers non-sequential while allowing internal implementation records to remain simple and efficient.

---

## 7. Security Architecture

### Authentication

- Spring Security authenticates users.
- Database-backed `account` is the source of authenticated identity.
- Passwords are stored as hashes only.
- Vaadin routes integrate with Spring Security.
- Login is the first screen for unauthenticated users.
- Open public self-signup is not part of the target model.
- Registration happens through valid invitation/onboarding context.

### Authorization

Authorization is application-managed.

Persisted chain:

```text
tenant_account
  -> tenant_account_role
      -> role
          -> role_permission
              -> permission
                  -> resource
                  -> action
```

Every protected service operation should evaluate:

```text
1. Is the account authenticated?
2. Is the account active/unlocked?
3. Is the tenant account active/unlocked?
4. Does the tenant account have the required permission?
5. Is the target record inside the same tenant?
6. Is the target record inside an allowed group class?
7. If the record is personal, does ownership match?
```

### Scope Enforcement

Permissions do not automatically grant data access.

A student may have `CONVERSATION:VIEW`, but still cannot view another student's conversation.

A professor may have `GROUNDING:CREATE`, but only inside a group class where the professor is an active professor member.

A tenant admin may have `GROUP_CLASS:CREATE`, but only inside the assigned tenant.

A system admin may have global administrative visibility where explicitly allowed.

---

## 8. Spring Security Configuration Intent

Security configuration should be explicit and simple.

Conceptual shape:

```java
@Configuration
@EnableWebSecurity
class SecurityConfig {

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

The exact code may vary by framework version, but the architectural intention is stable.

| Security area | Architectural intent |
|---|---|
| Static assets | Permit Vaadin and theme assets so public pages render. |
| `/login` | Public entry for unauthenticated users. |
| Invitation acceptance | Public token entry; token validation decides whether flow continues. |
| Onboarding routes | Temporary onboarding access only; not a workspace bypass. |
| Workspaces | Require authenticated session. |
| Service authorization | Required for every protected domain operation. |
| UI hiding | Helpful UX, never the security source of truth. |
| User details service | Load `account`, tenant accounts, roles, and permissions from DB. |
| Workspace resolver | Resolve default tenant/group-class context after login. |

---

## 9. Workspace Context Architecture

The UI and services need a current academic context.

A context resolver should determine:

```text
authenticated account
active tenant_account
active role set
active group_class_member
active tenant
active group_class
```

The resolver should use:

- explicit user selection when available,
- `last_tenant_account_id`,
- `last_group_class_member_id`,
- deterministic fallback to first accessible context,
- safe no-context state when nothing is available.

Missing context must not fall back to browser cookies or create academic records implicitly.

---

## 10. Conversation Architecture

Target model:

```text
conversation
conversation_snapshot
```

Old model:

```text
chat
chat_transcript
chat_message
```

The old model is obsolete as active persistence.

### Relationship Shape

```text
GroupClassMember 1 -> * Conversation
Conversation 1 -> * ConversationSnapshot
Conversation 1 -> 0..1 currentSnapshot
ConversationSnapshot 0..1 -> previous ConversationSnapshot
```

A conversation's `current_snapshot_id` points to the currently active snapshot.

The full snapshot list is separate from the current snapshot relationship.

### Snapshot Strategy

A snapshot stores:

- message list,
- compacted carry context,
- message count,
- token count,
- version,
- creation timestamp,
- optional compaction timestamp.

Compaction should create a new snapshot and move the conversation pointer forward instead of mutating old transcript rows.

---

## 11. Grounding Architecture

Grounding provides class-specific material to the tutor.

Target model:

```text
grounding_vector_store
```

Old model:

```text
ingested_document
document_ingestion_job
document_segment
vector_store
```

Old normalized document-ingestion persistence is obsolete as active persistence.

### Retrieval Boundary

Grounding retrieval must be scoped to the active group class.

AI retrieval tools should receive either:

- an already-authorized group-class id,
- or an already-authorized retrieval context object.

They must not query global grounding rows without scope constraints.

---

## 12. Formative Activity Architecture

Database model:

```text
training_activity
training_activity_assignment
```

Product-facing name:

```text
formative activity
```

`training_activity` is the definition.

`training_activity_assignment` is the student-facing assigned state.

`evaluation_run` is not part of the active target model.

Assignment progress is represented by status transitions on `training_activity_assignment`.

---

## 13. AI Architecture

AI configuration should remain centralized and explicit.

AI orchestration may include:

- ChatClient/model configuration,
- tutor prompt resources,
- guard advisors,
- pedagogical routing advisors,
- grounding retrieval tools,
- document/catalog context backed by target grounding data,
- memory/conversation context backed by target snapshots,
- logging/observability advisors.

AI must not:

- decide authorization,
- bypass tenant or group-class checks,
- use legacy persistence as active context,
- hardcode obsolete subject vocabulary,
- query cross-tenant data.

The AI layer should consume authorized domain context prepared by services.

---

## 14. Vaadin UI Architecture

Use role-specific workspace layouts.

| Workspace | Context selector | Main navigation |
|---|---|---|
| System Admin | Platform-level | Tenants, tenant-admin invitations, platform setup |
| Tenant Admin | Tenant selector | Dashboard, periods, subjects, group classes, invitations |
| Professor | Group-class selector | Home, new chat, formative activities, grounding, students |
| Student | Group-class selector or simple context header | New chat, conversation history, assigned activities |

Vaadin route guards and layouts should reflect authentication and context, but the service layer remains authoritative.

---

## 15. Configuration Architecture

Application configuration should be centralized and meaningful.

Important configuration groups:

```text
spring.datasource
spring.flyway
spring.jpa
spring.ai / model provider settings
app.chat or tutor.chat settings
grounding/vector store settings
security/session settings
document processing settings
observability/logging settings
```

Avoid introducing replacement configuration objects unless they have a clear target purpose.

Do not create a new browser identity configuration for academic persistence. Browser cookies may exist for technical session behavior, but they must not become domain identity.

---

## 16. Legacy Architecture

Legacy code must be isolated under a clear package such as:

```text
com.wornux.legacy
```

Legacy areas include:

```text
old chat persistence
old student profile persistence
old document ingestion persistence
old evaluation_run persistence
client_id-based ownership logic
```

Active startup must not scan legacy entities or repositories.

Active services must not inject legacy repositories.

Legacy logic may only be reintroduced through an approved feature that maps it into the target account/tenant/group-class model.

---

## 17. Service Layer Rules

- Services own business logic.
- Services own authorization checks.
- Services own transaction boundaries.
- Services should return DTOs or view models, not expose mutable entities to UI.
- Repositories only fetch and persist data.
- UI never performs direct repository access.
- AI tools do not perform unrestricted data access.
- All write operations must validate tenant/group-class context.

---

## 18. Testing Strategy

Test by behavior and by architectural boundary.

Required categories:

- schema migration tests,
- repository mapping tests,
- authorization tests,
- tenant scope tests,
- group-class membership tests,
- ownership tests,
- conversation snapshot tests,
- grounding retrieval tests,
- formative assignment tests,
- Vaadin route startup tests,
- AI advisor startup tests,
- legacy exclusion tests.

Final verification command:

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

---

## 19. Build and Run Locally

Typical local verification:

```bash
./mvnw clean test
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

PostgreSQL should be available locally or through Docker Compose according to project configuration.

The application must start only when Flyway migrations apply and Hibernate validates the schema.

---

## 20. Deployment Checklist

- [ ] Flyway migrations apply.
- [ ] Hibernate validates target ERD.
- [ ] Legacy repositories are excluded.
- [ ] Security filter chain protects workspaces.
- [ ] Static assets and login render unauthenticated.
- [ ] Password hashing is configured.
- [ ] Account principal resolves after login.
- [ ] Role and permission authorities derive from DB.
- [ ] Tenant scope checks are enforced.
- [ ] Group-class membership checks are enforced.
- [ ] Student ownership checks are enforced.
- [ ] AI guardrails start.
- [ ] Grounding retrieval is scoped.
- [ ] Vaadin routes instantiate.
- [ ] No active runtime depends on `client_id`.
