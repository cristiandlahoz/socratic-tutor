# UC-008: Publish and Deliver a Training Activity

---

**Goal:** As a professor, I want to publish a draft activity once and reliably deliver one assignment to every eligible student so that it appears immediately in their workspace and notifications are sent without risking partial publication.

**Status:** In Progress
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor or another authorized training activity manager
- **Secondary actors:** Eligible students, student workspace, notification outbox worker, email provider, UC-006 instruction review

---

## Preconditions

- Professor is authenticated in the activity's active group-class context.
- Activity exists as `DRAFT` with a nonblank title and instructions.
- Activity definition and Safe Browser configuration are ready to become immutable.
- The class has at least one active, unlocked student membership eligible at publication time.
- SPEC-005 assignment uniqueness, outbox, transaction, and concurrency rules are available.

---

## Trigger

The professor clicks **Publicar actividad** from a draft activity detail or edit surface.

---

## Main Flow

1. Professor clicks **Publicar actividad**.
2. System shows the final activity definition, eligible student count, scheduling data, Safe Browser requirement, and current instruction-review verdict.
3. Professor confirms publication.
4. System validates permission, active class scope, expected activity version, `DRAFT` status, deterministic required fields, dates, and eligible students.
5. System validates the current instruction-review state through UC-006.
6. If the review is favorable, or professor has explicitly confirmed **Publicar de todos modos**, system begins one short publication transaction.
7. System creates exactly one `training_activity_assignment` with status `ASSIGNED` for every student membership eligible at that moment.
8. System changes the activity from `DRAFT` to `PUBLISHED`, records `published_at`, and increments its version.
9. System writes one `ACTIVITY_PUBLISHED` outbox event with a unique deduplication key in the same transaction.
10. System commits the transaction and returns the published state to the professor without calling SMTP.
11. Professor sees the immutable published detail and the newly assigned student rows.
12. Each assigned student sees the activity row in their workspace from the committed assignment data.
13. Outbox worker claims the publication event after commit.
14. Worker sends one logical notification email to each assigned student using idempotent recipient delivery records or keys.
15. Worker marks successful deliveries and retries transient failures according to policy.

---

## Alternative Flows

### AF-1: Permission, class scope, or version is invalid

**Branches from:** Main Flow step 4  
**Condition:** Professor cannot publish the activity, active context does not match, or the expected version is stale.

1. System rejects publication.
2. No assignments, status change, or outbox event are committed.
3. System displays no-access or refresh/reconcile guidance.
4. Use case ends.

### AF-2: Activity is not a valid draft

**Branches from:** Main Flow step 4  
**Condition:** Activity is already published, closed, archived, deleted, or has blank deterministic fields.

1. System rejects the transition.
2. If already published, system returns the current published state idempotently when the command identifies the same completed publication.
3. Otherwise system shows the specific validation/lifecycle error.
4. No duplicate assignments or notifications are created.
5. Use case ends.

### AF-3: Review recommends changes or is unavailable

**Branches from:** Main Flow step 5  
**Condition:** Current review is `NEEDS_IMPROVEMENT`, `INVALID`, missing, stale, pending, or failed.

1. System shows the advisory result or unavailable state.
2. Professor may cancel, edit/request review, or click **Publicar de todos modos**.
3. On explicit confirmation, system records a `PUBLISH` override for the exact instructions hash.
4. Returns to Main Flow step 6.

### AF-4: No eligible students

**Branches from:** Main Flow step 4  
**Condition:** The class has no active, unlocked student memberships at commit time.

1. System does not publish the activity.
2. No assignment or outbox event is created.
3. Activity remains `DRAFT`.
4. System explains that at least one eligible student is required.
5. Use case ends.

### AF-5: Eligible roster changes after confirmation

**Branches from:** Main Flow step 7  
**Condition:** Student membership eligibility changes between preview and publication transaction.

1. System uses the eligible roster observed and locked/validated by the publication transaction.
2. System displays the actual committed assignment count after publication.
3. Students added later are not silently assigned by this command.
4. A future explicit late-assignment use case is required to add them.

### AF-6: Duplicate publication request

**Branches from:** Main Flow step 6 or 7  
**Condition:** Double-click, retry, or concurrent request submits the same publication command.

1. Optimistic state transition and assignment uniqueness allow one publication to commit.
2. Duplicate request receives the already-published result or a safe conflict.
3. Exactly one assignment per activity/student and one logical outbox event remain.
4. Use case ends.

### AF-7: Publication transaction fails

**Branches from:** Main Flow step 7, 8, or 9  
**Condition:** Database error prevents any part of the atomic publication mutation.

1. System rolls back activity status, all new assignments, and the outbox event together.
2. Activity remains `DRAFT`.
3. No student workspace row or email is produced from partial state.
4. Professor sees a retryable error.
5. Use case ends.

### AF-8: Email provider is unavailable

**Branches from:** Main Flow step 14  
**Condition:** One or more email deliveries time out or fail transiently.

1. Published activity and assignments remain committed.
2. Student workspace rows remain available.
3. Outbox/delivery record is scheduled for bounded retry without duplicating successful logical deliveries.
4. Operational monitoring exposes the backlog/failure.
5. Professor publication request is not held open or rolled back.

> SMTP cannot provide exactly-once recipient delivery. The worker persists a `SENDING` boundary before calling SMTP. A crash or transport error after that boundary becomes an auditable `UNCERTAIN` delivery and is never retried automatically; an authorized professor must explicitly replay it. Only outcomes known to have failed before acceptance are retried automatically.

### AF-9: Notification permanently fails

**Branches from:** Main Flow step 15  
**Condition:** Retry policy is exhausted for a recipient.

1. System records a terminal delivery failure with safe metadata.
2. Assignment remains available in the student workspace.
3. Authorized operational action may retry the failed delivery later.
4. Publication and other recipients remain unaffected.

---

## Postconditions

- **On success:** Activity is immutable and `PUBLISHED`; exactly one assignment exists per eligible student at publication time; workspace rows are immediately queryable; one durable outbox event represents notification delivery.
- **On notification failure:** Publication and assignments remain valid; delivery is retryable and observable without duplicate logical emails.
- **On publication failure:** Activity remains a draft and no partial assignments or outbox events survive.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only an authorized professor in the activity's class context can publish it. |
| BR-02 | Only a `DRAFT` with nonblank deterministic fields may be published. |
| BR-03 | AI review is advisory; publication may continue only after explicit override when review is not currently favorable. |
| BR-04 | Publication, assignment creation, activity transition, and outbox insertion are one atomic transaction. |
| BR-05 | `(training_activity_id, group_class_member_id)` is unique and publication is idempotent. |
| BR-06 | Eligible students are active, unlocked student memberships in the activity's group class at publication commit time. |
| BR-07 | Publication requires at least one eligible student. |
| BR-08 | Later class membership does not silently change the publication snapshot. |
| BR-09 | Student workspace visibility comes from committed assignment rows, not an in-memory notification. |
| BR-10 | Email is sent after commit through a durable outbox and never inside the professor's publication transaction. |
| BR-11 | Email failure never removes or hides an assignment. |
| BR-12 | Notification retries are idempotent per publication and recipient. |
| BR-13 | Published title, instructions, Safe Browser setting, and scheduling definition are immutable. |
| BR-14 | Publishing one activity does not globally prevent the same professor from publishing another valid activity. |

---

## Pending Dependencies and Partial Verification

- **Unresolved acceptance:** The Main Flow, AF-1 through AF-9, and BR-01 through BR-14 checklists remain open. Existing mapped focused tests cover service-level publication/outbox paths, expired lease recovery, bounded recovery queries, uncertain SMTP outcomes, runtime transport uncertainty, and authorized manual replay; they do not yet prove end-to-end publication UI, roster concurrency, worker contention, or full recipient delivery/replay acceptance.
- **Future use-case dependency:** UC-003 provides the draft lifecycle that UC-008 publishes; UC-005 consumes the immutable Safe Browser configuration and assignments; UC-007 consumes committed assignments in the student runtime; UC-009 follows UC-007 submission to generate reports. These successors are not implemented by publication.
- **Implemented and verified so far:** The working tree adds draft-to-published assignment/outbox persistence, student workspace refresh, recipient delivery states, bounded retry, uncertain-SMTP handling, and manual replay. Focused automated verification covers service paths, uncertain SMTP, and replay only; this is partial coverage.

---

## Tests

- [ ] Main Flow covered from publication preview through assignments, dashboard visibility, and outbox delivery.
- [ ] AF-1 authorization/scope/version covered.
- [ ] AF-2 invalid lifecycle and deterministic fields covered.
- [ ] AF-3 advisory publication override covered.
- [ ] AF-4 no-eligible-student rollback covered.
- [ ] AF-5 roster snapshot covered.
- [ ] AF-6 concurrent/duplicate publication covered.
- [ ] AF-7 atomic transaction rollback covered.
- [ ] AF-8 and AF-9 transient/permanent email failures covered.
- [ ] Worker claim contention, expired leases, uncertain SMTP acceptance, bounded retry, terminal replay, and event settlement covered.
- [ ] BR-01 through BR-14 covered.

---

## UI Surface

- Publication confirmation showing immutable definition, actual eligible count, scheduling, Safe Browser setting, and instruction-review state.
- Published activity detail showing committed assignment rows.
- Student workspace showing assigned/open activity rows directly from persistence.
- Nonblocking notification status is operational metadata and does not delay the professor screen.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Publication confirmation | Authenticated authorized professor | Draft detail at `/training-activities` |
| Published assignment list | Authenticated authorized professor/reviewer | Published activity detail |
| Assigned activity row | Authenticated owning student | `/student` |
