# UC-005: Safe Browser Session

---

**Goal:** As a professor, I want optional Safe Browser monitoring for an activity so that detectable loss of the controlled evaluation context blocks only the affected student's attempt and leaves an auditable incident I can review.

**Status:** In Progress
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Student assigned to a Safe Browser activity
- **Secondary actors:** Professor or authorized activity reviewer, browser monitoring runtime, scheduled heartbeat-expiry worker

---

## Preconditions

- Professor enabled Safe Browser while the activity was `DRAFT`.
- UC-008 published the immutable activity and created the student's assignment.
- Student is authenticated and owns the assignment.
- Activity and assignment are answerable.
- Browser supports the required fullscreen, visibility, focus, and heartbeat capabilities.
- SPEC-005 Safe Browser session/event persistence exists.

---

## Trigger

The student opens a Safe Browser-required assignment and chooses **Iniciar evaluación protegida**.

---

## Main Flow

1. Student opens the assigned activity.
2. System validates assignment ownership, activity state, assignment state, and Safe Browser requirement.
3. System displays the monitoring rules, browser requirements, detectable violations, and limitation that this mode cannot guarantee control of the operating system.
4. Student accepts the rules and clicks **Iniciar evaluación protegida**.
5. Browser requests fullscreen and initializes visibility, focus, unload, and heartbeat monitoring.
6. Backend creates one `PENDING` Safe Browser session, returns an opaque session token, and stores only its hash.
7. Browser sends the initial heartbeat with the assignment id and session token.
8. Backend validates authenticated ownership, token, session state, and assignment answerability, then activates the session.
9. System starts or resumes the tutor flow through UC-007 without waiting synchronously for a model response.
10. Browser sends heartbeat events at the configured interval while the assignment remains active.
11. Backend updates only the current active session heartbeat using a short, idempotent operation.
12. Student answers tutor questions while every answer command verifies that the required Safe Browser session remains active.
13. Browser detects a violation such as fullscreen exit, hidden tab, focus loss, or unload.
14. Browser sends an idempotent violation event with a unique client event id and current session token.
15. Backend validates ownership, token, active session, and nonterminal assignment state.
16. Backend atomically appends the violation event and changes the Safe Browser session to `VIOLATED`.
17. Subsequent start/answer commands for that assignment are rejected server-side.
18. Student sees a blocked state explaining that the professor must review the incident.
19. Professor opens the published activity detail.
20. System derives and shows incident counts and affected assignments from Safe Browser session/event data.
21. Professor opens the affected student's incident history.
22. System displays violation type, received time, session status, and assignment status without exposing secrets.
23. Professor chooses **Permitir nuevo intento de sesión** for that assignment.
24. System validates professor permission and current activity/assignment state.
25. System records a `MANUAL_UNLOCK` audit event and ends any remaining nonterminal session state.
26. Student may reopen the assignment and create a new Safe Browser session if the activity and assignment remain answerable.
27. Original sessions and events remain immutable.

---

## Alternative Flows

### AF-1: Unauthorized or wrong assignment

**Branches from:** Main Flow step 2, 8, 15, or 24  
**Condition:** Student does not own the assignment, professor cannot manage it, token is invalid, or class scope does not match.

1. Backend rejects the operation.
2. No session activation, heartbeat, violation, or unlock mutation is applied to the target assignment.
3. System shows a no-access/not-found state without leaking restricted details.
4. Use case ends.

### AF-2: Activity or assignment is not answerable

**Branches from:** Main Flow step 2, 8, 12, or 24  
**Condition:** Activity is closed/archived, assignment is submitted/expired/excused, or another domain rule prevents answers.

1. System does not create or activate a protected session.
2. Any active session is ended when appropriate.
3. Student cannot start or submit more answers.
4. Existing turns, reports, sessions, and events remain unchanged.
5. Use case ends.

### AF-3: Browser is unsupported or student refuses entry

**Branches from:** Main Flow step 4 or 5  
**Condition:** Required browser APIs are unavailable, fullscreen is denied, or student cancels.

1. System does not activate a Safe Browser session.
2. No violation is recorded because protected evaluation never started.
3. Student sees requirements and may retry with a supported environment.
4. Use case ends or returns to Main Flow step 3.

### AF-4: Initial heartbeat is not established

**Branches from:** Main Flow step 7 or 8  
**Condition:** First heartbeat does not arrive before the setup deadline.

1. Scheduled worker expires the `PENDING` session as a setup failure.
2. System does not record `HEARTBEAT_LOST` as a violation.
3. Student cannot enter UC-007 and may request a new setup session.
4. Use case ends or returns to Main Flow step 3.

### AF-5: Concurrent session request

**Branches from:** Main Flow step 6  
**Condition:** An active or pending session already exists for the assignment.

1. Backend rejects or idempotently returns the same session for the same start command.
2. It never creates two active sessions.
3. Student sees the current session state.
4. Use case ends or continues at Main Flow step 7.

### AF-6: Heartbeat is lost after activation

**Branches from:** Main Flow step 10 or 11  
**Condition:** Backend has not received heartbeat within the configured active-session deadline.

1. Scheduled backend worker claims the expired active session.
2. Worker atomically appends one idempotent `HEARTBEAT_LOST` event and changes session to `EXPIRED`.
3. Student answer/start commands are rejected on the next interaction.
4. Professor incident view includes the timeout.
5. Flow continues at Main Flow step 18.

### AF-7: Duplicate or late violation event

**Branches from:** Main Flow step 14 or 15  
**Condition:** Client retries an event id or event arrives after session/assignment became terminal.

1. Unique client event id makes an exact duplicate idempotent.
2. A late event may be retained as audit metadata only when policy permits, but cannot reactivate or reopen anything.
3. No duplicate logical incident or state transition is created.
4. Student sees the current persisted state.

### AF-8: Heartbeat arrives after terminal session

**Branches from:** Main Flow step 11  
**Condition:** Session is `VIOLATED`, `EXPIRED`, or `ENDED`.

1. Backend rejects the heartbeat as stale.
2. Session remains terminal.
3. Student must obtain professor permission when required and create a new session.
4. No automatic reactivation occurs.

### AF-9: Client-side blocked state is bypassed

**Branches from:** Main Flow step 17  
**Condition:** Student modifies the browser or directly invokes an answer command.

1. Backend revalidates the latest session and assignment state.
2. Backend rejects the answer before mutating a turn or creating an AI job.
3. UI refreshes the persisted blocked state.
4. Use case ends.

### AF-10: Professor keeps assignment blocked

**Branches from:** Main Flow step 23  
**Condition:** Professor closes the incident without allowing a new session.

1. Terminal session and event history remain unchanged.
2. Student remains unable to continue a Safe Browser-required assignment.
3. Professor may review again later while activity remains open.

### AF-11: Unlock requested for submitted or closed work

**Branches from:** Main Flow step 24  
**Condition:** Assignment is submitted or parent activity is no longer answerable.

1. System may record that the incident was reviewed but does not reopen the assignment.
2. Existing report and turns remain immutable.
3. System explains the independent lifecycle restriction.
4. Use case ends.

### AF-12: Violation transaction fails

**Branches from:** Main Flow step 16  
**Condition:** Event and terminal session transition cannot commit together.

1. System rolls back both mutations.
2. Client receives a retryable error and stops local answer submission until server state is known.
3. Duplicate retry remains safe through client event id.
4. No silent lock exists without an auditable event.

---

## Postconditions

- **On valid session:** Exactly one protected session is active and answer commands can proceed while heartbeat and assignment rules remain valid.
- **On violation/expiry:** One auditable event and terminal session state block only the affected assignment; other students are unaffected.
- **On professor allowance:** A manual audit event exists and the student may create a new session; the old session is never reactivated or deleted.
- **On failure:** Unauthorized, duplicate, stale, or partial operations do not corrupt assignment or incident history.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Safe Browser is optional per activity and configurable only while the activity is `DRAFT`. |
| BR-02 | Published Safe Browser configuration is immutable. |
| BR-03 | Safe Browser detects supported signals; it does not claim to prevent every tab, window, process, shutdown, or operating-system action. |
| BR-04 | Only one `PENDING` or `ACTIVE` session may exist per assignment. |
| BR-05 | Session tokens are opaque, stored hashed, and validated with authenticated assignment ownership. |
| BR-06 | A Safe Browser-required assignment accepts answers only with its current `ACTIVE` session. |
| BR-07 | Browser-side controls are UX and detection; backend checks are authoritative. |
| BR-08 | Setup failure before activation is not a violation. |
| BR-09 | Active heartbeat timeout creates one `HEARTBEAT_LOST` incident through scheduled backend work. |
| BR-10 | Heartbeat interval and expiry threshold are centrally configured; expiry must exceed the expected interval with tolerance. |
| BR-11 | Heartbeat requests are short, idempotent, and never call the tutor model. |
| BR-12 | `VIOLATED`, `EXPIRED`, and `ENDED` sessions are terminal and cannot reactivate. |
| BR-13 | Unlock permits a new session; it never mutates the terminal old session back to active. |
| BR-13A | After an activated session becomes `VIOLATED` or `EXPIRED`, a new session requires a later authorized professor allowance event. |
| BR-14 | Violations affect only the associated assignment and never the whole activity, membership, or other students. |
| BR-15 | Violation and manual action history is append-only. |
| BR-16 | Duplicate client events are idempotent by session and client event id. |
| BR-17 | Closing the parent activity or submitting the assignment independently prevents continuation. |
| BR-18 | Professor incident access and allowance actions are permission-checked server-side. |
| BR-19 | Incident summaries may be derived from sessions/events; a duplicate mutable grouped-alert source of truth is not required. |
| BR-20 | Safe Browser event metadata must not contain session tokens, prompts, or unrelated browsing data. |

---

## Tests

- [ ] Main Flow covered from protected setup through violation, professor review, and new session allowance.
- [ ] AF-1 authorization, token, and class scope covered.
- [ ] AF-2 independent lifecycle blocking covered.
- [ ] AF-3 and AF-4 setup failures produce no violation.
- [ ] AF-5 active-session uniqueness covered.
- [ ] AF-6 scheduled heartbeat expiry covered.
- [ ] AF-7 duplicate/late event idempotency covered.
- [ ] AF-8 terminal heartbeat cannot reactivate session.
- [ ] AF-9 backend answer enforcement covered.
- [ ] AF-10 and AF-11 professor decisions/lifecycle covered.
- [ ] AF-12 atomic event/session transition covered.
- [ ] BR-01 through BR-20 covered.

## Pending Dependencies and Partial Verification

- **Implemented and automatedly covered in this checkpoint:** `safe_browser_session` now persists one `PENDING`/`ACTIVE` session per assignment with an opaque SHA-256-hashed token, optimistic version, terminal state, heartbeat time, and timestamps. Browser RPCs carry the issued token and a unique client-event id; backend ownership, token, state, and duplicate checks remain authoritative. `PENDING` setup sessions expire without a violation, while expired `ACTIVE` sessions append one `HEARTBEAT_LOST` incident and block only the affected assignment. Heartbeat thresholds and the expiry poll are centralized under `app.safe-browser`. The existing entry flow monitors fullscreen/visibility/focus/unload, professor review/unlock remains append-only, and copy explicitly explains the browser/operating-system limitation.
- **UC-007 dependency (explicitly deferred):** UC-007 owns the durable tutor command and turn/job state machine. Its future start/answer commands must validate the current Safe Browser session before mutating a turn or creating an AI job (Main Flow steps 9 and 12; AF-9; BR-06 and BR-07). This UC-005 correction does not implement that durable enforcement. The current legacy synchronous evaluator has a compatibility guard, but it is not evidence for UC-007's durable-runtime acceptance.
- **Remaining UC-005 work:** The acceptance checklist remains open until migration-level and browser-route verification can be run against a clean database. UC-005 remains **In Progress** because UC-007 owns durable tutor-command enforcement; no unchecked acceptance checklist item is claimed as complete.
- **Manual verification blocker:** The local app cannot start for visual route review because the dev PostgreSQL Flyway history has V1 checksum `-322862748`, while the current cumulative V1 baseline resolves to `-1099346283`. Repairing or resetting that shared database is an explicit operator action and was not performed by this UC-005 checkpoint.

---

## UI Surface

- Professor draft setting explaining Safe Browser capabilities and limitations.
- Student entry instructions, compatibility result, fullscreen action, and setup progress.
- Student blocked state after violation/heartbeat expiry.
- Professor incident summary and assignment-specific immutable history with **Permitir nuevo intento de sesión**.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Safe Browser configuration | Authenticated authorized professor | Draft editor at `/training-activities` |
| Protected assignment entry | Authenticated owning student | `/training-activity/assignments/{assignmentId}` |
| Incident review | Authenticated authorized professor/reviewer | Published activity detail |
