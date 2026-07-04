# UC-005: Safe Browser Mode

---

**Goal:** As a professor, I want to enable Safe Browser Mode for a training activity so that when a student leaves the controlled evaluation context, the system detects it, locks only that student’s `training_activity_assignment`, and preserves the integrity of the formative evaluation.

**Status:** Implemented
**Date:** 2026-07-03

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor
- **Secondary actors:** Student, browser/client runtime, backend safe-browser service

---

## Preconditions

- The professor is authenticated.
- The professor has an active group-class context where they can manage training activities.
- UC-003 Student Training Evaluation exists as the base flow for creating, launching, assigning, and completing training activities.
- A `training_activity` exists in the active class, or the professor is creating it through the normal training activity flow.
- The class has active/unlocked student members that can receive assignments when the activity is launched.
- Safe Browser Mode applies only when the `training_activity` was configured with Safe Browser Mode enabled before the student executes it.
- To detect and block a violation, an individual `training_activity_assignment` must exist for the affected student.
- The student opens the evaluation from a valid assignment route.
- The browser/client runtime can execute the required client-side detection logic; unsupported browsers follow an alternative flow.

---

## Trigger

The professor enables Safe Browser Mode on a `training_activity` before launching it to students.

---

## Main Flow

> This use case extends UC-003 Student Training Evaluation. It does not replace UC-003; the student still completes the normal assigned training evaluation unless Safe Browser Mode is violated.

1. Professor opens the training activity creation or edit screen in the active class context.
2. System shows the training activity form with a Safe Browser Mode option.
3. Professor enables Safe Browser Mode and saves the training activity.
4. System persists the `training_activity` with Safe Browser Mode enabled.
5. Professor launches the training activity through the normal UC-003 launch flow.
6. System creates one `training_activity_assignment` per active/unlocked student in the class.
7. Student opens their assigned training activity from the student workspace.
8. System loads the student’s `training_activity_assignment` and detects that its parent `training_activity` requires Safe Browser Mode.
9. System shows the student the Safe Browser Mode entry instructions.
10. Student starts the protected evaluation session.
11. Browser/client runtime enters the controlled mode and starts sending heartbeat events.
12. System creates or marks an active Safe Browser session for that student’s `training_activity_assignment`.
13. Student starts answering the normal guided evaluation questions.
14. System stores the student’s progress normally while Safe Browser Mode remains valid.
15. Browser/client runtime detects a Safe Browser violation, such as fullscreen exit, tab hidden, window blur, before unload, or heartbeat lost.
16. Browser/client runtime reports the violation to the backend with the current `training_activity_assignment` id and event type.
17. Backend safe-browser service validates that the reported assignment belongs to the current student.
18. Backend safe-browser service stores a Safe Browser event for audit/tracing.
19. Backend safe-browser service locks only that student’s `training_activity_assignment`.
20. Backend safe-browser service creates or updates one grouped teacher alert for the professor in charge of the `training_activity`.
21. System blocks the student from continuing or submitting more answers for that assignment.
22. Student sees a blocked state explaining that Safe Browser Mode was interrupted.
23. Professor opens the training activity detail/review area.
24. System shows a grouped Safe Browser incident alert for that training activity.
25. Professor opens the grouped alert.
26. System shows the affected student assignment, violation type, timestamp, and locked status.
27. Professor selects the affected student assignment from the grouped incident detail.
28. Professor clicks “Unlock assignment”.
29. System validates that the professor is allowed to manage that training activity.
30. System unlocks only the selected student’s `training_activity_assignment`.
31. System records a manual unlock event for audit/tracing.
32. System preserves the original Safe Browser violation event history unchanged.
33. Student can reopen or continue the assignment according to the existing evaluation rules if the parent `training_activity` remains open and the assignment is not submitted.

---

## Alternative Flows

### AF-1: Professor has no permission to manage the activity

**Branches from:** Main Flow step 1 or 3
**Condition:** The current user is not allowed to create, update, or launch training activities in the active class context.

1. System denies access to the training activity management action.
2. System shows a permission/no-access message.
3. No activity changes are saved and no assignments are created.
4. Use case ends.

### AF-2: No active class context

**Branches from:** Main Flow step 1
**Condition:** Professor is authenticated but has no valid active group-class context selected.

1. System redirects the professor to context selection or no-access state.
2. Professor must select a valid class context before managing training activities.
3. No Safe Browser configuration is saved.
4. Use case ends or returns to Main Flow step 1 after context selection.

### AF-3: Invalid activity data

**Branches from:** Main Flow step 3
**Condition:** Professor saves the activity with missing or invalid required fields, such as blank title or blank instructions.

1. System validates the form.
2. System shows validation errors.
3. Professor corrects the fields.
4. Returns to Main Flow step 3.

### AF-4: Safe Browser Mode cannot be changed after launch

**Branches from:** Main Flow step 3
**Condition:** Professor tries to enable or disable Safe Browser Mode after the activity has already been launched/published.

1. System rejects the change.
2. System explains that Safe Browser Mode must be configured before launch.
3. Existing assignments remain unchanged.
4. Use case ends or returns to the activity detail screen.

### AF-5: Activity is not in draft state when launch is requested

**Branches from:** Main Flow step 5
**Condition:** Professor tries to launch an activity that is not in `DRAFT` status.

1. System rejects the launch.
2. System explains that only draft training activities can be launched.
3. No new `training_activity_assignment` rows are created.
4. Use case ends.

### AF-6: Professor already has another active launched activity

**Branches from:** Main Flow step 5
**Condition:** Professor tries to launch a `training_activity`, but the same professor already has another active launched/open `training_activity`, even if it belongs to another group.

1. System rejects the launch.
2. System explains that a professor can evaluate only one group/activity at a time.
3. No new `training_activity_assignment` rows are created.
4. The current activity remains saved as draft.
5. Returns to the activity management screen.

### AF-7: No active/unlocked students in the class

**Branches from:** Main Flow step 6
**Condition:** Professor launches the activity, but the active class has no eligible unlocked student members.

1. System does not create assignments.
2. System informs the professor that there are no eligible students to assign.
3. Activity remains unlaunched/draft because no assignments were created.
4. Use case ends or returns to the activity management screen.

### AF-8: Duplicate assignment creation is attempted

**Branches from:** Main Flow step 6
**Condition:** The system tries to create an assignment for a student who already has one for the same activity.

1. System prevents duplicate assignment creation using the existing unique rule for activity plus student member.
2. Existing assignment is preserved.
3. System continues creating assignments for other eligible students if possible.
4. Flow continues to Main Flow step 7.

### AF-9: Launch transaction fails

**Branches from:** Main Flow step 6
**Condition:** Database error or unexpected backend failure occurs while launching the activity or creating assignments.

1. System rolls back the launch transaction.
2. No partial assignments remain.
3. System shows an error message to the professor.
4. Activity remains in its previous state.
5. Use case ends or professor retries later.

### AF-10: Professor stops the active training activity because class time is over

**Branches from:** After Main Flow step 6 while students are working
**Condition:** Professor decides the evaluation window is over and students should not continue later.

1. Professor opens the active training activity detail/review area.
2. Professor clicks “Stop activity” or “Close activity”.
3. System asks for confirmation.
4. Professor confirms the action.
5. System validates that the professor is allowed to manage that training activity.
6. System sets the `training_activity` status to `CLOSED` and sets `closes_at` to the current time.
7. System prevents all non-submitted assignments under that activity from starting, continuing, or submitting answers.
8. System keeps already submitted assignments and final reports available for professor review.
9. System records the close action for traceability.
10. The professor can launch another training activity because there is no longer an active/open launched activity.
11. Use case ends.

### AF-11: Student assignment does not exist or does not belong to the student

**Branches from:** Main Flow step 7 or 8
**Condition:** Student opens an invalid assignment route, or the `training_activity_assignment` belongs to another student.

1. System validates the assignment id against the current student context.
2. System denies access to the assignment.
3. System shows a not-found or no-access message.
4. No Safe Browser session is created.
5. No evaluation progress is changed.
6. Use case ends.

### AF-12: Parent training activity is already closed

**Branches from:** Main Flow step 8
**Condition:** Student opens an assignment whose parent `training_activity` has status `CLOSED`.

1. System detects that the training activity is closed.
2. System prevents the student from starting or continuing the assignment.
3. System shows a message explaining that the evaluation window has ended.
4. No Safe Browser session is created or continued.
5. Existing transcript/report data remains unchanged.
6. Use case ends.

### AF-13: Assignment is already submitted

**Branches from:** Main Flow step 8 or 13
**Condition:** Student opens an assignment that has already been submitted.

1. System loads the submitted assignment.
2. System does not start a new Safe Browser session.
3. System shows the completed/submitted state.
4. Student cannot modify previous answers.
5. Use case ends.

### AF-14: Assignment is already Safe Browser locked

**Branches from:** Main Flow step 8 or 13
**Condition:** Student opens an assignment that was previously locked because Safe Browser Mode was interrupted.

1. System detects that `training_activity_assignment.safe_browser_locked` is true.
2. System prevents the student from starting, continuing, or submitting answers.
3. System shows a blocked state explaining that Safe Browser Mode was interrupted.
4. Student is told that the professor must review or unlock the assignment.
5. No transcript, question, or report data is changed.
6. Use case ends.

### AF-15: Browser does not support required Safe Browser features

**Branches from:** Main Flow step 9 or 10
**Condition:** The browser/client runtime cannot support required detection behavior, such as fullscreen handling, visibility detection, or heartbeat execution.

1. System detects that the browser is not compatible with Safe Browser Mode.
2. System shows a message explaining that the student must use a supported browser/environment.
3. System prevents the protected evaluation from starting.
4. No Safe Browser session is created.
5. No evaluation progress is changed.
6. Use case ends.

### AF-16: Student refuses or cancels Safe Browser entry

**Branches from:** Main Flow step 10
**Condition:** Student does not click the required start/enter-safe-mode action, denies fullscreen, exits before starting, or cancels the Safe Browser entry screen.

1. System keeps the assignment available but not started.
2. System does not start the protected evaluation session.
3. System shows instructions that Safe Browser Mode is required to continue.
4. Student may retry entering Safe Browser Mode.
5. Returns to Main Flow step 9.

### AF-17: Safe Browser session cannot be created

**Branches from:** Main Flow step 12
**Condition:** Backend fails to create or mark the active Safe Browser session because of a database error or unexpected service failure.

1. System does not allow the student to begin answering.
2. System shows an error message.
3. No evaluation progress is changed.
4. Student may retry later.
5. Use case ends or returns to Main Flow step 10.

### AF-18: Heartbeat cannot start or first heartbeat is not received

**Branches from:** Main Flow step 11 or 12
**Condition:** The controlled session starts, but the browser/client runtime cannot send heartbeat, or the backend does not receive the initial heartbeat.

1. System treats the session as not safely established.
2. System prevents the student from continuing into the evaluation questions.
3. System shows a message that Safe Browser Mode could not be verified.
4. No violation lock is created because the evaluation never actually started.
5. Student may retry entering Safe Browser Mode.
6. Returns to Main Flow step 9.

### AF-19: Student tries to answer before Safe Browser Mode is active

**Branches from:** Main Flow step 13
**Condition:** Student submits an answer before the backend has confirmed an active Safe Browser session for an activity that requires Safe Browser Mode.

1. System rejects the answer.
2. System does not update transcript, current question, question count, or final report.
3. System shows a message that Safe Browser Mode must be active before answering.
4. Student must start the protected session.
5. Returns to Main Flow step 9.

### AF-20: Answer submission fails while Safe Browser Mode is valid

**Branches from:** Main Flow step 14
**Condition:** Student submits an answer, but the backend fails to persist the answer due to a database error, network issue, or unexpected service error.

1. System does not advance the evaluation question.
2. System does not mark the assignment as submitted.
3. System shows an error message to the student.
4. Student may retry the answer submission while the Safe Browser session remains valid.
5. Returns to Main Flow step 13.

### AF-21: Concurrent Safe Browser session is detected

**Branches from:** Main Flow step 10 or 12
**Condition:** The same student assignment already has an active Safe Browser session, for example because the student opened the assignment in another tab or device.

1. System detects an existing active session for the same `training_activity_assignment`.
2. System rejects the new session or expires the older session according to the implementation rule.
3. System shows a message explaining that only one active Safe Browser session is allowed per assignment.
4. No duplicate evaluation session is allowed.
5. Use case ends or returns to Main Flow step 9.

### AF-22: Violation report is duplicated

**Branches from:** Main Flow step 16
**Condition:** Browser/client runtime reports the same violation more than once, for example multiple `visibilitychange`, `blur`, or `fullscreenchange` events fire close together.

1. Backend receives the repeated violation report.
2. Backend validates the assignment and student context.
3. Backend stores the event if useful for audit, or deduplicates it according to implementation rules.
4. Backend does not create duplicate open teacher alerts for the same teacher/activity.
5. Backend leaves the assignment locked if it was already locked.
6. Flow continues to Main Flow step 21.

### AF-23: Violation report arrives after assignment is already locked

**Branches from:** Main Flow step 17 or 19
**Condition:** The assignment was already locked by a previous Safe Browser violation.

1. Backend detects that `training_activity_assignment.safe_browser_locked` is already true.
2. Backend may record the additional event for audit.
3. Backend does not change the original locked timestamp unless the implementation explicitly tracks latest violation separately.
4. Backend does not create a duplicate grouped teacher alert.
5. Student remains blocked.
6. Flow continues to Main Flow step 21.

### AF-24: Violation report does not belong to the current student

**Branches from:** Main Flow step 17
**Condition:** The browser/client reports an assignment id that does not belong to the authenticated/current student.

1. Backend rejects the violation report.
2. Backend does not lock the reported assignment.
3. Backend does not create a Safe Browser event for that assignment.
4. Backend may record a security/audit warning if the project has a general audit mechanism.
5. System returns an access denied or invalid assignment response.
6. Use case ends for that report.

### AF-25: Violation report arrives after assignment was submitted

**Branches from:** Main Flow step 17 or 19
**Condition:** Student had already submitted the assignment before the violation report reached the backend.

1. Backend detects that the assignment status is already `SUBMITTED`.
2. Backend does not change the assignment back to locked.
3. Backend may record the late event for audit with a note that the assignment was already submitted.
4. Backend does not prevent the already completed final report from remaining available.
5. Teacher alert creation is optional and follows the implementation decision for late events.
6. Use case ends for that report.

### AF-26: Violation report arrives after parent activity was closed

**Branches from:** Main Flow step 17 or 19
**Condition:** Professor already closed the parent `training_activity` before or during violation processing.

1. Backend detects that the parent `training_activity` is `CLOSED`.
2. Backend may record the event for audit.
3. Backend does not need to apply a Safe Browser lock if the closed activity already prevents continuation.
4. Student remains unable to continue because the activity is closed.
5. Teacher alert is not duplicated if the activity closure already ended the evaluation window.
6. Use case ends or continues to the blocked state.

### AF-27: Backend fails while recording the Safe Browser event

**Branches from:** Main Flow step 18
**Condition:** Database error or unexpected backend failure occurs while storing the Safe Browser event.

1. Backend does not partially persist an inconsistent state.
2. Backend either rolls back the whole violation transaction or follows a clear transactional rule.
3. If the event cannot be recorded, the assignment is not silently locked without traceability.
4. System returns an error response to the client.
5. Student is prevented from continuing until the system can verify the assignment state.
6. Use case ends or student sees an error/blocked verification state.

### AF-28: Backend locks the assignment but teacher alert creation fails

**Branches from:** Main Flow step 20
**Condition:** The Safe Browser event is stored and the assignment is locked, but grouped teacher alert creation/update fails.

1. Backend preserves the assignment lock and Safe Browser event.
2. Backend logs or records that teacher alert creation failed.
3. Student remains blocked.
4. The incident remains discoverable through the stored Safe Browser event even if the alert failed.
5. System may retry alert creation later if retry infrastructure exists.
6. Flow continues to Main Flow step 21.

### AF-29: Student loses connection before the violation report reaches backend

**Branches from:** Main Flow step 16
**Condition:** Browser detects the violation but the network disconnects, the tab closes, or the report is not delivered.

1. Backend does not receive the immediate violation report.
2. Heartbeat timeout logic later detects that the active Safe Browser session stopped reporting.
3. Backend records a `HEARTBEAT_LOST` or equivalent event.
4. Backend locks the student’s `training_activity_assignment`.
5. Backend creates or updates the grouped teacher alert.
6. Student is blocked on the next interaction or page load.
7. Flow continues to Main Flow step 21 once the timeout is processed.

### AF-30: Student attempts to continue answering after local blocked state fails

**Branches from:** Main Flow step 21
**Condition:** The frontend does not update correctly, or the student tries to bypass the blocked UI and submit another answer.

1. Student submits another answer request.
2. Backend checks the assignment lock before mutating evaluation state.
3. Backend rejects the answer because the assignment is Safe Browser locked.
4. Transcript, current question, question count, and final report remain unchanged.
5. Student sees the blocked state again.
6. Use case ends.

### AF-31: Grouped teacher alert already exists

**Branches from:** Main Flow step 20
**Condition:** There is already an open grouped Safe Browser alert for the same professor and `training_activity`.

1. Backend finds the existing open alert.
2. Backend increments the alert count or updates the last event timestamp.
3. Backend does not create a duplicate open alert.
4. The detailed individual event remains stored separately.
5. Professor later sees one grouped alert with updated incident count.
6. Flow continues to Main Flow step 21.

### AF-32: Professor cannot access the grouped alert

**Branches from:** Main Flow step 23
**Condition:** The professor opens the training activity detail/review area, but the activity does not belong to a class/context they can manage, or they lack permission to view/manage training activity incidents.

1. System validates the professor’s active context and permissions.
2. System denies access to the grouped Safe Browser alert.
3. System shows a permission/no-access message.
4. No alert, event, or assignment lock state is changed.
5. Use case ends.

### AF-33: No grouped alert exists

**Branches from:** Main Flow step 24
**Condition:** Professor opens the training activity detail/review area, but there are no open Safe Browser alerts for that activity.

1. System shows the normal activity details and assignment status.
2. System does not show a Safe Browser incident banner.
3. Professor can continue reviewing submitted reports normally.
4. Use case ends or continues with normal report review.

### AF-34: Grouped alert count is stale

**Branches from:** Main Flow step 24 or 25
**Condition:** The grouped alert count does not match the current number of detailed events, for example because new events arrived while the professor was viewing the page.

1. System reloads the latest alert and event data when the professor opens the alert detail.
2. System shows the current affected assignments and latest event count.
3. System avoids using stale client-side data for unlock actions.
4. Flow continues to Main Flow step 26.

### AF-35: Event details cannot be loaded

**Branches from:** Main Flow step 25 or 26
**Condition:** Professor opens the grouped alert, but the system cannot load the affected assignments/events because of a database or backend error.

1. System shows an error message.
2. System does not unlock or modify any assignment.
3. Grouped alert remains open.
4. Professor may retry later.
5. Use case ends or returns to Main Flow step 24.

### AF-36: Professor tries to unlock an assignment they cannot manage

**Branches from:** Main Flow step 29
**Condition:** Professor selects an affected assignment, but the assignment belongs to a training activity/class they are not allowed to manage.

1. System validates professor permission and ownership/context.
2. System rejects the unlock action.
3. System keeps the `training_activity_assignment` locked.
4. System records no manual unlock event.
5. System shows a permission/no-access message.
6. Use case ends.

### AF-37: Assignment is no longer locked when unlock is requested

**Branches from:** Main Flow step 30
**Condition:** Professor clicks “Unlock assignment”, but the assignment was already unlocked by another valid action/session.

1. System detects that `safe_browser_locked` is already false.
2. System does not create a duplicate manual unlock event unless the implementation intentionally records duplicate attempts.
3. System refreshes the assignment state in the UI.
4. Professor sees that the assignment is already unlocked.
5. Use case ends or returns to the incident detail view.

### AF-38: Parent training activity is closed

**Branches from:** Main Flow step 30 or 33
**Condition:** Professor unlocks the individual assignment, but the parent `training_activity` has already been stopped/closed.

1. System may allow clearing the Safe Browser lock for audit/admin correctness.
2. System keeps the student unable to continue because the parent activity is `CLOSED`.
3. System shows that the assignment is unlocked from Safe Browser but blocked by the closed activity.
4. Original violation events remain preserved.
5. Use case ends.

### AF-39: Assignment is already submitted

**Branches from:** Main Flow step 30 or 33
**Condition:** Professor tries to unlock an assignment that is already `SUBMITTED`.

1. System detects that the assignment is already submitted.
2. System does not reopen or modify the submitted evaluation automatically.
3. System may clear the Safe Browser lock only if the professor is correcting the incident record.
4. Existing transcript and final report remain unchanged.
5. System explains that submitted assignments cannot be continued.
6. Use case ends.

### AF-40: Unlock transaction fails

**Branches from:** Main Flow step 30 or 31
**Condition:** Database error or unexpected backend failure occurs while unlocking the assignment or recording the manual unlock event.

1. System rolls back the unlock transaction.
2. Assignment remains locked.
3. No partial/manual unlock state is persisted without the audit event.
4. System shows an error message.
5. Professor may retry later.
6. Use case ends or returns to the incident detail view.

### AF-41: Student tries to continue immediately while unlock is still processing

**Branches from:** Main Flow step 33
**Condition:** Student attempts to answer before the unlock transaction is committed or before their UI refreshes.

1. Backend checks the latest persisted assignment state.
2. If still locked, backend rejects the answer.
3. If unlocked and parent activity is still open, backend allows continuation according to normal evaluation rules.
4. Student UI refreshes to match the server state.
5. Use case ends or continues with normal evaluation.

### AF-42: Professor keeps the assignment locked

**Branches from:** Main Flow step 27 or 28
**Condition:** Professor reviews the incident and decides not to unlock the affected assignment.

1. Professor closes the incident detail without unlocking, or marks the incident as reviewed.
2. System keeps the selected `training_activity_assignment` locked.
3. System preserves the Safe Browser violation events.
4. Student remains unable to continue that assignment.
5. Grouped alert may remain open or be marked reviewed, depending on the chosen alert-status design.
6. Use case ends.

### AF-43: Multiple students are selected for unlock

**Branches from:** Main Flow step 28
**Condition:** Professor selects multiple locked assignments from the grouped incident detail and chooses a bulk unlock action.

1. System validates that the professor can manage all selected assignments.
2. System unlocks only the selected assignments.
3. System records one manual unlock event per assignment, or one bulk event plus assignment references, depending on the audit design.
4. Assignments not selected remain locked.
5. System refreshes the grouped incident detail.
6. Use case ends or returns to the incident detail view.

---

## Postconditions

- **On success:** The `training_activity` exists with Safe Browser Mode enabled before launch; the activity is launched only if the professor has no other active launched/open training activity globally; one `training_activity_assignment` exists for each eligible active/unlocked student; a Safe Browser violation locks only the affected student’s assignment; lock metadata is stored on the assignment; a Safe Browser event is stored for audit/tracing; one grouped teacher alert exists or is updated for the professor in charge of the `training_activity`; the grouped alert does not duplicate open alerts for the same professor plus activity; the locked student is prevented server-side from starting, continuing, or submitting answers; other students’ assignments remain unaffected; the professor can review the grouped alert and affected events; if the professor unlocks the assignment, only the selected assignment is unlocked; a manual unlock event is stored; original violation events remain preserved; if the parent activity remains open, the unlocked student may continue according to normal evaluation rules; if the professor closes the active training activity, the activity moves to `CLOSED`, `closes_at` is set, non-submitted assignments can no longer continue, and the professor can launch another activity later.
- **On failure:** If permission/context validation fails, no activity, assignment, event, alert, lock, unlock, or close state is changed; invalid activity changes are not saved; launch failures leave no partial assignments; if the professor already has another active launched/open activity, the new activity remains draft and no assignments are created; if no eligible students exist, no assignments are created and the activity remains draft/unlaunched; if Safe Browser Mode cannot be established before starting, no violation is recorded and no evaluation progress is changed; invalid assignment access creates no session/event/lock; invalid violation reports do not lock assignments; if an event cannot be stored, the system avoids silently locking without traceability; if alert creation fails after lock/event persistence, the lock and event remain authoritative and discoverable through event history; failed unlock leaves the assignment locked with no partial unlock state; unlocking does not override a `CLOSED` parent activity or reopen a `SUBMITTED` assignment; Safe Browser events, transcripts, final reports, and close/unlock audit history are not deleted by failure handling.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Safe Browser Mode extends UC-003 Student Training Evaluation; it does not replace the base student training evaluation flow. |
| BR-02 | A `training_activity` counts as active/open when its status is `PUBLISHED`; `DRAFT`, `CLOSED`, and `ARCHIVED` do not count as active/open. |
| BR-03 | A professor may have only one `PUBLISHED` training activity globally, even across different groups/classes. |
| BR-04 | The one-active-activity restriction applies when launching/publishing, not when creating drafts. |
| BR-05 | To launch another activity, the professor must first close, finish, or archive the currently active launched activity. |
| BR-06 | Safe Browser Mode can be enabled or disabled only while the `training_activity` is `DRAFT`. |
| BR-07 | Once the activity is `PUBLISHED`, the Safe Browser setting cannot be changed. |
| BR-08 | When the professor closes an active training activity, the parent `training_activity` moves to `CLOSED`, `closes_at` is set, and all non-submitted assignments under it become non-answerable. |
| BR-09 | Closing a training activity is a professor control action, not a Safe Browser violation. |
| BR-10 | Closing the activity affects the whole `training_activity`; Safe Browser violations affect only individual assignments. |
| BR-11 | If the parent `training_activity` is `CLOSED`, unlocking an individual Safe Browser lock does not allow the student to continue. |
| BR-12 | If a `training_activity_assignment` is already `SUBMITTED`, Safe Browser unlock does not reopen the assignment, allow more answers, or modify the final report. |
| BR-13 | Safe Browser violations lock only the affected `training_activity_assignment`; they must not lock the whole activity, the student’s class membership, or other students’ assignments. |
| BR-14 | Other students’ assignments for the same activity remain unaffected by one student’s Safe Browser violation. |
| BR-15 | For a Safe Browser required activity, answers must not be accepted unless an active Safe Browser session is established. |
| BR-16 | Failure to enter Safe Browser Mode before answering is a setup failure and must not create a violation event or assignment lock. |
| BR-17 | Only one active Safe Browser session is allowed per `training_activity_assignment`. |
| BR-18 | The browser/client runtime should send heartbeat events while the protected evaluation session is active; suggested default interval is every 10 seconds. |
| BR-19 | If the backend has not received a heartbeat for an active Safe Browser session within 30 seconds, the system treats it as `HEARTBEAT_LOST`, records a Safe Browser event, and locks the affected assignment. |
| BR-20 | Missing heartbeat before the protected session is fully established is a setup failure, not a Safe Browser violation. |
| BR-21 | Initial violation event types include `FULLSCREEN_EXIT`, `TAB_HIDDEN`, `WINDOW_BLUR`, `BEFORE_UNLOAD`, `HEARTBEAT_LOST`, and `MANUAL_UNLOCK`. |
| BR-22 | Optional non-violation/support event types may include `SESSION_STARTED`, `HEARTBEAT`, `SESSION_ENDED`, and `ACTIVITY_CLOSED`. |
| BR-23 | Safe Browser violations should be recorded with severity `VIOLATION`; support events may use severity `INFO` if stored. |
| BR-24 | Teacher alerts must be grouped by professor in charge plus `training_activity`. |
| BR-25 | There must be at most one open grouped Safe Browser alert for the same professor and training activity. |
| BR-26 | Suggested grouped alert statuses are `OPEN`, `REVIEWED`, and `RESOLVED`. |
| BR-27 | Repeated violations update the grouped alert count and last event timestamp instead of creating duplicate open alerts. |
| BR-28 | Violation processing and alert creation must be safe against duplicate browser events. |
| BR-29 | Locking must happen only after the backend validates that the assignment belongs to the current student. |
| BR-30 | The frontend blocked state is only a UX layer; backend validation is authoritative. |
| BR-31 | `TrainingAssignmentEvaluationService.start()` and `TrainingAssignmentEvaluationService.answer()` must enforce assignment locks and closed parent activity rules server-side. |
| BR-32 | Unlocking must affect only the selected `training_activity_assignment`. |
| BR-33 | Unlocking must not delete original Safe Browser violation events. |
| BR-34 | Manual unlock must be auditable. |
| BR-35 | Safe Browser violation events, manual unlock events, transcripts, final reports, and activity close timestamps must not be deleted when a professor unlocks an assignment. |
| BR-36 | Professor alert review, unlock, and close actions must be permission-checked server-side. |
| BR-37 | Grouped alert data should be refreshed before professor actions to avoid stale unlock decisions. |
| BR-38 | If bulk unlock is supported, it must preserve assignment-level audit history and unlock only selected assignments. |
| BR-39 | The system does not claim to absolutely prevent tab switching or exiting fullscreen; it detects these events when possible and relies on backend lock/heartbeat timeout enforcement. |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [ ] Main Flow covered: professor enables Safe Browser Mode, launches the activity, student starts the protected evaluation, browser violation is reported, only the affected `training_activity_assignment` is locked, grouped professor alert is created/updated, professor reviews the alert, and professor unlocks the selected assignment.
- [ ] Professor activity lifecycle alternatives covered: AF-1 through AF-10.
- [ ] Student protected-session setup alternatives covered: AF-11 through AF-21.
- [ ] Violation processing alternatives covered: AF-22 through AF-31.
- [ ] Professor review/unlock alternatives covered: AF-32 through AF-43.
- [ ] Business rule covered: a professor can have only one active `PUBLISHED` training activity globally.
- [ ] Business rule covered: a professor can close an active training activity, and closing it prevents all non-submitted assignments from continuing.
- [ ] Business rule covered: Safe Browser Mode can only be changed while the activity is `DRAFT`.
- [ ] Business rule covered: Safe Browser violations lock only the affected `training_activity_assignment`.
- [ ] Business rule covered: other students’ assignments remain unaffected.
- [ ] Business rule covered: locked assignments cannot start, continue, or submit answers server-side.
- [ ] Business rule covered: parent `training_activity = CLOSED` overrides Safe Browser unlock.
- [ ] Business rule covered: submitted assignments are immutable and are not reopened by unlock.
- [ ] Business rule covered: heartbeat timeout after the protected session starts creates a `HEARTBEAT_LOST` violation.
- [ ] Business rule covered: missing heartbeat before the protected session starts is a setup failure, not a violation.
- [ ] Business rule covered: repeated violations are idempotent and do not create duplicate open grouped alerts.
- [ ] Business rule covered: grouped alerts are unique by professor in charge plus `training_activity` while `OPEN`.
- [ ] Business rule covered: manual unlock preserves original Safe Browser violation events.
- [ ] Business rule covered: browser detection is not trusted as the only enforcement layer; backend validation is authoritative.

---

## UI Surface

> This use case is a Vaadin Flow authenticated workspace flow. There is no public React/Hilla surface and no anonymous Safe Browser route.

- Professor training activity management/review screen: the professor creates or edits a training activity, enables Safe Browser Mode before launch, launches the activity, closes/stops the active activity, sees grouped Safe Browser alerts, opens incident details, and unlocks selected student assignments.
- Student assigned training activity execution screen: the student sees Safe Browser entry instructions, starts the protected session, runs browser/client detection, sends heartbeat events, reports violations, and sees the blocked state if the assignment is locked.
- Route access requires authentication, but service methods remain authoritative for ownership, permission, group-class context, assignment lock, and closed-activity rules.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Professor training activity management | Authenticated professor or authorized training activity manager | `/training-activity` |
| Professor activity detail / incident review | Authenticated professor or authorized training activity manager | `/training-activity?trainingActivity={id}` or existing activity detail dialog |
| Student protected assignment execution | Authenticated owning student | `/training-activity/assignments/{assignmentId}` |
