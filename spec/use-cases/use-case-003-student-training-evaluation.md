# Use Case 003: Student Training Evaluation

**Status:** Verified
**Depends on:** SPEC-005 and UC-005 through UC-009

## Goal

Allow professors to launch a formative training activity for students, let students complete the assigned evaluation flow, and make the resulting report available to the professor.

## Actors

- Professor
- Student

## Main Flow

1. A professor creates or edits a draft formative activity in the active class context and may request advisory instruction review.
2. The professor explicitly saves or publishes despite an unfavorable, unavailable, or stale review only through the applicable override flow.
3. The professor publishes the draft once; the system atomically creates one assignment per eligible unlocked student and a durable notification outbox event.
4. A student opens the owned assignment from the student workspace at `/training-activity/assignments/{assignmentId}`.
5. The backend verifies the student's ownership, active class scope, assignment/activity lifecycle, configured time window, and any required Safe Browser session before accepting a command.
6. The student starts and answers guided evaluation questions through short durable commands. `training_activity_ai_job` performs `FIRST_QUESTION` and `NEXT_DECISION` work without blocking the Vaadin request.
7. The ordered `training_activity_turn` rows remain the authoritative transcript. A terminal tutor decision submits the assignment and atomically creates one normalized `training_activity_report` request and `FINAL_REPORT` job.
8. The student returns to the workspace immediately after persisted submission. The professor opens the activity detail and reviews the normalized report projection together with canonical ordered turns.

## Acceptance Criteria

- [x] Draft activities can be published only once. Publication creates assignments only for eligible unlocked student class members and writes notification work through the outbox.
- [x] Student workspace actions open only the owned assignment route. Backend ownership, group-class, lifecycle, Safe Browser, and time-window checks remain authoritative.
- [x] `training_activity_assignment` owns delivery/runtime state; `training_activity_turn` is the authoritative transcript; `training_activity_report` stores only normalized structured report content; and `training_activity_ai_job` owns durable LLM work.
- [x] `safe_browser_session` holds protected-session state and `safe_browser_event` remains append-only incident/audit fact history. Optional Safe Browser checks block only the affected assignment.
- [x] Blank answers are rejected in the UI, service, and PostgreSQL constraints. A stable `answerSubmissionId` makes an accepted answer idempotent without accepting a conflicting payload.
- [x] Start, answer, completion, retry, and reload/recovery states are reconstructable from PostgreSQL. No Vaadin request waits for a model or report result.
- [x] Students navigate to the workspace immediately after `SUBMITTED`; professors can see assignment status and review pending, generating, ready, or failed normalized report projections with canonical turns.

## Verification Status

All seven acceptance checks are reconciled as verified.

### Evidence

- Playwright desktop and mobile walkthroughs covered the professor and student main flow, including the Safe Browser lock state and professor report projections.
- A real Lightning interactive tutor session completed. Fresh final reports exercised the failure/retry path without stale-result or pending-report stranding; an existing real Lightning `READY` report confirmed normalized findings and canonical ordered turns.
- Focused tests passed (6 + 4), UC-003 dependency regressions passed (120), the full suite passed (133), the production build passed, and an empty PostgreSQL baseline passed.
- Runtime verification finished with zero final errors.

### Review reconciliation

The frozen 4R review found one corroborated resilience issue. It was fixed with focused cancellation/interrupt tests and the scoped outcome is approved. RELIABILITY-001 and RELIABILITY-002 were refuted; RELIABILITY-003 was informational only.

Native review receipt: **NOT AVAILABLE — Gentle-AI 1.48.0 lifecycle defect.** This unavailable receipt is documented as an exception and is not a native approval.
