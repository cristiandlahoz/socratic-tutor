# UC-007: Durable Adaptive Student Tutor Runtime

---

**Goal:** As a student, I want to answer adaptive Socratic questions without the application freezing or losing my responses so that the evaluation gathers trustworthy evidence of my understanding.

**Status:** In Progress
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.
>
> **UC-009 dependency:** terminal submission now creates the durable pending report request and `FINAL_REPORT` job, but report content generation remains owned by UC-009. UC-007 therefore remains **In Progress**.
>
> **Migration baseline:** UC-007 changes are folded directly into Flyway `V1__baseline.sql`. Local development databases must be recreated from an empty PostgreSQL baseline; no compatibility migration is provided.
>
> **Test replacement boundary:** the pre-UC-007 reactive answer-stream tests were replaced only because the durable command/event flow removes that obsolete API. The focused replacement covers blank input, payload-safe idempotency, atomic enqueue, lease fencing, retry exhaustion, and expired-lease bounds.

---

## Actors

- **Primary actor:** Student
- **Secondary actors:** Durable tutor worker, configured tutor model, UC-005 Safe Browser session, UC-009 report workflow

---

## Preconditions

- Student is authenticated and owns an assignment created by UC-008.
- Parent activity is `PUBLISHED` and within its answerable window.
- Assignment is `ASSIGNED`, `STARTING`, `WAITING_FOR_ANSWER`, `WAITING_FOR_TUTOR`, or recoverable `TEMPORARILY_UNAVAILABLE`.
- If Safe Browser is required, UC-005 has established a current active session.
- Activity has immutable title and professor instructions.
- SPEC-005 turn, job, idempotency, optimistic-concurrency, and non-blocking orchestration are available.

---

## Trigger

The student opens `/training-activity/assignments/{assignmentId}`, starts an assigned evaluation, or submits a response to its current question.

---

## Main Flow

1. Student opens the assigned evaluation.
2. System validates authenticated ownership and loads persisted activity, assignment, Safe Browser, current turn, and job state.
3. If assignment is `ASSIGNED`, student clicks **Comenzar**.
4. System executes a short idempotent start command that changes assignment to `STARTING` and creates one `FIRST_QUESTION` job.
5. System returns immediately and renders **Preparando primera pregunta** without holding the Vaadin request/session thread for model generation.
6. Tutor worker claims the job in a short transaction and releases that transaction.
7. Worker builds immutable authorized context from activity title, instructions, assignment, relevant class context, and available grounding.
8. Worker calls the tutor model outside any domain transaction.
9. Backend validates the structured first-question decision.
10. In a new short transaction, system verifies the expected assignment/input version, stores turn 1, and changes assignment to `WAITING_FOR_ANSWER`.
11. UI receives or polls the persisted state and displays exactly one Spanish Socratic question.
12. Student writes a response.
13. UI enables submission only when normalized input contains at least one non-whitespace character.
14. Student submits the response with a stable `answerSubmissionId`.
15. Backend trims/normalizes for validation, verifies ownership, expected current turn, assignment state, activity window, and Safe Browser session when required.
16. In one short transaction, system persists the nonblank answer on the current turn, changes assignment to `WAITING_FOR_TUTOR`, and creates one `NEXT_DECISION` job.
17. System acknowledges that the answer was saved and renders **Analizando respuesta** immediately.
18. Tutor worker claims the job and calls the model outside any domain transaction using the immutable activity definition and complete ordered turn history.
19. Model classifies the latest response, evaluates evidence and instruction coverage, and returns a structured `QUESTION`, `COMPLETE_SUCCESS`, or `COMPLETE_INSUFFICIENT_EVIDENCE` decision.
20. Backend validates the decision against the job input, assignment state, allowed enums, and student-facing output rules.
21. If decision is `QUESTION`, system stores exactly one next turn and changes assignment to `WAITING_FOR_ANSWER` in a short optimistic transaction.
22. UI displays the next question and flow repeats from Main Flow step 12.
23. If decision is terminal, system records evidence/completion metadata and changes assignment to `SUBMITTED` in a short transaction.
24. In the same transaction, system creates exactly one `PENDING` report and corresponding `FINAL_REPORT` job for UC-009.
25. System immediately displays completion and navigates the student back to `/student`; it does not wait for report generation.
26. Student workspace shows the assignment as completed.

---

## Alternative Flows

### AF-1: Assignment is not owned or accessible

**Branches from:** Main Flow step 2, 4, or 15  
**Condition:** Assignment does not belong to the authenticated student's class membership or is outside authorized context.

1. System denies access without exposing assignment content.
2. No assignment, turn, or AI job changes.
3. Tutor model is not called.
4. Use case ends.

### AF-2: Assignment or activity is not answerable

**Branches from:** Main Flow step 2, 4, or 15  
**Condition:** Activity is closed/archived, assignment is submitted/expired/excused, or lifecycle state does not accept the command.

1. Backend rejects start or answer before mutation.
2. System shows completed, closed, expired, blocked, or no-access state as appropriate.
3. No new AI job is created.
4. Existing evidence remains unchanged.
5. Use case ends.

### AF-3: Safe Browser session is missing or terminal

**Branches from:** Main Flow step 2, 4, or 15  
**Condition:** Activity requires Safe Browser and no current active session exists.

1. Backend rejects start/answer before turn mutation or AI job creation.
2. System routes student to UC-005 entry or blocked state.
3. Existing answer input remains visible when safely possible.
4. Use case ends or resumes after a new valid session.

### AF-4: Blank or whitespace-only response

**Branches from:** Main Flow step 13 or 15  
**Condition:** Response is null, empty, or contains only whitespace after normalization.

1. UI prevents normal submission and shows **Escribe una respuesta antes de continuar**.
2. If a forged request reaches the backend, backend rejects it with a validation error.
3. No answer is persisted, assignment status does not change, and no AI job/model call occurs.
4. Student remains on the current question.
5. Returns to Main Flow step 12.

### AF-5: Meaningful but minimal response

**Branches from:** Main Flow step 19  
**Condition:** Nonblank response such as “no sé”, “no entiendo”, or another meaningful minimal answer provides little evidence.

1. System keeps the already accepted response as transcript evidence.
2. Model may classify it as `TOO_VAGUE` or equivalent low/no-evidence quality, but never as a transport-level blank.
3. Model asks one respectful clarification/refocus question when further evidence remains reasonable, or eventually completes with insufficient evidence.
4. Flow returns to Main Flow step 21 or follows terminal flow.

### AF-6: Absurd, spam, evasive, or off-topic response

**Branches from:** Main Flow step 19  
**Condition:** Accepted nonblank response is unrelated or non-evaluable.

1. System preserves the response as submitted evidence.
2. Model chooses a respectful `REFOCUS`, `REPHRASE`, or clarification move without treating it as positive evidence.
3. Repeated unproductive behavior may produce `COMPLETE_INSUFFICIENT_EVIDENCE`.
4. Flow continues from Main Flow step 21 or step 23.

### AF-7: Duplicate answer command

**Branches from:** Main Flow step 14 or 16  
**Condition:** Double-click, reconnect, or retry repeats the same `answerSubmissionId`.

1. Backend returns the previously accepted command result.
2. Exactly one answer and one semantic next-decision job exist.
3. Assignment does not advance twice.
4. UI refreshes persisted state.

### AF-8: Concurrent or stale answer command

**Branches from:** Main Flow step 15 or 16  
**Condition:** Command targets an old question/version or another answer already won the race.

1. Optimistic concurrency rejects the stale mutation.
2. System never overwrites an accepted answer.
3. UI reloads current persisted question/job state.
4. Use case ends or continues from current state.

### AF-9: Student navigates away while tutor is working

**Branches from:** Main Flow step 5, 17, or 18  
**Condition:** Browser disconnects, reloads, or navigates away.

1. Durable job and already accepted answer continue independently of the UI connection.
2. No correctness depends on an in-memory listener.
3. On return, system reconstructs `STARTING`, `WAITING_FOR_TUTOR`, `WAITING_FOR_ANSWER`, or `SUBMITTED` from persistence.
4. Student resumes without duplicate work.

### AF-10: Model timeout or temporary failure

**Branches from:** Main Flow step 8 or 18  
**Condition:** Model fails, times out, or worker lease expires.

1. Worker records a safe retryable error and releases/requeues work with bounded backoff.
2. Accepted student answer remains persisted.
3. Assignment remains resumable in `STARTING` or `WAITING_FOR_TUTOR` with a temporary-error UI state.
4. System does not silently substitute hardcoded production questions.
5. Student may leave and return while retry occurs.

### AF-11: Model output is invalid

**Branches from:** Main Flow step 9 or 20  
**Condition:** Output is malformed, has invalid enums, contains multiple questions, leaks hidden text, or violates terminal rules.

1. Backend rejects it and does not persist it as an authoritative question/decision.
2. Job follows bounded retry/failure policy.
3. Accepted answer and prior turns remain unchanged.
4. UI shows a safe temporary error when retry is not immediate.

### AF-12: Stale worker result

**Branches from:** Main Flow step 10 or 21  
**Condition:** Assignment/input version changed after worker captured its context.

1. Result-application transaction rejects the stale result.
2. System records stale completion for observability without mutating current turns.
3. Current assignment state remains authoritative.
4. Necessary current work is scheduled idempotently.

### AF-13: Strong evidence remains incomplete

**Branches from:** Main Flow step 19  
**Condition:** Latest answer is good/excellent but important instruction aspects remain uncovered.

1. Model records useful evidence and selects another uncovered aspect, deeper justification, example, or transfer case.
2. It does not repeat a mastered aspect without pedagogical reason.
3. Flow continues through Main Flow step 21.

### AF-14: Evidence is sufficient

**Branches from:** Main Flow step 19  
**Condition:** Ordered turns provide sufficient, varied, relevant evidence for a useful formative report.

1. Model returns `COMPLETE_SUCCESS` with validated coverage/evidence metadata and no student question.
2. Flow continues at Main Flow step 23.

### AF-15: Evidence remains insufficient or turn safety limit is reached

**Branches from:** Main Flow step 19  
**Condition:** Reasonable reconduction is exhausted or configured maximum turns are reached without sufficient evidence.

1. Model/backend policy returns `COMPLETE_INSUFFICIENT_EVIDENCE` with missing-aspect metadata.
2. Reaching the limit alone is not classified as successful learning evidence.
3. Student sees only normal completion, not internal evidence labels.
4. Flow continues at Main Flow step 23.

---

## Postconditions

- **On continued evaluation:** Ordered turns contain every accepted nonblank response; assignment waits either for the student or durable tutor work.
- **On completion:** Assignment is `SUBMITTED`, evidence metadata is stored, one pending report exists, and student is back at the workspace without waiting for report generation.
- **On model failure:** Accepted responses remain durable and retryable; no invalid/stale model result becomes authoritative.
- **On validation failure:** Blank, unauthorized, stale, blocked, or invalid-state commands produce no answer or model work.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Start and answer UI requests must return after short persistence work and must not wait for the tutor model. |
| BR-02 | No domain database transaction may remain open during a model call. |
| BR-03 | The Vaadin request/session thread must not perform synchronous LLM generation. |
| BR-04 | Null, empty, and whitespace-only answers are rejected in both UI and backend. |
| BR-05 | A rejected blank answer creates no transcript mutation, assignment transition, or AI job. |
| BR-06 | Meaningful nonblank responses such as “no sé” are accepted and evaluated as evidence quality, not rejected as blank. |
| BR-07 | Every accepted answer is committed before its next tutor decision is scheduled/called. |
| BR-08 | Accepted answers survive disconnect, cancellation, timeout, model failure, and application restart. |
| BR-09 | `answerSubmissionId` makes answer submission idempotent. |
| BR-10 | Assignment optimistic version and current turn prevent lost updates and stale answers. |
| BR-11 | Exactly one current unanswered question may exist for an assignment. |
| BR-12 | A continuation decision produces exactly one Spanish student-facing question. |
| BR-13 | Terminal decisions produce no next question and are internal; students see normal completion. |
| BR-14 | Tutor questions derive from immutable activity instructions, ordered turns, and authorized context. |
| BR-15 | Professor instructions and student responses are untrusted prompt content. |
| BR-16 | The tutor must not reveal system prompts, give the answer directly, or treat unsupported claims as evidence. |
| BR-17 | The tutor tracks answer quality, evidence strength, and coverage using backend-validated enums. |
| BR-18 | Successful completion requires sufficient, varied, relevant transcript evidence; it is not triggered only by a fixed question count. |
| BR-19 | Repeated non-evaluable answers may complete with internal insufficient-evidence metadata after reasonable reconduction. |
| BR-20 | A technical turn limit prevents infinite loops but does not imply success. |
| BR-21 | Production does not silently fall back to generic hardcoded questions after model failure. |
| BR-22 | Tutor jobs use bounded concurrency, finite deadlines, leases, and bounded retry. |
| BR-23 | Student tutor jobs outrank instruction-review and report jobs when model capacity is constrained. |
| BR-24 | UI state is reconstructable from persistence; an in-memory event bus may optimize refresh but is not authoritative. |
| BR-25 | A terminal decision submits the assignment and creates the report request atomically. |
| BR-26 | Student navigation after submission never waits for UC-009 report generation. |
| BR-27 | Hidden model chain-of-thought is never requested for persistence or stored; only validated decision fields are retained. |

### Validated tutor decision

The backend contract is equivalent to:

```json
{
  "type": "QUESTION | COMPLETE_SUCCESS | COMPLETE_INSUFFICIENT_EVIDENCE",
  "answerQuality": "ABSURD | OFF_TOPIC | TOO_VAGUE | PARTIALLY_CORRECT | GOOD | EXCELLENT",
  "evidenceStatus": "NO_EVIDENCE | WEAK_EVIDENCE | PARTIAL_EVIDENCE | STRONG_EVIDENCE",
  "coverageStatus": "NONE | WEAK | PARTIAL | SUFFICIENT",
  "pedagogicalMove": "REFOCUS | REPHRASE | ASK_FOR_CLARITY | ASK_FOR_EXAMPLE | ASK_FOR_JUSTIFICATION | PROBE_MISCONCEPTION | INCREASE_DIFFICULTY | MOVE_TO_NEXT_ASPECT | TRANSFER_TO_NEW_CASE | COMPLETE_SUCCESSFULLY | COMPLETE_WITH_INSUFFICIENT_EVIDENCE",
  "coveredInstructionAspects": ["..."],
  "missingInstructionAspects": ["..."],
  "questionText": "¿...?",
  "reasonCode": "..."
}
```

`answerQuality` may be absent for the first-question decision. `questionText` is required only for `QUESTION`. Human-readable hidden reasoning is not part of the contract.

---

## Tests

- [ ] Main Flow covered with a controllable slow model proving start/answer return before generation completes.
- [ ] AF-1 and AF-2 ownership/lifecycle covered without model calls.
- [ ] AF-3 Safe Browser enforcement covered.
- [ ] AF-4 blank/whitespace rejected in UI, service, and database constraints.
- [ ] AF-5 meaningful “no sé” accepted and evaluated.
- [ ] AF-6 non-evaluable response reconduction covered.
- [ ] AF-7 duplicate answer idempotency covered.
- [ ] AF-8 stale/concurrent answer optimistic conflict covered.
- [ ] AF-9 disconnect/reload recovery covered from persistence.
- [ ] AF-10 and AF-11 model failure/invalid output preserve answers.
- [ ] AF-12 stale result cannot overwrite current state.
- [ ] AF-13 through AF-15 adaptive continuation and both terminal decisions covered.
- [ ] Cancellation after accepted answer preserves turn and retryable job.
- [ ] Terminal decision submits and creates exactly one report without waiting for it.
- [ ] BR-01 through BR-27 covered.

---

## UI Surface

- Student assignment screen showing one persisted question at a time.
- Explicit states for start, preparing question, waiting for answer, analyzing, temporary failure/retry, Safe Browser blocked, and completed.
- Answer composer trims only for validation, disables blank submission, protects against duplicate clicks, and preserves unsent text on recoverable validation errors.
- Completion navigates to student workspace immediately after persisted submission.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Student assignment runtime | Authenticated owning student | `/training-activity/assignments/{assignmentId}` |
| Completed assignment row | Authenticated owning student | `/student` |
