# Architecture

This document defines the implementation-level architecture baseline for Socratic Tutor. It keeps the project centered on learning workflows while aligning future work to the `account`-based, tenant-aware security direction established in `project-context.md` and UC-001.

## Quick path

1. Treat `account` as the canonical authenticated identity for every signed-in person.
2. Treat `tenant` as the first-class workspace boundary in a hierarchical multi-tenant model rooted in the default legacySubject scope **Introduction to Algorithms**.
3. Implement authorization through persisted roles and permissions enforced by application-managed Spring Security.
4. Keep tutor resources centered on `chat`, `document`, and `evaluation`, with tenant and ownership checks layered on top where required.

## Architectural stance

Socratic Tutor is a Spring Boot + Vaadin monolith with clear internal boundaries, not a collection of disconnected features. The architecture should optimize for:

- **Learning-first workflows** across chat, document ingestion, and evaluation.
- **Explicit security rules** inside the application, not delegated to external role systems.
- **Hierarchical multi-tenancy** where the legacySubject scope contains professor-owned tenant spaces.
- **Stable domain vocabulary** so future specs and code talk about the same nouns.
- **Incremental migration** from legacy `client_id` ownership to `account` + `tenant` boundaries without freezing delivery.

The target model is an application-managed tutor platform where identity, tenancy, authorization, ownership, AI orchestration, and UI flows each have a distinct responsibility.

## Hierarchical multi-tenant baseline

The architectural baseline is hierarchical multi-tenancy:

- **Subject scope root:** `Introduction to Algorithms` is the default legacySubject boundary for this project.
- **Tenant layer:** each professor owns a tenant space inside that legacySubject scope.
- **Membership layer:** students are associated with a professor tenant, not with the platform globally.
- **Resource layer:** `chat`, `document`, and `evaluation` instances are tenant-scoped records.

This means a professor's permissions do not imply global access to every tutor resource in the system. Professor authority is always interpreted inside professor-owned tenant boundaries unless a future use case explicitly expands the model.

## System shape

| Layer / area | Responsibility |
|---|---|
| `ui` | Vaadin views, view models, and UI orchestration for login, chat, document ingestion, and evaluation flows. |
| `services` | Application use-case orchestration, transaction boundaries, ownership checks, and policy-aware business workflows. |
| `data.entities` / `data.repositories` | Persistence model and repository access for tutor records and security records. |
| `ai` | Tutor prompting, routing, guardrails, memory, retrieval, and AI-adjacent orchestration. |
| `infrastructure` | External integrations and runtime adapters such as browser/session helpers and document-processing clients. |
| `config` | Spring configuration, property binding, and application-managed security wiring. |

This structure is already visible in the codebase and should remain the top-level organization. New work should deepen these boundaries rather than introduce parallel architectures.

## Canonical bounded areas

### Identity and access

Identity and authorization form one shared platform capability for the whole tutor.

- `account` is the root identity aggregate for both students and professors.
- `tenant` is the first-class workspace aggregate for professor-owned academic spaces.
- Role assignment answers what an account may do.
- Permission assignment answers which `resource:action` pairs are allowed.
- Tenant membership answers which workspace an account operates inside.
- Ownership answers which specific records an account may access inside that tenant.

This area should own:

- account lifecycle needed for sign-up and sign-in,
- tenant lifecycle and tenant membership needed for professor-owned academic spaces,
- persisted roles, permissions, and join relationships,
- authority derivation for Spring Security,
- authorization helpers used by tutor resource services.

### Chat

Chat is the learner-facing tutor workspace. It owns conversations, transcripts, messages, continuity, and tutor turn orchestration.

Authorization for chat is three-stage:

1. permission check for `chat:*`
2. tenant boundary check for the authenticated account and target chat
3. ownership check against the authenticated account for student-scoped conversations

Chat services should never rely only on route protection. They must re-check tenant boundary and ownership before loading or mutating a conversation. Anonymous conversations are outside the target architecture.

### Document

Document covers ingestion, review, segmentation, cataloging, retrieval, and tutor-context preparation for academic material.

In the current direction, document capabilities are professor-managed within the owning professor tenant. Document workflows may use AI and background processing, but access control still comes from the same account/role/permission model plus tenant boundary enforcement.

### Evaluation

Evaluation covers both evaluation definitions and learner runs.

- Professors manage evaluation definitions and related tutor assets.
- Students are limited to learner actions such as `evaluation:run` inside their assigned tenant unless a future use case expands the policy.

Evaluation should stay separate from chat as a domain area even when the UI reuses conversational interaction patterns.

## Security architecture

Security is application-managed. The target flow is:

`SecurityConfig` → authentication entry points → `CustomUserDetailsService` → persisted `account` + tenant membership + active roles + permissions → derived authorities → service-level tenant authorization + ownership checks

### Required components

| Component | Responsibility |
|---|---|
| `SecurityConfig` | Define login flow, protected routes, public assets, session rules, and authorization hooks for the Vaadin/Spring application. |
| `CustomUserDetailsService` | Load the authenticated `account`, resolve active roles and permissions, and emit Spring Security authorities from persisted data. |
| Security persistence model | Store `account`, `tenant`, membership, `role`, `account_role`, `permission`, and `role_permission` relationships. |
| Authorization helpers | Centralize permission, tenant, and ownership decisions so chat, document, and evaluation services do not duplicate policy logic inconsistently. |

### Authority model

Authorities should come from persisted tutor permissions, not hardcoded UI assumptions and not external identity-provider claims.

The conceptual chain is:

`account` → `account_role` → `role_permission` → `permission(resource, action)`

Recommended authority representation is a direct `resource:action` string or a small wrapper that preserves the same semantics. The important rule is consistency: UI guards, service guards, and tests should all reason over the same persisted permission vocabulary.

### Ownership model

Permissions alone are not enough. Tutor records are also tenant-scoped, and some are personal.

- Chat, document, and evaluation records are tenant-scoped.
- Student chat access is ownership-scoped to the authenticated `account` inside the assigned tenant.
- Document and evaluation management are professor-scoped only within the owning professor tenant in the current foundation.
- If a future use case introduces shared or delegated access, it must extend this model explicitly instead of bypassing it with ad hoc repository filters.

## Package responsibility guidance

Future implementation should use package boundaries like these:

| Package | Responsibility |
|---|---|
| `com.wornux.config` | Spring configuration and cross-cutting runtime wiring. Keep framework setup here, not business rules. |
| `com.wornux.security` | Security-specific components such as `CustomUserDetailsService`, authority mapping, security principals, and authorization helpers. |
| `com.wornux.data.entities.security` / `repositories.security` | Persistent identity, tenant, membership, and authorization records for `account`, `tenant`, roles, and permissions. |
| `com.wornux.services.chat` | Conversation lifecycle, tutor turns, transcript usage, tenant boundary checks, and chat ownership enforcement. |
| `com.wornux.services.document` | Ingestion pipeline, review flow, retrieval, and professor-tenant-scoped document management. |
| `com.wornux.services.evaluation` | Evaluation definition management, evaluation runs, and learner/professor capability boundaries inside tenant scope. |
| `com.wornux.services.profile` | Learner-profile signals and adaptive tutoring support. Keep this supportive to account security, not a replacement for it. |
| `com.wornux.ai` | AI orchestration only. It may consume authorized domain data, but it must not become the source of truth for access control. |
| `com.wornux.ui.*` | View composition and user interaction. UI can hide unavailable capabilities, but service-layer policy remains authoritative. |

The critical discipline here is SIMPLE: authorization decisions belong to security and service layers; repositories fetch data; UI reflects decisions; AI adapts learning behavior but does not decide permissions.

## Transitional treatment of legacy `client_id`

The current codebase still uses `client_id` across chat, document, and profile flows. Architecturally, this should be treated as a **legacy migration concern**, not the target identity or tenancy model.

That means:

- new specs and new security design should use `account` as the canonical term,
- new specs and new security design should use `tenant` as the canonical workspace boundary,
- new authorization logic should be designed around authenticated accounts and persisted authorities,
- existing `client_id` fields may remain temporarily as compatibility or migration scaffolding,
- migration work should progressively replace `client_id`-based ownership with `account` + `tenant` boundaries rather than expanding `client_id` into new areas.

In other words, `client_id` is an implementation-history concern. `account` and `tenant` are the architectural model.

## Implementation rules for future work

- Keep tutor resources centered on `chat`, `document`, and `evaluation`.
- Keep hierarchical multi-tenancy explicit in new design and migration work.
- Do not reintroduce Keycloak-specific or OAuth-only authority assumptions into domain design.
- Prefer explicit policy checks over implicit access hidden inside UI flows.
- Keep AI adapters and prompt services downstream from authorization, never upstream from it.
- Add new roles or permissions only when a concrete use case requires them.

## Next step

Use this architecture as the baseline for future implementation and for the later `datamodel` document, which should formalize the `account` / `tenant` / role / permission schema and tenant-aware ownership relationships without changing the architectural direction defined here.
