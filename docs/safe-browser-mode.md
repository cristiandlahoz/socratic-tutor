# Safe Browser Mode

This document explains how Socratic Tutor implements **Safe Browser Mode** for
formative activities.

The important split is:

```text
TrainingActivity.safeBrowserEnabled = whether the activity requires Safe Browser
TrainingActivityAssignment          = per-student lock/session state
Browser runtime                     = detects suspicious browser signals
SafeBrowserModeService              = records events and enforces assignment state
SafeBrowserAlert                    = grouped professor-facing incident alert
SafeBrowserAssignmentStateBus       = realtime student UI refresh
```

Safe Browser Mode does not try to make the browser impossible to escape. The
browser can only report signals such as fullscreen exit, tab hiding, window blur,
or unload. The server records those signals and decides what happens to the
assignment.

## Purpose

Safe Browser Mode adds lightweight integrity monitoring to formative activities.

It preserves:

- professor opt-in per activity
- student consent through an explicit start action
- server-side event history
- assignment lock state after a detected violation
- grouped professor incident alerts
- manual professor unlock
- realtime refresh for the affected student view

It does not provide operating-system-level lockdown. The design is detection and
server-side assignment enforcement, not physical browser control.

## Data model

Safe Browser state is stored on the assignment and in two incident tables.

```text
training_activity
  - safe_browser_enabled

training_activity_assignment
  - safe_browser_locked
  - safe_browser_locked_at
  - safe_browser_lock_reason
  - safe_browser_session_active
  - safe_browser_last_heartbeat_at

safe_browser_event
  - training_activity_assignment_id
  - actor_group_class_member_id
  - event_type
  - severity
  - occurred_at
  - created_at

safe_browser_alert
  - training_activity_id
  - professor_tenant_account_id
  - professor_group_class_member_id
  - status
  - incident_count
  - last_event_at
```

The assignment carries the current state. The event log carries the audit trail.
The alert groups incidents for the professor.

## Activity setup

The professor enables Safe Browser when creating or editing a draft activity:

```text
TrainingActivityView
  -> Safe Browser Mode checkbox
  -> TrainingActivityService.createPending(..., safeBrowserEnabled)
```

The flag can only change before launch:

```java
if (activity.getStatus() != TrainingActivityLifecycleStatus.DRAFT
        && activity.isSafeBrowserEnabled() != safeBrowserEnabled) {
    throw new IllegalStateException("Safe Browser Mode can only be changed before launch.");
}
```

After launch, the activity is published and each eligible student receives an
assignment. Safe Browser is then interpreted from the activity flag attached to
that assignment.

## Student entry flow

```text
Student opens assignment
        │
        ▼
┌─────────────────────────────────────────┐
│ TrainingAssignmentView                  │
│                                         │
│ if activity.safeBrowserEnabled:         │
│   show Safe Browser entry panel         │
│ else:                                   │
│   auto-start normal evaluation          │
└────────────────────┬────────────────────┘
                     │
                     ▼
Student clicks Start Safe Browser Mode
        │
        ▼
┌─────────────────────────────────────────┐
│ SafeBrowserModeService.startSession(...)│
│                                         │
│ 1. require current student assignment   │
│ 2. ensure assignment can start session  │
│ 3. mark session active                  │
│ 4. store heartbeat timestamp            │
│ 5. record SESSION_STARTED event         │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ installSafeBrowserRuntime()             │
│                                         │
│ browser listeners + heartbeat timer     │
└─────────────────────────────────────────┘
```

The composer is enabled only when Safe Browser is active:

```text
assignment is not submitted
assignment is not blocked
and either:
  - activity does not require Safe Browser
  - Safe Browser session is active
```

## Browser runtime

The runtime is installed from `TrainingAssignmentView` with client-side JavaScript.

It registers these signals:

```text
fullscreenchange -> FULLSCREEN_EXIT when fullscreen is missing
visibilitychange -> TAB_HIDDEN when document is hidden
window blur      -> WINDOW_BLUR
beforeunload     -> BEFORE_UNLOAD
interval         -> heartbeat every 10 seconds
```

It also attempts to enter fullscreen:

```text
if no fullscreen element:
  requestFullscreen()
  if request fails -> report FULLSCREEN_EXIT
```

Important distinction:

```text
browser event detection      = client-side signal
assignment lock/enforcement  = server-side state change
```

The browser code does not prevent the student from changing tabs or leaving
fullscreen. It detects that it happened and reports it through `@ClientCallable`
methods.

## Violation handling

Client callbacks:

```java
@ClientCallable
public void reportSafeBrowserViolation(String eventType)

@ClientCallable
public void recordSafeBrowserHeartbeat()
```

Runtime flow for a violation:

```text
Browser detects signal
        │
        ▼
TrainingAssignmentView.reportSafeBrowserViolation(...)
        │
        ▼
SafeBrowserModeService.reportViolation(...)
        │
        ▼
lockAssignment(...)
        │
        ├── record SafeBrowserEvent severity=VIOLATION
        ├── set assignment.safeBrowserLocked=true
        ├── set safeBrowserLockedAt
        ├── set safeBrowserLockReason
        ├── set safeBrowserSessionActive=false
        ├── upsert professor alert
        └── publish assignment state notification after commit
```

Supported violation types:

```text
FULLSCREEN_EXIT
TAB_HIDDEN
WINDOW_BLUR
BEFORE_UNLOAD
HEARTBEAT_LOST
```

Informational event types:

```text
SESSION_STARTED
HEARTBEAT
SESSION_ENDED
MANUAL_UNLOCK
ACTIVITY_CLOSED
```

`HEARTBEAT` and `SESSION_ENDED` exist in the enum, but the current runtime path
updates heartbeat timestamps without writing a heartbeat event for every pulse.

## Lock semantics

The assignment is locked only when it is still answerable.

```text
if assignment is SUBMITTED:
  record violation event only
  do not lock

if activity is CLOSED:
  record violation event only
  do not lock

otherwise:
  lock assignment
  deactivate Safe Browser session
  create/update professor alert
```

This avoids punishing a student after the assignment is already submitted or the
activity window has already closed.

When locked, answering is blocked by the evaluation service:

```java
if (assignment.isSafeBrowserLocked()) {
    throw new IllegalStateException(
        "Safe Browser Mode was interrupted. Ask your professor to review this assignment."
    );
}
```

The student UI shows:

```text
Safe Browser Mode fue interrumpido. Tu profesor debe revisar o desbloquear esta asignación.
```

## Heartbeat behavior

The client sends a heartbeat every 10 seconds:

```text
window.setInterval(heartbeat, 10000)
```

Server-side heartbeat update:

```text
SafeBrowserModeService.recordHeartbeat(...)
  - require current student assignment
  - ignore disabled Safe Browser or locked assignment
  - set safeBrowserSessionActive=true
  - update safeBrowserLastHeartbeatAt
```

There is also a server method for detecting stale active sessions:

```text
SafeBrowserModeService.lockExpiredSessions()
  - cutoff = now - 30 seconds
  - find active Safe Browser sessions
  - lock sessions with old heartbeat timestamps
  - report HEARTBEAT_LOST
```

Current code defines this expiration mechanism, but there is no in-project caller
for `lockExpiredSessions()`. If heartbeat expiry should run automatically, it
needs an explicit scheduler or external trigger.

## Professor alerts

When a lockable violation occurs, the service upserts one open alert per
professor and activity:

```text
find OPEN alert by:
  professor_tenant_account_id
  training_activity_id

if missing:
  create alert

increment incident_count
update last_event_at
```

The professor can query:

```text
SafeBrowserModeService.listOpenAlerts(activityId)
SafeBrowserModeService.listEvents(activityId)
```

Both require the current context to be a professor context for the same class as
the training activity.

## Manual unlock

The professor unlock action lives in the activity dialog assignment grid:

```text
TrainingActivityDialog
  -> unlockButton(...)
  -> unlockAssignment(...)
  -> SafeBrowserModeService.unlockAssignment(...)
```

Unlock does this:

```text
1. require professor can manage the activity
2. set safeBrowserLocked=false
3. set safeBrowserSessionActive=false
4. clear safeBrowserLockReason
5. record MANUAL_UNLOCK event
6. publish assignment state notification after commit
```

Unlock does not automatically restart Safe Browser. The student must start a new
Safe Browser session before answering again.

## Realtime student refresh

The student assignment view subscribes to assignment state changes:

```text
TrainingAssignmentView.onAttach(...)
  -> SafeBrowserAssignmentStateBus.subscribe(...)
```

When the service locks or unlocks an assignment, it publishes after commit:

```text
SafeBrowserAssignmentStateBus.Notification
  - assignmentId
  - groupClassMemberId
  - locked
```

The view checks whether the notification affects the open assignment, reloads the
assignment, and re-renders inside `UI.access(...)`.

```text
service transaction commits
        │
        ▼
assignmentStateBus.publish(notification)
        │
        ▼
TrainingAssignmentView subscriber
        │
        ▼
evaluationService.getForCurrentStudent(assignmentId)
        │
        ▼
renderAssignment()
```

This is why a professor unlock can update the student's open assignment view
without waiting for a full page reload.

## Closed activity behavior

When an activity is manually closed:

```text
TrainingActivityService.close(...)
  - mark activity CLOSED
  - mark non-submitted, non-terminal assignments EXPIRED
  - set safeBrowserSessionActive=false
```

Closed submitted assignments reopen in normal review mode, not Safe Browser shell
mode. The student gets the normal application shell, a review app bar, and no
answer composer.

## Implementation map

Activity and assignment state:

```text
src/main/java/com/wornux/data/entities/training_activity/
TrainingActivity.java
TrainingActivityAssignment.java
TrainingActivityAssignmentStatus.java
TrainingActivityLifecycleStatus.java
```

Safe Browser event and alert entities:

```text
src/main/java/com/wornux/data/entities/training_activity/
SafeBrowserEvent.java
SafeBrowserEventType.java
SafeBrowserEventSeverity.java
SafeBrowserAlert.java
SafeBrowserAlertStatus.java
```

Repositories:

```text
src/main/java/com/wornux/data/repositories/training_activity/
SafeBrowserEventRepository.java
SafeBrowserAlertRepository.java
TrainingActivityAssignmentRepository.java
```

Core service:

```text
src/main/java/com/wornux/services/training_activity/
SafeBrowserModeService.java
```

Realtime state bus:

```text
src/main/java/com/wornux/services/training_activity/
SafeBrowserAssignmentStateBus.java
```

Student runtime and client callbacks:

```text
src/main/java/com/wornux/ui/training_activity/
TrainingAssignmentView.java
```

Professor activity and incident UI:

```text
src/main/java/com/wornux/ui/training_activity/
TrainingActivityView.java
TrainingActivityDialog.java
```

Focused use-case tests:

```text
src/test/java/com/wornux/specdriven/usecases/uc005_safe_browser_mode/
UC005SafeBrowserMode.java
```

## Things not to do

- Do not describe Safe Browser as a hard browser lockdown. It is detection plus
  server-side assignment enforcement.
- Do not rely on client-side JavaScript as the source of truth for permission or
  ownership. The service re-resolves the current academic context.
- Do not let non-professor contexts manage Safe Browser incidents.
- Do not enable the answer composer for Safe Browser activities until a session is
  active.
- Do not auto-resume a session after professor unlock. Unlock clears the lock;
  the student starts a fresh session.
- Do not assume heartbeat expiry runs automatically unless a scheduler or caller
  is added.

## Future improvements

- Add an explicit scheduler for `lockExpiredSessions()` if heartbeat expiry should
  be automatic.
- Close or resolve open `SafeBrowserAlert` rows when the professor reviews an
  incident.
- Add a richer professor incident timeline in the activity dialog.
- Add metrics for violations, unlocks, heartbeat losses, and lock latency.
- Make the client runtime easier to test by extracting the JavaScript into a
  frontend module.
