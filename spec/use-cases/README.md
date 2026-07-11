# Specification and Use Case Catalog

This folder contains two independent numbered series:

- `SPEC-NNN` files define cross-cutting technical foundations and implementation constraints.
- `UC-NNN` files define actor-visible behavior, alternative flows, business rules, and acceptance tests.

Numbers may repeat across the two series. Always reference the complete identifier (`SPEC-005`, not just `005`). A use case may depend on several technical specs; a technical spec may be implemented through several use cases.

The canonical project documents in `spec/` remain authoritative. When an approved specification changes persistence, architecture, context, or shared UX states, update those canonical documents in the same specification change.

## Workflow

1. Read `../project-context.md`, `../spec.md`, `../architecture.md`, and `../datamodel/datamodel.md`.
2. Read technical specs in dependency order.
3. Read the target use case and every use case it references.
4. Implement one bounded increment at a time.
5. Run the target flow/rule tests and regression tests for already implemented dependencies.
6. Mark a document `Implemented` only when its mapped implementation and verification are complete.

Do not repeatedly reimplement every prior use case. Re-run their acceptance/regression tests after dependent changes.

## Technical specification index

| ID | Title | Status | Primary target | File |
|----|-------|--------|----------------|------|
| SPEC-001 | RBAC Schema and Domain Model | Verified | Database, entities, repositories | [001-rbac-schema-and-domain-model.md](001-rbac-schema-and-domain-model.md) |
| SPEC-002 | Authorization Engine, Cache, and Annotations | Verified | Security services, cache, annotations | [002-authorization-engine-cache-and-annotations.md](002-authorization-engine-cache-and-annotations.md) |
| SPEC-003 | Login Context, Navigation, and Route Security | Implemented | Login, active context, route security | [003-login-context-navigation-and-route-security.md](003-login-context-navigation-and-route-security.md) |
| SPEC-004 | Role Matrix and Assignment UI | Implemented | Contextual role administration | [004-role-matrix-and-assignment-ui.md](004-role-matrix-and-assignment-ui.md) |
| SPEC-005 | Formative Activity Persistence and Orchestration | Pending | Data model, async AI jobs, outbox, concurrency | [005-formative-activity-persistence-and-orchestration.md](005-formative-activity-persistence-and-orchestration.md) |

## Behavioral use case index

| ID | Title | Status | Primary actor | File |
|----|-------|--------|---------------|------|
| UC-003 | Training Activity Lifecycle | Pending | Professor | [use-case-003-training-activity-lifecycle.md](use-case-003-training-activity-lifecycle.md) |
| UC-005 | Safe Browser Session | In Progress | Student / Professor reviewer | [use-case-005-safe-browser-mode.md](use-case-005-safe-browser-mode.md) |
| UC-006 | Advisory AI Instruction Quality Review | In Progress | Professor | [use-case-006-ai-instruction-quality-review.md](use-case-006-ai-instruction-quality-review.md) |
| UC-007 | Durable Adaptive Student Tutor Runtime | In Progress | Student | [use-case-007-adaptive-student-tutor-runtime.md](use-case-007-adaptive-student-tutor-runtime.md) |
| UC-008 | Publish and Deliver a Training Activity | In Progress | Professor | [use-case-008-publish-and-deliver-training-activity.md](use-case-008-publish-and-deliver-training-activity.md) |
| UC-009 | Finalize and Report a Training Evaluation | Pending | Professor reviewer | [use-case-009-finalize-and-report-evaluation.md](use-case-009-finalize-and-report-evaluation.md) |

Missing UC numbers are reserved or belong to historical work; do not renumber approved identifiers merely to close gaps.

## Formative activity dependency order

```text
SPEC-005
  ├── UC-003 activity lifecycle
  │     └── UC-006 advisory instruction review
  │           └── UC-008 publish and deliver
  ├── UC-005 Safe Browser session
  └── UC-007 durable tutor runtime
        └── UC-009 finalization and report
```

Recommended implementation sequence:

1. SPEC-005 schema, state machines, durable AI jobs, outbox, and concurrency foundation.
2. UC-003 and UC-006 draft lifecycle and advisory review.
3. UC-008 atomic publication, student workspace delivery, and notification outbox.
4. UC-005 Safe Browser sessions/events and heartbeat expiry.
5. UC-007 non-blocking tutor runtime and durable nonblank answers.
6. UC-009 immediate student completion and asynchronous structured reports.

## Formative activity non-negotiable rules

- AI instruction review is a suggestion. Deterministic blank validation is mandatory; a professor may explicitly save or publish despite AI advice.
- No Vaadin request/session thread or domain transaction waits for LLM or SMTP work.
- Blank/whitespace-only student answers are rejected in UI and backend and create no AI job.
- Every accepted answer is committed before requesting the next tutor decision.
- Student completion never waits for final report generation.
- Assignment, turn, report, review, Safe Browser, job, and outbox responsibilities remain separated as specified by SPEC-005.
- There is no POC-era global limit of one published activity per professor.
- Published evidence is archived, not cascade-deleted through ordinary product actions.

## RBAC foundation constraints

The existing RBAC specifications retain their own ordered dependency:

```text
SPEC-001 -> SPEC-002 -> SPEC-003 -> SPEC-004
```

Follow their explicit schema, permission, active-context, route-security, and role-management rules. Formative activity implementation must use those authorization services and must not create a parallel authorization model.

## Status legend

- **Pending** — approved or drafted target is not yet fully implemented and verified.
- **In Progress** — implementation is underway.
- **Implemented** — code and required automated/manual checks are complete.
- **Verified** — implemented behavior has been reviewed against the specification.
- **Superseded** — retained only for history and replaced by an identified canonical document.

## Maintenance rule

When adding, renaming, or replacing a file:

1. Preserve its complete `SPEC-NNN` or `UC-NNN` identity.
2. Update the applicable index and dependency relationship.
3. Remove or mark contradictory behavior in the replaced canonical document.
4. Keep flows and business rules testable.
5. Do not leave implementation agents to infer transaction, ownership, idempotency, or failure semantics that affect correctness.
