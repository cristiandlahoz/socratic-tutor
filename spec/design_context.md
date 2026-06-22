# Design Context

> UX, navigation, workspace behavior, and visual direction for Socratic Tutor. This file explains how the product should feel and how users move through the academic multi-tenant model. It is not the data model and not the security source of truth.

---

## 1. Design Goal

Socratic Tutor should feel like a calm academic workspace, not a generic chatbot.

The interface must help each user understand:

```text
Who am I?
What role am I operating as?
Which tenant/institution am I inside?
Which group class am I working in?
What actions are available here?
```

The UI should reduce confusion around the hierarchical model by making context visible and switching explicit.

---

## 2. Experience Principles

### Learning First

The student experience should prioritize reasoning, clarity, and low cognitive noise.

Avoid UI that encourages “ask anything anywhere” without class context.

### Context Visible

The active tenant and group class should be visible whenever they affect the page.

Users should not wonder whether they are acting globally, inside a tenant, or inside a class.

### Role Specific

Each role should see a workspace designed for its job.

System admins should not see a student-first interface.

Students should not see academic setup tools.

Professors should see class operations, not global platform management.

### Safe by Design

The UI should avoid showing unavailable actions, but it must never be the only authorization layer.

Every action still goes through service-layer checks.

### Progressive Setup

The product setup should feel like a guided chain:

```text
system admin creates tenant
tenant admin creates academic structure
tenant admin invites professor
professor configures group class
professor invites students
students learn
```

---

## 3. User Journey Overview

### System Admin Journey

1. Logs in.
2. Lands in platform admin workspace.
3. Creates tenant/institution.
4. Invites tenant admin.
5. Reviews platform setup state.

Primary question:

```text
What institutions exist and who is responsible for setting them up?
```

### Tenant Admin Journey

1. Accepts invitation or logs in.
2. Lands in tenant admin workspace.
3. Selects tenant if more than one exists.
4. Creates academic periods.
5. Creates subjects.
6. Creates group classes.
7. Invites professors.

Primary question:

```text
What academic structure exists inside this institution?
```

### Professor Journey

1. Accepts invitation or logs in.
2. Lands in professor workspace.
3. Selects group class if more than one exists.
4. Views group-class dashboard.
5. Invites students.
6. Configures grounding material.
7. Creates formative activities.
8. Starts or uses tutor conversations inside class context.

Primary question:

```text
What class am I teaching right now, and what learning tools are configured for it?
```

### Student Journey

1. Accepts invitation or logs in.
2. Lands in student workspace.
3. Selects group class if more than one exists.
4. Starts or continues tutor conversation.
5. Views assigned formative activities.
6. Completes own activity assignments.

Primary question:

```text
What class am I learning in, and what should I work on next?
```

---

## 4. Workspace Model

The UI uses role-specific workspaces.

| Workspace | Main context | Main purpose |
|---|---|---|
| System Admin | Platform | Create tenants and invite tenant admins. |
| Tenant Admin | Tenant | Configure academic structure. |
| Professor | Group class | Teach and manage class learning resources. |
| Student | Group class | Learn through tutor chat and assigned activities. |

---

## 5. Navigation Model

### Context Navigation

Context navigation answers:

```text
Where am I operating?
```

For tenant admins, context is tenant.

For professors and students, context is group class.

Context switching should be visually separate from page navigation.

### Action Navigation

Action navigation answers:

```text
What can I do here?
```

Examples:

- Dashboard
- Periods
- Subjects
- Group Classes
- Students
- New Chat
- Grounding
- Formative Activities

---

## 6. Layout Direction

### System Admin Layout

Suggested layout:

```text
Top bar
  - app name
  - user menu
Main sidebar
  - dashboard
  - tenants
  - invitations
Content area
  - platform summary cards
  - tenant list
  - tenant-admin invitation actions
```

System admin does not need a group-class context selector.

### Tenant Admin Layout

Suggested layout:

```text
Vertical context rail
  - tenant/institution circles
Traditional sidebar
  - dashboard
  - academic periods
  - subjects
  - group classes
  - invitations
Content area
  - selected tenant dashboard
```

Dashboard cards:

- periods,
- subjects,
- group classes,
- professors,
- pending invitations.

### Professor Layout

Suggested layout:

```text
Vertical context rail
  - group-class circles/cards
Traditional sidebar
  - home
  - new chat
  - formative activities
  - grounding
  - students
Content area
  - selected group-class workspace
```

Professor dashboard cards:

- subject,
- academic period,
- student count,
- grounding status,
- formative activity status,
- recent conversations where allowed.

### Student Layout

Suggested layout:

```text
Top/context header
  - active class
  - class switcher if multiple
Sidebar or simple navigation
  - new chat
  - conversations
  - assigned activities
Content area
  - learning workspace
```

Student UI should be simpler than professor or admin UI.

---

## 7. Login and Onboarding UX

Unauthenticated users should land on login.

Invitation acceptance should feel controlled and clear:

```text
open invitation link
  -> validate token
  -> show invited role and context
  -> register or login
  -> accept invitation
  -> redirect to workspace
```

The UI should explain:

- which email was invited,
- which role is being granted,
- which tenant or group class the invitation belongs to,
- whether the user must register or log in.

The invited email should be read-only during registration.

Open self-signup should not be shown as a normal public option.

---

## 8. Empty and Blocked States

The app must handle missing context safely.

### No Role

Message intent:

```text
Your account exists, but it has not been assigned to a tenant or class yet.
Ask an administrator or professor for an invitation.
```

### Tenant Admin Without Tenant

Message intent:

```text
No tenant context is available. Contact a system admin.
```

### Professor Without Group Class

Message intent:

```text
You are not assigned to any group class yet.
A tenant admin must invite you to a class.
```

### Student Without Group Class

Message intent:

```text
You are not enrolled in any class yet.
Use an invitation from your professor to join.
```

### No Grounding Material

Message intent:

```text
No grounding material has been configured for this class yet.
The tutor can still help generally, but class-specific material is unavailable.
```

### No Assigned Activities

Message intent:

```text
You do not have assigned formative activities right now.
```

---

## 9. Chat UX

The chat experience should make class context visible.

Required visible context:

- active group class,
- subject or class name,
- whether grounding material is available,
- conversation title/history where applicable.

Chat should support:

- new conversation,
- conversation history,
- message display,
- tutor response streaming if available,
- safe error state,
- empty state for first use.

Tutor messages should feel instructional.

Avoid UI copy that makes the tutor sound like a generic answer bot.

Preferred copy direction:

```text
Let's reason through it.
What have you tried so far?
Can you explain what this loop is doing?
Here is a hint, not the full solution yet.
```

---

## 10. Grounding UX

Professor-facing grounding UI should show:

- grounding collections,
- documents inside a collection,
- document status,
- upload or text input action,
- processing/ready/failed state,
- retry or disable where later use cases allow.

Students should not manage grounding.

In student chat, grounding should be reflected as context availability, not as a raw document management tool.

---

## 11. Formative Activities UX

The product-facing term should be:

```text
Formative Activities
```

Database and code may still use `evaluation` and `evaluation_assignment` until naming is migrated by a later use case.

Professor UI should support:

- activity list,
- create/edit activity,
- publish/close/archive,
- assign to students,
- view assignment status where allowed.

Student UI should support:

- assigned activities list,
- status badge,
- start activity,
- continue started activity,
- submit activity.

Status labels should be friendly:

| Database status | Student-facing label |
|---|---|
| `ASSIGNED` | Not started |
| `STARTED` | In progress |
| `SUBMITTED` | Submitted |
| `SKIPPED` | Skipped |
| `EXPIRED` | Expired |
| `EXCUSED` | Excused |

---

## 12. Visual Style

The UI should be:

- calm,
- academic,
- modern,
- readable,
- not gamified excessively,
- not visually noisy.

Use Vaadin theme tokens instead of hard-coded colors and sizes.

If Aura is the active theme, use Aura-compatible tokens and avoid mixing incompatible theme systems.

Preferred visual patterns:

- cards for dashboards,
- clear sidebars,
- compact badges,
- readable tables,
- calm empty states,
- clear primary actions,
- destructive actions with confirmation.

---

## 13. Component Standards

Recommended components:

| Component | Use |
|---|---|
| Vaadin Grid | Lists: tenants, periods, subjects, group classes, students, activities, grounding documents. |
| FormLayout | Create/edit forms. |
| Dialog or side panel | Focused create/edit flows. |
| ComboBox | Select tenant, subject, period, group class, target student. |
| TextField | Search, names, codes, email. |
| TextArea | Instructions, descriptions, document text. |
| Upload | Grounding document upload. |
| Notification | Success/error feedback. |
| ConfirmDialog | Destructive or disabling actions. |
| Badge/Span | Status indicators. |

---

## 14. Local UI State

Use local UI state for:

- open/closed side panels,
- selected row,
- current tab,
- search text,
- filter values,
- form dirty state,
- confirmation dialog state,
- active context UI selector.

Do not use local UI state for:

- authentication,
- authorization,
- persisted role,
- tenant membership,
- group-class membership,
- conversation ownership,
- assignment ownership.

---

## 15. Accessibility

The UI should:

- support keyboard navigation,
- provide visible focus states,
- use semantic labels,
- associate form labels with inputs,
- avoid relying on color alone for status,
- keep contrast readable,
- make error messages clear,
- support responsive layouts on small screens.

---

## 16. Design Review Checklist

Before UI changes are accepted:

- [ ] Active role is clear.
- [ ] Active tenant or group class is visible when relevant.
- [ ] Protected actions are hidden or disabled when unavailable.
- [ ] Service-layer checks still enforce security.
- [ ] Empty states are safe and useful.
- [ ] Student UI remains learning-focused.
- [ ] Professor UI remains group-class-focused.
- [ ] Tenant admin UI remains tenant-setup-focused.
- [ ] System admin UI remains platform-focused.
- [ ] Grounding UI is professor-only in the baseline.
- [ ] Formative activities use friendly labels.
- [ ] Chat copy supports Socratic tutoring.
- [ ] No UI flow creates academic data without valid context.
