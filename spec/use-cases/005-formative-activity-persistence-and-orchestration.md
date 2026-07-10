# SPEC-005 — Formative Activity Persistence and Orchestration

**Status:** Pending  
**Date:** 2026-07-10  
**Depends on:** SPEC-001, SPEC-002, SPEC-003, and SPEC-004  
**Implemented through:** UC-003, UC-005, UC-006, UC-007, UC-008, and UC-009

## Goal

Replace the formative-activity POC persistence and synchronous LLM flow with a clean, durable, non-blocking module that preserves student evidence, supports safe retries, and keeps UI, domain, persistence, and AI responsibilities separate.

This specification is the technical contract shared by the formative-activity use cases. The use cases define observable behavior; this document defines the persistence and orchestration rules required to implement that behavior safely.

## Scope

This specification covers:

- training activity lifecycle persistence;
- advisory instruction reviews and professor overrides;
- publication and student assignment delivery;
- asynchronous first-question and follow-up tutor decisions;
- durable question-and-answer turns;
- asynchronous final report generation;
- Safe Browser sessions and events;
- email/event delivery through a transactional outbox;
- concurrency, idempotency, retries, and observability;
- a clean baseline migration with no legacy-data preservation.

It does not define:

- a generic workflow engine;
- multiple attempts per assignment;
- automatic grading or numeric scores;
- storage of model chain-of-thought;
- absolute prevention of browser or operating-system actions;
- distributed microservices.

The target remains a modular Spring Boot + Vaadin monolith.

## Architectural principles

1. The UI never calls an LLM, SMTP server, or repository directly.
2. A request that starts an assignment or submits an answer completes after a short database transaction; it does not wait for model generation.
3. No database transaction remains open while waiting for an external model or email provider.
4. Student answers are durable before a follow-up model call is scheduled.
5. Model output is untrusted until parsed and validated by the backend.
6. Assignment state, turns, reports, reviews, Safe Browser sessions, and delivery events have separate persistence responsibilities.
7. Commands are idempotent and mutable aggregates use optimistic concurrency.
8. AI failures are recoverable workflow states, not reasons to discard accepted student work.
9. Student-facing tutor work has higher execution priority than instruction reviews and report generation.
10. Internal records are authorized through their parent `training_activity` or `training_activity_assignment`; they do not create new RBAC resources.

## Canonical aggregate and table model

```text
group_class
  -> training_activity
      -> training_instruction_review
          -> training_instruction_review_override
      -> training_activity_assignment
          -> training_activity_turn
          -> training_activity_report
          -> safe_browser_session
              -> safe_browser_event

training_activity_ai_job       durable internal AI work
outbox_event                   durable integration delivery
```

### `training_activity`

Owns the professor-authored definition and lifecycle.

Required data:

```text
id
group_class_id
created_by_tenant_account_id
created_by_group_class_member_id
title
instructions
status
safe_browser_required
opens_at
closes_at
published_at
closed_at
version
created_at
updated_at
```

Rules:

- Status is `DRAFT | PUBLISHED | CLOSED | ARCHIVED`.
- Title, instructions, and Safe Browser configuration are editable only in `DRAFT`.
- Publishing makes the evaluated definition immutable.
- A published or closed activity is archived instead of physically deleted.
- Only a draft with no publication history may be physically deleted.
- `version` is an optimistic-lock value.

### `training_instruction_review`

Stores an immutable result for one exact instructions value and rubric version. Review history must not be flattened into columns on `training_activity`.

Required data:

```text
id
candidate_id
training_activity_id nullable
group_class_id
requested_by_group_class_member_id
title_snapshot
instructions_snapshot
instructions_hash
execution_status       PENDING | SUCCEEDED | FAILED
outcome                GOOD | NEEDS_IMPROVEMENT | INVALID | null
summary
issues                 jsonb
improved_instructions
model_name
rubric_version
failure_code
requested_at
completed_at
```

Rules:

- Freshness is based on the exact normalized instructions text, model policy, and rubric version.
- The title may be supplied as model context, but changing only the title does not invalidate a review.
- `candidate_id` correlates review work before the first draft save; `training_activity_id` may be attached once when that candidate becomes a saved activity.
- `issues` contains displayable diagnostics, source ranges when reliable, reasons, and suggested replacements.
- Invalid or malformed model output produces `FAILED`; raw unvalidated output is never shown as a review.
- Review content is append-only history; only the one-time association of an unsaved candidate to its saved activity may be added later.
- Unassociated abandoned candidate reviews follow a documented retention policy.

### `training_instruction_review_override`

Audits a professor decision to continue despite an AI warning or unavailable review.

Required data:

```text
id
training_activity_id
training_instruction_review_id nullable
instructions_hash
action                    SAVE_DRAFT | PUBLISH
actor_group_class_member_id
created_at
```

An override acknowledges only the referenced instructions hash and action. It never bypasses deterministic validation, authorization, or lifecycle rules.

### `training_activity_assignment`

Represents delivery and the single supported student evaluation attempt.

Required data:

```text
id
training_activity_id
group_class_member_id
status
assigned_at
started_at
submitted_at
evidence_status nullable
completion_reason nullable
version
updated_at
```

Status is:

```text
ASSIGNED
STARTING
WAITING_FOR_ANSWER
WAITING_FOR_TUTOR
SUBMITTED
SKIPPED
EXPIRED
EXCUSED
```

State machine:

```text
ASSIGNED -> STARTING -> WAITING_FOR_ANSWER
WAITING_FOR_ANSWER -> WAITING_FOR_TUTOR -> WAITING_FOR_ANSWER
STARTING | WAITING_FOR_ANSWER | WAITING_FOR_TUTOR -> SUBMITTED
ASSIGNED | STARTING | WAITING_FOR_ANSWER | WAITING_FOR_TUTOR -> EXPIRED | EXCUSED
ASSIGNED -> SKIPPED
```

Rules:

- `(training_activity_id, group_class_member_id)` is unique.
- `version` protects every state transition.
- Safe Browser blocking is derived from the current Safe Browser session; it is not duplicated as an unrelated assignment status.
- A model or report failure does not move the assignment backwards or erase accepted turns.

### `training_activity_turn`

Stores one tutor question and its optional student answer. It is the authoritative transcript.

Required data:

```text
id
training_activity_assignment_id
sequence_number
question_text
question_created_at
answer_text nullable
answer_submission_id nullable
answer_submitted_at nullable
decision_type nullable
answer_quality nullable
evidence_status nullable
coverage_status nullable
pedagogical_move nullable
decision_metadata jsonb nullable
created_at
updated_at
```

Constraints:

- `(training_activity_assignment_id, sequence_number)` is unique.
- `question_text` must contain at least one non-whitespace character.
- If `answer_text` is not null, it must contain at least one non-whitespace character.
- `answer_submission_id` is unique within the assignment and provides client-command idempotency.
- A turn accepts at most one authoritative answer.
- `decision_metadata` stores validated outcome fields only; model chain-of-thought and hidden reasoning are prohibited.

### `training_activity_report`

Stores one structured professor report per submitted assignment.

Required data:

```text
id
training_activity_assignment_id
status                    PENDING | GENERATING | READY | FAILED
evidence_status
summary
strengths                  jsonb
weaknesses                 jsonb
observations               jsonb
recommendations            jsonb
model_name
prompt_version
attempt_count
last_error_code
version
requested_at
completed_at
updated_at
```

Rules:

- `training_activity_assignment_id` is unique.
- Question-and-answer history is read from `training_activity_turn`; it is not duplicated inside report Markdown.
- A rendering layer may produce formatted text from the structured report, but rendered Markdown is not the source of truth.
- A failed report never reopens or un-submits an assignment.

### `training_activity_ai_job`

Provides durable work for instruction review, first question, next tutor decision, and final report generation.

Required data:

```text
id
job_type                  INSTRUCTION_REVIEW | FIRST_QUESTION | NEXT_DECISION | FINAL_REPORT
priority
training_activity_id nullable
training_instruction_review_id nullable
training_activity_assignment_id nullable
training_activity_turn_id nullable
training_activity_report_id nullable
input_version
semantic_key
generation
status                    PENDING | RUNNING | SUCCEEDED | RETRYABLE | FAILED
attempt_count
max_attempts
available_at
lease_until nullable
last_error_code nullable
created_at
updated_at
```

Rules:

- `(semantic_key, generation)` identifies job history, and a partial uniqueness rule prevents duplicate live jobs for the same semantic input.
- Claiming a job uses an atomic database operation and a finite lease.
- The claim transaction ends before the model call starts.
- Applying a result happens in a new short transaction and requires the expected aggregate/input version.
- A stale result is discarded safely and recorded; it must not overwrite newer professor text or student state.
- Student tutor jobs have higher priority than review and report jobs.
- Concurrency is bounded according to configured model capacity; an unbounded executor is prohibited.

### `safe_browser_session`

Required data:

```text
id
training_activity_assignment_id
token_hash
status                    PENDING | ACTIVE | VIOLATED | EXPIRED | ENDED
started_at
last_heartbeat_at
ended_at nullable
version
created_at
updated_at
```

Rules:

- At most one `PENDING` or `ACTIVE` session exists per assignment.
- The server issues an opaque session token and stores only its hash.
- Every heartbeat and event validates authenticated student ownership, assignment id, and session token.
- A `VIOLATED`, `EXPIRED`, or `ENDED` session can never become active again.
- Unlocking allows creation of a new session; it does not reactivate the violated session.

### `safe_browser_event`

Required data:

```text
id
safe_browser_session_id
training_activity_assignment_id
client_event_id
event_type
client_occurred_at nullable
received_at
metadata jsonb nullable
```

Rules:

- `(safe_browser_session_id, client_event_id)` is unique.
- Supported violations include `FULLSCREEN_EXIT`, `TAB_HIDDEN`, `WINDOW_BLUR`, `BEFORE_UNLOAD`, and `HEARTBEAT_LOST`.
- `MANUAL_UNLOCK` is an auditable professor action, not a violation.
- Event history is append-only.

### `outbox_event`

Required data:

```text
id
aggregate_type
aggregate_id
event_type
deduplication_key
payload jsonb
status                    PENDING | PROCESSING | PUBLISHED | FAILED
attempt_count
available_at
lease_until nullable
last_error_code nullable
created_at
published_at nullable
```

Rules:

- `deduplication_key` is unique.
- Domain state and its outbox event are committed in the same transaction.
- Email or other external delivery occurs after commit.
- Retrying an event must not send a duplicate logical notification.

## Non-blocking orchestration contracts

### Start assignment

```text
Student command
  -> short transaction: validate + ASSIGNED -> STARTING + enqueue FIRST_QUESTION
  -> return immediately with STARTING
  -> worker claims job
  -> model call outside transaction
  -> short transaction: persist first turn + STARTING -> WAITING_FOR_ANSWER
  -> UI receives/polls the new state
```

### Submit answer

```text
Student command with answerSubmissionId
  -> trim and reject blank input
  -> short transaction: validate ownership/state/session
  -> persist answer on current turn
  -> WAITING_FOR_ANSWER -> WAITING_FOR_TUTOR
  -> enqueue NEXT_DECISION
  -> return immediately
  -> worker calls model outside transaction
  -> short transaction: persist next turn or terminal decision
```

The accepted answer remains durable if the client disconnects, the model times out, generation is cancelled, or the application restarts.

### Complete assignment

```text
Validated terminal tutor decision
  -> short transaction: assignment -> SUBMITTED
  -> create report PENDING
  -> enqueue FINAL_REPORT
  -> return student to dashboard without waiting for report
  -> report worker generates and stores structured report later
```

### Publish activity

```text
Professor command
  -> short transaction: validate DRAFT + create assignments + PUBLISHED
  -> create ACTIVITY_PUBLISHED outbox event
  -> commit
  -> student dashboard reads committed assignments immediately
  -> outbox worker sends notification emails after commit
```

## Transaction boundaries

Services own all transaction boundaries.

Permitted transaction work:

- authorization and ownership checks;
- state-transition validation;
- insert/update of domain rows;
- idempotency checks;
- AI job/outbox creation or claim;
- application of already validated model results.

Prohibited inside a domain transaction:

- LLM calls or streaming;
- SMTP calls;
- waiting for browser push acknowledgement;
- unbounded retry loops;
- sleeping while a lease or row lock is held.

## UI integration contract

- Vaadin request/session threads must not wait for LLM generation.
- Start, answer, review, and report commands return a persisted state immediately.
- The UI renders explicit pending, retryable-error, blocked, and completed states.
- Updates may arrive through Vaadin push or bounded polling; correctness cannot depend on an in-memory event bus.
- Navigating away and returning reconstructs the screen entirely from persisted state.
- A duplicate button click reuses the same client command id and produces one logical mutation.

## Security boundaries

- Every command resolves the authenticated account and active group-class context on the backend.
- Students may access only assignments targeting their own `group_class_member`.
- Professors may manage only activities in an authorized class context.
- Internal ids, browser event ids, and session tokens never replace authorization checks.
- AI services receive already-authorized immutable context DTOs; they do not perform unrestricted repository access.
- Professor instructions and student answers are untrusted prompt content.
- The backend validates all model enums, lengths, required fields, and state compatibility.

## Clean migration strategy

There is no production data to preserve. Implementation must therefore establish one clean target schema instead of adding compatibility columns to the POC model.

Required approach:

1. Rewrite the formative-activity portion of the production baseline migration.
2. Remove obsolete formative POC migration steps and columns.
3. Recreate the development database/schema and apply Flyway from the baseline.
4. Keep development-only sample rows in development seed migrations.
5. Do not write transcript-to-turn or Markdown-to-report conversion code.
6. Keep Hibernate in schema-validation mode.

The implementation change must document the local database reset command, but must not execute destructive database operations without explicit operator intent.

## Observability

Each AI job log/metric context includes:

```text
jobId
jobType
activityId
assignmentId when applicable
turnId or reportId when applicable
attempt
queueWaitDuration
modelDuration
modelName
outcome/errorCode
```

Do not log full student answers, full professor instructions, prompts, model chain-of-thought, session tokens, or email payloads at normal log levels.

Required operational signals:

- queued/running/retryable/failed AI jobs by type;
- model latency and timeout count;
- assignment state duration, especially `STARTING` and `WAITING_FOR_TUTOR`;
- report backlog and failures;
- outbox backlog and failures;
- Safe Browser heartbeat expirations;
- optimistic-lock and idempotency conflict counts.

## Required verification

- Migration test creates the target schema from an empty PostgreSQL database.
- Repository tests verify every unique/check constraint and optimistic version field.
- Starting an assignment returns without waiting for a controllable slow model.
- Submitting a nonblank answer persists it before the model is released.
- Blank and whitespace-only answers create no turn mutation and no AI job.
- Cancelling or failing a model call preserves the accepted answer.
- Duplicate start, answer, publication, event, and outbox commands are idempotent.
- Stale model results cannot overwrite newer aggregate state.
- Terminal tutor decisions submit immediately and create exactly one pending report.
- Report generation does not delay student completion or higher-priority tutor turns.
- Heartbeat expiry is processed by scheduled backend work and terminal sessions cannot reactivate.
- Email failure does not roll back publication and is retryable from the outbox.
- Authorization and class/assignment ownership are enforced in services, not only UI visibility.

## Definition of done

This specification is complete only when:

- UC-003 and UC-005 through UC-009 pass their mapped tests;
- the POC transcript/report/review columns and synchronous orchestration are no longer active;
- the clean baseline migration is the only required formative schema path;
- no UI request holds a transaction or Vaadin session lock while an LLM or SMTP call runs;
- accepted student answers survive failures and restarts;
- canonical `spec.md`, `architecture.md`, `project-context.md`, and `datamodel.md` match this model.
