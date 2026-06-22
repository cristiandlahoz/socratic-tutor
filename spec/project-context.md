# Project Context

Socratic Tutor is an academic learning product for students and educators. Its job is not to behave like a generic chat app, but to support reasoning, guided practice, and trustworthy learning workflows through tutor conversations, document ingestion, and evaluations.

## Why this document exists

Use this file as the shared vocabulary anchor for future use cases, architecture notes, and data model documents. It defines the product-level nouns and boundaries that should stay stable even when implementation details change.

## Product direction

The default academic scope for this project is **Introduction to Algorithms**. Future legacySubject expansion may exist later, but the current specification baseline assumes one legacySubject domain with professor-owned tenant spaces inside it.

The product serves two primary audiences:

- **Students**, who use the tutor to explore concepts, ask questions, work through algorithms, use ingested learning material, and run evaluations.
- **Educators / professors**, who need the tutor to feel academically trustworthy and to manage learning assets that support students.

The intended experience is rigorous, calm, and learning-first. The system should reduce cognitive noise and keep attention on reasoning, evidence, and guided progress.

## Core domain vocabulary

| Term | Meaning in Socratic Tutor |
|------|---------------------------|
| **account** | The shared authenticated identity aggregate for every signed-in person. Students and professors are both accounts. |
| **tenant** | A first-class academic workspace boundary inside the default legacySubject scope. A tenant is owned by one professor account and contains the students and tutor resources managed within that professor's space. |
| **role** | A capability grouping assigned to an account, such as `STUDENT` or `PROFESSOR`. |
| **permission** | An allowed `resource:action` pair granted through role assignments. |
| **resource** | A protected tutor capability area. The current foundation centers on `chat`, `document`, and `evaluation`. |
| **action** | An operation on a resource, such as `view`, `create`, `update`, `delete`, or `run`. |
| **chat** | A tutor conversation workspace used for guided reasoning. Every chat belongs to one tenant and one authenticated owning account. |
| **document** | Ingested academic material that can be processed, reviewed, cataloged, and used as tutor context. Documents are tenant-scoped resources. |
| **evaluation** | A structured assessment artifact, including both evaluation definitions and learner runs. Evaluations are tenant-scoped resources. |
| **ownership boundary** | A rule that ties a resource instance to a specific tenant and, where needed, to a specific account. Student chat access remains personal within the tenant boundary. |

## Identity and authorization model

The project direction established by UC-001 is explicit:

- Authentication and authorization are **application-managed**, not delegated to Keycloak or an external role server.
- `account` is the single identity root for authenticated people.
- `tenant` is the first-class workspace boundary for professor-led academic spaces inside the default legacySubject scope.
- Roles and permissions are persisted in the application database.
- Tutor access is decided through a role/permission/resource matrix plus tenant and ownership checks, not through separate identity types or anonymous claims.

This means future specs should talk about:

`account` → `account_role` → `role_permission` → `permission` (`resource` + `action`)

and should avoid reintroducing old concepts such as `client_id` as the long-term identity model, anonymous ownership, or Keycloak-specific authority assumptions.

## Subject and tenant foundation

- The baseline legacySubject scope is **Introduction to Algorithms**.
- Each professor owns one or more tenant spaces within that legacySubject scope.
- Students belong to a professor-owned tenant space; they are not globally attached to every professor in the system.
- Tutor resources in this foundation are created and accessed within tenant boundaries.

### Chat

Chat is the learner-facing tutor workspace. It stores conversations, messages, and tutor continuity. Chat access is tenant-scoped first, then ownership-scoped where applicable. For students, access is not only permission-based; it is also ownership-based, meaning an account can only access its own conversations inside its assigned tenant unless a future use case defines a broader rule.

### Document

Document represents ingested learning material that the tutor can process and use as context. In the current foundation, document capabilities are professor-managed inside the professor's tenant. Future use cases should treat document ingestion, review, cataloging, and retrieval as part of one coherent tutor resource family without implying cross-tenant professor access.

### Evaluation

Evaluation covers structured formative assessment. The foundation already distinguishes between managing evaluations and running them. Professors manage evaluation definitions inside their tenant; students are limited to permitted learner actions such as `evaluation:run` within their assigned tenant unless a later use case extends the model.

## Role foundation

The initial authorization matrix is intentionally small and product-specific:

- **`STUDENT`**: chat capabilities plus `evaluation:run`; no document permissions; access stays inside the student's assigned tenant.
- **`PROFESSOR`**: chat capabilities plus document and evaluation management capabilities inside professor-owned tenants only.

This is a foundation, not the final full policy surface. New roles or permissions should be introduced only when a concrete use case requires them.

## Domain boundaries for future specs

When writing future use cases or design docs, keep these boundaries clear:

- **Identity** answers who the authenticated person is: `account`.
- **Authorization** answers what that account may do: roles, permissions, resources, actions.
- **Tenancy** answers which professor-owned workspace contains the account and the resource instance.
- **Ownership** answers which specific records that account may access inside that tenant, especially for student chat data.
- **Tutor resources** should stay centered on chat, document, and evaluation unless the product scope explicitly expands.
- **Learning context** such as the default legacySubject scope, misconceptions, document segments, and evaluation runs supports the tutor domain, but does not replace the account-centered and tenant-aware security model.

## Transitional note

The current codebase still contains legacy identifiers such as `client_id` in existing schema and flows. That reflects implementation history, not the target vocabulary for new specification work. New specs and architecture discussions should treat `account` as the canonical identity concept, `tenant` as the canonical workspace boundary, and describe legacy identifiers only when documenting migration or compatibility work.

## What this document should guide next

Use this context as the baseline for:

- future use cases that need consistent tutor-domain language,
- architecture notes that define application-managed security,
- data model documents that formalize `account`, `tenant`, roles, permissions, and tenant-aware tutor resource ownership.
