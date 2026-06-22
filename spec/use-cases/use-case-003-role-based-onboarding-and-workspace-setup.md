# UC-003: Role-Based Onboarding and Workspace Setup

---

**Goal:** As the development team, I want to implement role-based login, onboarding, invitation, email delivery, and workspace setup flows so that system admins, tenant admins, professors, and students enter the application through the correct UI and operate inside the academic multi-tenant model established by UC-001 and activated by UC-002.

**Status:** Pending  
**Date:** 2026-06-22

---

## Scope

This use case covers the first complete role-based application setup flow after the database and runtime adaptation work.

This use case includes:

- Showing login first when the user is not authenticated.
- Resolving the authenticated account after login.
- Resolving the user's available tenant accounts.
- Resolving the user's active role context.
- Redirecting the user to the correct workspace depending on role.
- Creating the system admin workspace.
- Allowing a system admin to create tenants.
- Allowing a system admin to invite a tenant admin by email.
- Adding Mailpit as a local SMTP/webmail service for development and testing.
- Implementing a reusable email architecture, not just a Docker container.
- Implementing email configuration through application properties and environment variables.
- Implementing an `EmailService` abstraction.
- Implementing an SMTP email sender using Jakarta Mail / Eclipse Angus Mail.
- Implementing an `EmailTemplateService` abstraction for HTML email templates.
- Implementing invitation email templates.
- Simulating email delivery locally through Mailpit.
- Allowing invited users to register if they do not already have an account.
- Allowing invited users to accept an invitation and receive the correct role.
- Implementing a controlled multi-step onboarding acceptance flow inspired by a proven production-style signup pattern.
- Adding a temporary session-scoped onboarding context for invitation acceptance.
- Supporting secure single-use invitation tokens with expiration and contextual metadata.
- Applying role-based post-login routing after onboarding or authentication.
- Creating the tenant admin workspace.
- Allowing a tenant admin to create academic periods.
- Allowing a tenant admin to create subjects.
- Allowing a tenant admin to create group classes.
- Allowing a tenant admin to invite professors into group classes.
- Creating the professor workspace.
- Showing a professor the group classes where they are a member.
- Selecting the first available group class by default.
- Showing professor actions for:
  - home/dashboard
  - new chat
  - formative activities/evaluations
  - document ingestion/grounding
  - student management
- Allowing a professor to invite students into a group class.
- Showing a student workspace with basic chat access.
- Allowing students to access group classes and assignments only when invited or assigned.
- Establishing UI layout rules for Discord-like context switching and traditional navigation sidebar.

This use case does not include:

- Production email provider setup.
- Production password reset.
- OAuth/social login.
- Full authorization middleware for every future permission.
- Full student analytics.
- Full learner-profile or misconception tracking.
- Final visual polish of all screens.
- Real external SMTP delivery.
- Payments, billing, or subscription management.
- Institution-wide SIS integration.

---

## Relationship to Previous Use Cases

UC-001 defines the academic multi-tenant ERD.

UC-002 adapts active runtime logic to operate on the target ERD.

UC-003 adds the first complete user-facing setup experience on top of that model.

The sequence is:

```text
UC-001: Create the target schema.
UC-002: Adapt active services and runtime to the target schema.
UC-003: Add role-based login, onboarding, invitations, email delivery, and workspaces.
```

---

## Current Product Direction

The system is no longer centered around anonymous browser `client_id`.

The system is centered around authenticated users and academic context:

```text
account
  -> tenant_account
      -> group_class_member
```

The UI must reflect that.

A user does not simply enter "the chat app."

A user enters the correct workspace based on who they are:

```text
SYSTEM_ADMIN -> platform administration
TENANT_ADMIN -> university/tenant setup
PROFESSOR -> group-class teaching workspace
STUDENT -> learning/chat/evaluation workspace
```

---

## Actors

- **Primary actor:** Development team

Secondary product actors represented in the flow:

- **System Admin**
- **Tenant Admin**
- **Professor**
- **Student**
- **Mailpit local SMTP server**
- **Socratic Tutor application**

---

## Preconditions

- UC-001 target ERD exists.
- UC-002 runtime adaptation is complete or sufficiently complete for active services to use the target ERD.
- The application has an `account` table.
- The application has `tenant`, `tenant_account`, and role assignment tables.
- The application has `subject`, `academic_period`, `group_class`, and `group_class_member`.
- The application has `group_class_join_code` or an equivalent invitation mechanism for student entry.
- The system admin seed account exists.
- Spring Security or the chosen local login mechanism can authenticate an account.
- The local development environment can run Docker Compose services.
- Mailpit can be added as a local SMTP/webmail service.
- The application can send email through local SMTP configuration.
- The UI can render Vaadin views after login.

---

## Trigger

The development team needs to make the application usable after login by showing each authenticated user the correct role-based workspace and by supporting the first setup path through system admin, tenant admin, professor, and student onboarding.

In practical terms:

```text
The app must start at login, identify who the user is, and route them to the correct setup or workspace experience.
```

---

# Main Flow

---

## Stage 1: Add Local Mailpit Development Service

### Purpose

Enable local email invitation testing without using a real SMTP provider.

Mailpit must not be treated as a loose Docker-only detail. It is part of the local email architecture used to test invitation delivery safely.

### Flow

1. **Development team** adds Mailpit to the Docker Compose setup.
2. **Development team** exposes the Mailpit web UI port.
3. **Development team** exposes the Mailpit SMTP port.
4. **Development team** configures the application email settings to use Mailpit in the `dev` profile.
5. **Development team** implements an email service abstraction.
6. **Development team** implements an SMTP-backed email sender.
7. **Development team** implements an email template renderer.
8. **Development team** adds HTML invitation templates.
9. **System** starts Mailpit with the rest of local services.
10. **System** sends invitation emails to Mailpit instead of a real external mailbox.
11. **Development team** can open Mailpit and inspect invitation emails.

### Result

```text
Local invitation email delivery is available through Mailpit and a reusable email architecture.
```

---

## Stage 2: Implement Email and Mailpit Reference Architecture

### Purpose

Implement a production-style email architecture that works locally with Mailpit and can later be redirected to a real SMTP provider without rewriting invitation logic.

### Desired Architecture

The application should use this email flow:

```text
Application service / invitation service
    -> EmailTemplateService renders an HTML template
    -> EmailService receives a rendered email message
    -> SMTP implementation sends through configured SMTP host
    -> Mailpit captures emails in local/dev environment
```

For local development:

```text
SMTP host: localhost
SMTP port: 1025
Mailpit web UI: http://localhost:8025
```

For future production:

```text
SMTP host, port, username, password, sender address, and sender name should come from environment variables.
```

### Flow

1. **Development team** adds application-specific email properties.
2. **Development team** adds environment-variable overrides for SMTP configuration.
3. **Development team** creates an `EmailService` interface.
4. **Development team** creates an `EmailMessage` model.
5. **Development team** creates a templated email message model.
6. **Development team** creates an `EmailSendException`.
7. **Development team** creates an SMTP email implementation using Jakarta Mail / Eclipse Angus Mail.
8. **Development team** creates an `EmailTemplateService` interface.
9. **Development team** creates a Thymeleaf-based email template renderer.
10. **Development team** places email templates under `src/main/resources/templates/email/`.
11. **Development team** creates invitation templates for tenant admin, professor, and student invitations.
12. **System** sends rendered HTML email through the configured SMTP host.
13. **Mailpit** captures the email locally.

### Result

```text
Email delivery is abstracted, template-driven, locally testable, and future SMTP-provider ready.
```

---

## Stage 3: Implement Onboarding State and Invitation Acceptance Pattern

### Purpose

Adopt a proven multi-step onboarding pattern for invited users, inspired by a production-style Spring/Vaadin application that uses signup flags, a session-scoped onboarding context, secure single-use tokens, and role-based post-login routing.

The goal is to make invitation acceptance reliable across this path:

```text
invitation link -> token validation -> registration or login -> role/membership creation -> workspace redirect
```

### Reference Pattern

The reference implementation uses these ideas:

```text
Multi-step onboarding flow
Session-scoped context for temporary onboarding data
Single-use email tokens with expiration
Metadata attached to the token/invitation
Post-login routing based on user state
Temporary onboarding access before the final role context exists
Event-driven notifications for signup/invitation milestones
Optional abandoned-signup or expired-invitation detection
```

UC-003 should reuse the good parts of that pattern while adapting it to Socratic Tutor's academic multi-tenant model.

### Flow

1. **Development team** defines an onboarding state model for invited users.
2. **Development team** defines an invitation acceptance flow that can support three target roles:
   - `TENANT_ADMIN`
   - `PROFESSOR`
   - `STUDENT`
3. **Development team** ensures invitation tokens are single-use and expire after a configured duration.
4. **Development team** stores only token hashes in the database.
5. **Development team** allows the invitation record or token metadata to carry contextual data such as:
   - tenant id
   - group class id
   - target role
   - invited email
   - invitation purpose
6. **Development team** creates a session-scoped onboarding context for temporary data needed between token validation, registration, login, and final workspace redirect.
7. **Invited user** opens the invitation link.
8. **System** validates the invitation token.
9. **System** stores the valid invitation context in the onboarding session context.
10. If the invited email does not belong to an existing account, **System** routes the user to registration.
11. If the invited email belongs to an existing account, **System** routes the user to login.
12. **User** completes registration or login.
13. **System** verifies that the authenticated account email matches the invitation email.
14. **System** creates or reuses the required `account`.
15. **System** creates or reuses the required `tenant_account`.
16. **System** assigns the target role when needed.
17. If the target role is `PROFESSOR` or `STUDENT`, **System** creates the required `group_class_member`.
18. **System** marks the invitation as accepted.
19. **System** clears the onboarding session context.
20. **System** redirects the user to the correct workspace.

### Required Onboarding Context

The onboarding session context should be temporary and must not replace persisted invitation state.

Recommended data:

```text
invitation_id
invited_email
target_role
tenant_id
group_class_id
post_accept_redirect
validated_at
```

This context exists to carry data safely between request steps. The database remains the source of truth.

### Post-Login Routing Pattern

After login, the system must inspect the authenticated account and decide where the user belongs.

Recommended routing priority:

```text
SYSTEM_ADMIN -> system admin workspace
TENANT_ADMIN -> tenant admin workspace
PROFESSOR -> professor workspace
STUDENT -> student workspace
NO_ROLE -> no-access or pending-invitation state
```

This routing should happen after authentication and after any pending invitation context is accepted.

### Temporary Onboarding Access

A newly invited user may not have all normal role records before the invitation is accepted.

The system may allow temporary access only to onboarding routes such as:

```text
/login
/register
/invitations/accept
/onboarding/*
```

This must not become a general permission bypass. It only exists to let invited users complete onboarding.

### Result

```text
Invitation acceptance behaves like a controlled multi-step onboarding flow instead of a one-shot form submission.
```

---

## Stage 4: Show Login First

### Purpose

Ensure unauthenticated users always begin at login.

### Flow

1. **Unauthenticated user** opens the application.
2. **System** checks whether the user is authenticated.
3. **System** redirects unauthenticated users to `/login`.
4. **System** shows the login page.
5. **User** enters credentials.
6. **System** validates credentials.
7. **System** creates an authenticated session.
8. **System** resolves the authenticated `account`.

### Result

```text
The application starts with login and resolves the authenticated account before showing any workspace.
```

---

## Stage 5: Resolve Authenticated User Context

### Purpose

Determine what the logged-in user can access.

### Flow

1. **System** loads the authenticated `account`.
2. **System** loads all `tenant_account` records for the account.
3. **System** loads assigned roles for each tenant account.
4. **System** loads group-class memberships for each tenant account.
5. **System** determines the highest relevant navigation context:
   - system admin
   - tenant admin
   - professor
   - student
6. **System** determines the default active tenant or group class.
7. **System** stores the active context in the UI/session state.
8. **System** redirects the user to the correct workspace.

### Role Routing Rules

```text
SYSTEM_ADMIN -> system admin workspace
TENANT_ADMIN -> tenant admin workspace
PROFESSOR -> professor workspace
STUDENT -> student workspace
```

If a user has multiple roles, the system must choose a deterministic default and allow context switching where appropriate.

### Result

```text
The app knows who is logged in and what workspace they should see.
```

---

## Stage 6: System Admin Creates Tenant

### Purpose

Allow platform setup from the system admin account.

### Flow

1. **System Admin** logs in.
2. **System** routes the system admin to the system admin workspace.
3. **System** shows a dashboard for platform administration.
4. **System Admin** selects "Create tenant."
5. **System** shows a tenant creation form.
6. **System Admin** enters tenant name.
7. **System Admin** submits the form.
8. **System** creates a `tenant`.
9. **System** creates the system admin's `tenant_account` relationship if needed.
10. **System** records the tenant owner relationship if required by the schema.
11. **System** shows the newly created tenant in the system admin workspace.

### Result

```text
A tenant/university exists and can receive a tenant admin.
```

---

## Stage 7: System Admin Invites Tenant Admin

### Purpose

Allow the system admin to delegate tenant setup to a tenant admin.

### Flow

1. **System Admin** opens a tenant.
2. **System Admin** selects "Invite tenant admin."
3. **System** shows an invitation form.
4. **System Admin** enters the tenant admin email.
5. **System Admin** submits the invitation.
6. **System** creates an invitation record.
7. **System** generates a secure invitation token.
8. **System** stores only a hash of the invitation token.
9. **System** builds an invitation link using the raw token.
10. **System** renders the tenant admin invitation template.
11. **System** sends the invitation email through the email service.
12. **Mailpit** captures the invitation email locally.
13. **System Admin** can see the invitation status.
14. **Invited user** opens the email from Mailpit.
15. **Invited user** clicks the invitation link.
16. **System** validates the token.
17. If the email does not belong to an existing account, **System** shows registration.
18. **Invited user** registers.
19. **System** creates the account.
20. **System** creates a `tenant_account`.
21. **System** assigns the `TENANT_ADMIN` role.
22. **System** marks the invitation as accepted.
23. **System** logs in or redirects the user to login.
24. **Tenant Admin** enters the tenant admin workspace.

### Result

```text
A tenant admin can be invited locally through Mailpit and can access the tenant admin workspace after registration or login.
```

---

## Stage 8: Tenant Admin Workspace

### Purpose

Provide the tenant admin with a workspace to manage tenant academic setup.

### UI Direction

The tenant admin workspace uses two navigation areas:

```text
1. Discord-like vertical context sidebar:
   - each circular item represents a university/tenant the user can access

2. Traditional application sidebar:
   - dashboard
   - periods
   - subjects
   - group classes
   - invitations
```

The dashboard can use card-style UI inspired by modern admin dashboards, with cards for:

```text
academic periods
subjects
group classes
professors
students
pending invitations
```

### Flow

1. **Tenant Admin** logs in.
2. **System** resolves tenant admin context.
3. **System** shows the tenant admin workspace.
4. **System** shows accessible tenants in the Discord-like tenant sidebar.
5. **System** selects the first accessible tenant by default.
6. **System** shows the traditional sidebar for tenant setup actions.
7. **System** shows tenant dashboard cards.
8. **Tenant Admin** can navigate to periods, subjects, group classes, and invitations.

### Result

```text
Tenant admin has a clear workspace for academic setup inside a selected tenant.
```

---

## Stage 9: Tenant Admin Creates Academic Periods

### Purpose

Allow the tenant admin to define academic periods.

### Flow

1. **Tenant Admin** opens the periods view.
2. **System** lists existing academic periods for the selected tenant.
3. **Tenant Admin** selects "Create period."
4. **System** shows a form.
5. **Tenant Admin** enters:
   - code
   - name
   - start date
   - end date
6. **Tenant Admin** submits the form.
7. **System** validates the period.
8. **System** creates an `academic_period`.
9. **System** refreshes the period list.

### Result

```text
The tenant has at least one academic period available for group-class creation.
```

---

## Stage 10: Tenant Admin Creates Subjects

### Purpose

Allow the tenant admin to define subjects/courses.

### Flow

1. **Tenant Admin** opens the subjects view.
2. **System** lists existing subjects for the selected tenant.
3. **Tenant Admin** selects "Create subject."
4. **System** shows a form.
5. **Tenant Admin** enters:
   - code
   - name
6. **Tenant Admin** submits the form.
7. **System** validates the subject.
8. **System** creates a `subject`.
9. **System** refreshes the subject list.

### Result

```text
The tenant has subjects available for group-class creation.
```

---

## Stage 11: Tenant Admin Creates Group Classes

### Purpose

Allow the tenant admin to create concrete class sections.

### Flow

1. **Tenant Admin** opens the group classes view.
2. **System** lists group classes for the selected tenant.
3. **Tenant Admin** selects "Create group class."
4. **System** shows a form.
5. **Tenant Admin** selects:
   - subject
   - academic period
6. **Tenant Admin** enters:
   - group code
   - group name
7. **Tenant Admin** submits the form.
8. **System** validates the group class.
9. **System** creates a `group_class`.
10. **System** records `created_by_tenant_account_id`.
11. **System** refreshes the group-class list.

### Result

```text
The tenant has group classes ready for professor assignment.
```

---

## Stage 12: Tenant Admin Invites Professor

### Purpose

Allow the tenant admin to invite professors into group classes.

### Flow

1. **Tenant Admin** opens a group class.
2. **Tenant Admin** selects "Invite professor."
3. **System** shows an invitation form.
4. **Tenant Admin** enters professor email.
5. **Tenant Admin** submits the invitation.
6. **System** creates an invitation record.
7. **System** generates a secure invitation token.
8. **System** stores only a hash of the token.
9. **System** renders the professor invitation template.
10. **System** sends invitation email through the email service.
11. **Mailpit** captures the invitation locally.
12. **Professor** opens the invitation link.
13. **System** validates the invitation.
14. If the professor has no account, **System** shows registration.
15. **Professor** registers or logs in.
16. **System** creates or reuses `account`.
17. **System** creates or reuses `tenant_account`.
18. **System** assigns the `PROFESSOR` role where required.
19. **System** creates `group_class_member` with role `PROFESSOR`.
20. **System** marks the invitation as accepted.
21. **System** redirects the professor to professor workspace.

### Result

```text
A professor can be invited to a group class and receives the correct membership.
```

---

## Stage 13: Professor Workspace

### Purpose

Provide professors with a workspace centered around their group classes.

### UI Direction

The professor workspace uses two navigation areas:

```text
1. Discord-like vertical context sidebar:
   - each circular item represents a group class the professor belongs to

2. Traditional application sidebar:
   - home/dashboard
   - new chat
   - formative activities
   - ingest documents
   - students
```

The first available group class is selected by default.

The professor dashboard shows cards for group classes and selected-class details.

The selected group-class area shows:

```text
group class summary
subject
period
student list
document/grounding status
evaluation/activity status
student invitation action
```

### Flow

1. **Professor** logs in.
2. **System** resolves professor tenant accounts and group-class memberships.
3. **System** shows professor workspace.
4. **System** shows group classes as circular items in the context sidebar.
5. **System** selects the first group class by default.
6. **System** shows the traditional sidebar.
7. **System** shows professor dashboard cards.
8. **System** shows the students for the selected class.
9. **Professor** can select a different group class.
10. **System** updates the active group-class context.
11. **Professor** can navigate to:
    - new chat
    - formative activities
    - ingest documents
    - students

### Result

```text
Professor sees a group-class-centered workspace and can operate inside the selected class context.
```

---

## Stage 14: Professor Manages Students

### Purpose

Allow professors to manage students in their own group class.

### Flow

1. **Professor** opens the students list for the selected group class.
2. **System** shows students in a table.
3. Each row may show:
   - student name
   - email
   - membership status
   - joined date
   - actions
4. **Professor** can edit a student membership where allowed.
5. **Professor** can disable/remove a student from the group class.
6. **System** uses logical removal or locking instead of hard deletion.
7. **System** updates the students table.

### Result

```text
Professor can manage students inside the selected group class without affecting global account identity.
```

---

## Stage 15: Professor Invites Students

### Purpose

Allow professors to invite students into a group class.

### Flow

1. **Professor** opens the students view.
2. **Professor** selects "Invite student."
3. **System** shows an invitation form.
4. **Professor** enters student email.
5. **Professor** submits the invitation.
6. **System** creates an invitation record or group-class join mechanism.
7. **System** generates a secure invitation token.
8. **System** stores only a hash of the token.
9. **System** renders the student invitation template.
10. **System** sends the invitation email through the email service.
11. **Mailpit** captures the invitation locally.
12. **Student** opens the invitation link.
13. **System** validates the invitation.
14. If the student has no account, **System** shows registration.
15. **Student** registers or logs in.
16. **System** creates or reuses `account`.
17. **System** creates or reuses `tenant_account`.
18. **System** creates `group_class_member` with role `STUDENT`.
19. **System** marks the invitation as accepted.
20. **System** redirects the student to the student workspace.

### Result

```text
Students can enter a group class through professor invitation.
```

---

## Stage 16: Student Workspace

### Purpose

Provide a simple student workspace focused on consuming tutor and assignment flows.

### UI Direction

The initial student workspace is intentionally simple.

The student sees:

```text
traditional sidebar
new chat
assigned evaluations / formative activities
active group-class context
```

The student does not manage tenants, subjects, periods, group classes, professors, or students.

### Flow

1. **Student** logs in.
2. **System** resolves student group-class memberships.
3. **System** selects the first available group class by default.
4. **System** shows student workspace.
5. **Student** can start a new chat in the selected group class.
6. **Student** can view assigned evaluations.
7. **Student** can open an assigned evaluation.
8. **Student** can update their own evaluation assignment status through the assignment flow.

### Result

```text
Student has a simple learning workspace tied to invited group classes and assigned activities.
```

---

## Stage 17: Add Invitation Data Model

### Purpose

Support tenant-admin, professor, and student invitation links.

### Current Gap

UC-001 includes `group_class_join_code`, but that alone is not enough for all invitation flows.

The system also needs email invitations for:

```text
system admin -> tenant admin
tenant admin -> professor
professor -> student
```

### Required Direction

The implementation should add an invitation model or equivalent.

Recommended model:

```text
invitation
- id
- tenant_id
- group_class_id nullable
- invited_email
- target_role
- token_hash
- status
- expires_at
- accepted_at
- invited_by_account_id nullable
- invited_by_tenant_account_id nullable
- invited_by_group_class_member_id nullable
- created_at
- updated_at
```

Allowed `target_role` values:

```text
TENANT_ADMIN
PROFESSOR
STUDENT
```

Allowed `status` values:

```text
PENDING
ACCEPTED
EXPIRED
REVOKED
```

### Flow

1. **Development team** adds invitation persistence.
2. **Development team** adds invitation token generation.
3. **Development team** stores only a secure token hash.
4. **Development team** sends the raw token only through the invitation link.
5. **System** validates invitation token hash on acceptance.
6. **System** prevents reused accepted invitations.
7. **System** prevents expired invitations.
8. **System** creates the correct account, tenant account, role, and group-class membership on acceptance.

### Result

```text
Invitation links can support all role onboarding flows.
```

---

## Stage 18: Verify End-to-End Local Setup

### Purpose

Confirm the full setup works locally.

### Flow

1. **Development team** starts local services.
2. **Development team** runs the application.
3. **System** starts successfully.
4. **Development team** logs in as system admin.
5. **System Admin** creates a tenant.
6. **System Admin** invites tenant admin.
7. **Development team** opens Mailpit.
8. **Tenant Admin** accepts invite and registers.
9. **Tenant Admin** creates period, subject, and group class.
10. **Tenant Admin** invites professor.
11. **Professor** accepts invite.
12. **Professor** invites student.
13. **Student** accepts invite.
14. **Student** enters the student workspace.
15. **Development team** verifies role-based routing and UI navigation.

### Result

```text
The local system can be set up end-to-end from system admin to student using Mailpit invitations.
```

---

# Alternative Flows

---

## AF-1: User Is Not Authenticated

**Branches from:** Stage 4  
**Condition:** The user opens any protected route without a valid session.

1. **System** redirects the user to `/login`.
2. **System** does not show protected workspace content.
3. **Use case continues** after login.

---

## AF-2: Invalid Credentials

**Branches from:** Stage 4  
**Condition:** User enters invalid credentials.

1. **System** rejects login.
2. **System** shows an error message.
3. **System** keeps the user on `/login`.
4. **Use case ends for that attempt**.

---

## AF-3: Authenticated User Has No Role

**Branches from:** Stage 5  
**Condition:** Account exists but has no tenant account, role, or group-class membership.

1. **System** shows a no-access state.
2. **System** explains that the account must be invited or assigned.
3. **System** does not route the user to any workspace.
4. **Use case ends for that account state**.

---

## AF-4: User Has Multiple Tenants

**Branches from:** Stage 5, Stage 8  
**Condition:** User has access to more than one tenant.

1. **System** shows all accessible tenants in the Discord-like tenant sidebar.
2. **System** selects the first tenant by default.
3. **User** may switch tenant context.
4. **System** updates the traditional sidebar and dashboard.
5. **Use case continues**.

---

## AF-5: User Has Multiple Group Classes

**Branches from:** Stage 13 or Stage 16  
**Condition:** Professor or student belongs to multiple group classes.

1. **System** shows all accessible group classes in the Discord-like context sidebar.
2. **System** selects the first group class by default.
3. **User** may switch group class.
4. **System** updates all class-scoped views.
5. **Use case continues**.

---

## AF-6: Invitation Email Already Has an Account

**Branches from:** Stage 7, Stage 12, Stage 15  
**Condition:** Invited email already belongs to an existing account.

1. **System** does not show registration.
2. **System** prompts the user to log in.
3. **User** logs in.
4. **System** validates that the logged-in email matches the invitation email.
5. **System** accepts the invitation.
6. **Use case continues**.

---

## AF-7: Invitation Email Has No Account

**Branches from:** Stage 7, Stage 12, Stage 15  
**Condition:** Invited email does not belong to an existing account.

1. **System** shows registration.
2. **User** creates account.
3. **System** validates account email against invitation email.
4. **System** accepts the invitation.
5. **Use case continues**.

---

## AF-8: Invitation Token Is Invalid

**Branches from:** Stage 7, Stage 12, Stage 15  
**Condition:** Invitation token does not exist or does not match the stored token hash.

1. **System** rejects the invitation.
2. **System** shows invalid invitation state.
3. **System** does not create account, tenant account, role, or group-class membership.
4. **Use case ends**.

---

## AF-9: Invitation Token Expired

**Branches from:** Stage 7, Stage 12, Stage 15  
**Condition:** Invitation is expired.

1. **System** rejects the invitation.
2. **System** shows expired invitation state.
3. **System** does not accept the invitation.
4. **Use case ends**.

---

## AF-10: Invitation Already Accepted

**Branches from:** Stage 7, Stage 12, Stage 15  
**Condition:** Invitation status is already `ACCEPTED`.

1. **System** blocks reuse.
2. **System** shows already accepted state.
3. **System** does not create duplicate memberships.
4. **Use case ends**.

---

## AF-11: Tenant Admin Creates Duplicate Subject

**Branches from:** Stage 10  
**Condition:** Subject code already exists for the tenant.

1. **System** rejects the form.
2. **System** shows validation error.
3. **System** does not create duplicate subject.
4. **Use case returns to the subject form**.

---

## AF-12: Tenant Admin Creates Group Class Without Period or Subject

**Branches from:** Stage 11  
**Condition:** Tenant has no academic period or subject.

1. **System** prevents group-class creation.
2. **System** asks tenant admin to create required period and subject first.
3. **Use case returns to tenant setup**.

---

## AF-13: Professor Tries to Manage Class Without Membership

**Branches from:** Stage 13, Stage 14, Stage 15  
**Condition:** Professor attempts to access a group class where they are not an active professor member.

1. **System** denies access.
2. **System** does not show class details.
3. **System** does not allow student invitations.
4. **Use case ends for that request**.

---

## AF-14: Student Tries to Access Class Without Invitation

**Branches from:** Stage 16  
**Condition:** Student attempts to access a group class without membership.

1. **System** denies access.
2. **System** does not create membership.
3. **System** shows no-access state.
4. **Use case ends for that request**.

---

## AF-15: Mailpit Is Not Running

**Branches from:** Stage 1, Stage 2, Stage 7, Stage 12, Stage 15  
**Condition:** Application cannot connect to Mailpit SMTP.

1. **System** fails email sending safely.
2. **System** records the invitation as not delivered or pending.
3. **System** logs the local SMTP error.
4. **Development team** starts or fixes Mailpit.
5. **Use case continues after retry**.

---

## AF-16: Email Template Cannot Render

**Branches from:** Stage 2, Stage 7, Stage 12, Stage 15  
**Condition:** Email template rendering fails because the template is missing or required model data is missing.

1. **System** does not send the email.
2. **System** leaves the invitation in a safe pending or failed-delivery state.
3. **System** logs the template rendering failure.
4. **Development team** fixes the template or model.
5. **Use case continues after retry**.

---

# Postconditions

---

## On Success

- The application shows login first for unauthenticated users.
- Authenticated users are resolved to an `account`.
- The system resolves tenant accounts, roles, and group-class memberships.
- Mailpit is available for local invitation testing.
- The application has a reusable email architecture.
- The application can render HTML invitation emails from templates.
- The application can send local invitation emails through Mailpit.
- System admins can create tenants.
- System admins can invite tenant admins through Mailpit.
- Tenant admins can accept invitations and access tenant admin workspace.
- Tenant admins can create academic periods.
- Tenant admins can create subjects.
- Tenant admins can create group classes.
- Tenant admins can invite professors.
- Professors can accept invitations and access professor workspace.
- Professors can see their group classes.
- Professors can manage students inside their group classes.
- Professors can invite students.
- Students can accept invitations and access student workspace.
- Students can access only their own allowed group-class context.
- UI navigation reflects role and active context.
- The app no longer behaves like a single anonymous chat interface.

---

## On Failure

- The user remains unauthenticated or unassigned.
- No unauthorized tenant, role, group-class, or membership is created.
- Invalid invitations do not create accounts or memberships.
- Expired invitations cannot be reused.
- Missing Mailpit does not corrupt invitation state.
- Email template failures do not accept invitations.
- Email delivery failures do not create invalid accepted invitations.
- Users without role context see a safe no-access state.
- Protected routes remain protected.

---

# Business Rules

| ID | Rule |
|----|------|
| BR-01 | Login must be the first screen for unauthenticated users. |
| BR-02 | Workspace routing must happen after resolving the authenticated account. |
| BR-03 | `SYSTEM_ADMIN` routes to the system admin workspace. |
| BR-04 | `TENANT_ADMIN` routes to the tenant admin workspace. |
| BR-05 | `PROFESSOR` routes to the professor workspace. |
| BR-06 | `STUDENT` routes to the student workspace. |
| BR-07 | A system admin can create tenants. |
| BR-08 | A system admin can invite tenant admins. |
| BR-09 | Tenant admin invitations must be email-token based. |
| BR-10 | Local invitation emails must be delivered through Mailpit in development. |
| BR-11 | Invitation tokens must be secure and stored as hashes. |
| BR-12 | A user without an account can register through a valid invitation. |
| BR-13 | A user with an existing account must log in to accept an invitation. |
| BR-14 | The account email must match the invited email unless a later use case explicitly allows reassignment. |
| BR-15 | A tenant admin can create periods only inside their tenant. |
| BR-16 | A tenant admin can create subjects only inside their tenant. |
| BR-17 | A tenant admin can create group classes only inside their tenant. |
| BR-18 | A tenant admin can invite professors to group classes inside their tenant. |
| BR-19 | A professor can see only group classes where they are an active member. |
| BR-20 | A professor can invite students only to group classes where they are an active professor member. |
| BR-21 | A professor can manage students only inside their own group classes. |
| BR-22 | Removing a student from a group class must be logical removal or locking, not global account deletion. |
| BR-23 | A student can enter a group class only through invitation, assignment, or approved join mechanism. |
| BR-24 | A student cannot create tenants, periods, subjects, or group classes. |
| BR-25 | The Discord-like sidebar represents available context, not global navigation. |
| BR-26 | For tenant admins, the context sidebar shows tenants/universities. |
| BR-27 | For professors, the context sidebar shows group classes. |
| BR-28 | The first available context may be selected by default. |
| BR-29 | The traditional sidebar shows actions available inside the selected context. |
| BR-30 | The UI must not show actions that the current role cannot perform. |
| BR-31 | Invitations must not be reusable after acceptance. |
| BR-32 | Expired invitations must not be accepted. |
| BR-33 | The system must fail safely when Mailpit is unavailable. |
| BR-34 | The application must remain aligned with the UC-001 identity and academic hierarchy. |
| BR-35 | Development email delivery must use Mailpit. |
| BR-36 | SMTP settings must be configurable through environment variables. |
| BR-37 | Invitation services must not send raw SMTP messages directly. |
| BR-38 | Email sending must go through an `EmailService` abstraction. |
| BR-39 | HTML email content must be rendered through an `EmailTemplateService`. |
| BR-40 | Invitation emails must use templates, not hard-coded string bodies. |
| BR-41 | The raw invitation token must only appear in the email link and must not be stored directly. |
| BR-42 | Stored invitation tokens must be hashed. |
| BR-43 | Mailpit unavailability must not create invalid tenant accounts, memberships, or accepted invitations. |
| BR-44 | Email delivery failure must leave the invitation in a safe pending or failed-delivery state. |
| BR-45 | Invitation acceptance must behave as a controlled multi-step onboarding flow. |
| BR-46 | Invitation tokens must be single-use. |
| BR-47 | Invitation tokens must expire. |
| BR-48 | Invitation metadata may carry tenant, group-class, target-role, and invited-email context. |
| BR-49 | Session-scoped onboarding context may carry temporary invitation data but must not be the source of truth. |
| BR-50 | Persisted invitation state remains the source of truth during onboarding. |
| BR-51 | A newly invited user may access only onboarding routes before role context is fully created. |
| BR-52 | Temporary onboarding access must not bypass protected application workspaces. |
| BR-53 | Post-login routing must evaluate account, tenant-account, role, and group-class membership. |
| BR-54 | Users with no role or accepted invitation must see a no-access or pending-invitation state. |
| BR-55 | Invitation acceptance must clear temporary onboarding session context after completion. |
| BR-56 | The system may publish onboarding or invitation events for audit, monitoring, or later abandoned-invitation handling. |

---

## Invited Registration Field Rules

| ID | Rule |
|----|------|
| BR-57 | Registration must be invitation-only. Open self-signup is not allowed. |
| BR-58 | The invited email must be read-only during registration and becomes the account email. |
| BR-59 | Invited registration requires first name, last name, password, and confirm password. |
| BR-60 | `password_hash` must be generated with `PasswordEncoder`. |
| BR-61 | `username` must be auto-generated from the invited email local part, lowercased, invalid characters normalized or replaced, and made unique with deterministic numeric suffixes such as `manuel2`, `manuel3`. |
| BR-62 | If the invited email already belongs to an account, the system must not show registration and must require login instead. |
| BR-63 | Existing-account invitation acceptance succeeds only when the logged-in account email matches the invited email. |
| BR-64 | Invited onboarding must not allow changing the invited email while registration or acceptance is in progress. |

---

# UI Surface

| Surface | Access | Entry Point | UI Expectations |
|--------|--------|-------------|-----------------|
| Login | Anonymous | `/login` | First screen for unauthenticated users. |
| System Admin Workspace | `SYSTEM_ADMIN` | `/admin` or role-based redirect | Create tenants and invite tenant admins. |
| Tenant Admin Workspace | `TENANT_ADMIN` | role-based redirect | Discord-like tenant selector plus traditional sidebar for dashboard, periods, subjects, group classes, and invitations. |
| Professor Workspace | `PROFESSOR` | role-based redirect | Discord-like group-class selector plus traditional sidebar for home, new chat, formative activities, document ingestion, and students. |
| Student Workspace | `STUDENT` | role-based redirect | Simple learning workspace with new chat and assigned activities. |
| Mailpit UI | Development team | `http://localhost:8025` | Inspect local invitation emails. |
| Invitation Acceptance | Invited user | `/invitations/accept?token=...` | Accept invitation, register or log in, then enter correct workspace. |

---

# Visual Notes From Sketches

The provided sketches suggest the following UI direction:

- Login must appear before any workspace.
- The app must determine who the logged-in user is.
- The shown page depends on role and context.
- System admin creates tenants and sends invitation links.
- Tenant admin sees universities/tenants as circular context items.
- Tenant admin creates:
  - periods
  - subjects
  - group classes
- Tenant admin invites professors through email.
- Professor sees group classes as circular context items.
- Professor has a traditional sidebar for:
  - home
  - new chat
  - formative activities
  - document ingestion
- Professor dashboard includes group-class cards.
- Professor sees a student table for the selected group class.
- Student rows include actions such as edit and remove/disable.
- Professor can invite students from the student table.
- Student workspace is simpler and focuses on consuming chat and assigned evaluations.

---

# Technical Notes

## Onboarding Reference Implementation Pattern

UC-003 should follow a proven multi-step onboarding pattern instead of implementing invitation acceptance as a fragile one-request operation.

The reference pattern contains the following reusable ideas:

```text
1. Multi-step onboarding flow
2. Persisted state flags or invitation statuses
3. Session-scoped context for temporary onboarding data
4. Single-use token links with expiration
5. Token/invitation metadata for contextual information
6. Post-login routing based on account state and role context
7. Temporary onboarding access before full role assignment
8. Event-driven notifications or audit events
9. Optional abandoned-signup / expired-invitation cleanup
```

### Multi-Step Onboarding

The system should not treat signup or invitation acceptance as one isolated form.

The intended path is:

```text
/invitations/accept?token=...
  -> validate token
  -> store temporary onboarding context
  -> register or login
  -> create account / tenant_account / role / group_class_member
  -> mark invitation accepted
  -> clear temporary context
  -> redirect to workspace
```

This mirrors a robust production pattern where signup is a controlled sequence rather than a single endpoint.

### Persisted State

The persisted invitation should behave like a small state machine.

Recommended statuses:

```text
PENDING
ACCEPTED
EXPIRED
REVOKED
DELIVERY_FAILED
```

The system may also add explicit account onboarding fields later, but UC-003 should not require a complex onboarding-state table unless needed. The invitation record is enough for this use case.

### Session-Scoped Onboarding Context

The system should use a temporary session-scoped context to carry validated invitation data between steps.

Recommended class responsibility:

```text
OnboardingFlowContext
- stores validated invitation id
- stores invited email
- stores target role
- stores tenant id
- stores group class id when applicable
- stores intended post-accept redirect
- clears itself after completion
```

This is useful because an invited user may need to move through token validation, registration, login, password creation, and final redirect.

The session context must not replace the database. It only reduces repeated token parsing and keeps the flow coherent across screens.

### Single-Use Token and Metadata

Invitation links should behave like single-use magic links.

The raw token appears only in the URL sent by email.

The database stores only a token hash.

The invitation record should store contextual data directly or as structured metadata.

Useful metadata includes:

```text
tenant_id
group_class_id
target_role
invited_email
invitation_source
post_accept_redirect
```

This prevents the application from relying on hidden UI state or fragile query parameters after the token has been validated.

### Post-Login Routing

After authentication, routing should inspect the account state and academic role context.

Recommended routing logic:

```text
if account.system_admin == true:
    route to system admin workspace
else if account has TENANT_ADMIN role:
    route to tenant admin workspace
else if account has PROFESSOR group-class membership:
    route to professor workspace
else if account has STUDENT group-class membership:
    route to student workspace
else if onboarding context exists:
    continue onboarding
else:
    route to no-access state
```

This routing may live in a Vaadin route guard, layout before-enter hook, authentication success handler, or a dedicated workspace router service. The important point is that routing is based on resolved domain context, not hard-coded pages.

### Temporary Onboarding Access

A user in the middle of accepting an invitation may not yet have a tenant role or group-class membership.

The system may allow temporary access to onboarding routes only:

```text
/login
/register
/invitations/accept
/onboarding/*
```

This is not a general permission bypass.

It exists only so invited users can complete the flow that creates their final role context.

### Events and Monitoring

The implementation may publish events for major onboarding milestones:

```text
InvitationCreatedEvent
InvitationEmailSentEvent
InvitationAcceptedEvent
InvitationExpiredEvent
OnboardingCompletedEvent
OnboardingFailedEvent
```

These events can support logging, audit, local testing, future monitoring, and later abandoned-invitation workflows.

### What This Project Adds Beyond the Reference Pattern

The referenced production-style onboarding pattern is useful, but UC-003 goes further.

UC-003 must support a full academic invitation hierarchy:

```text
SYSTEM_ADMIN -> TENANT_ADMIN
TENANT_ADMIN -> PROFESSOR
PROFESSOR -> STUDENT
```

It must also create the correct Socratic Tutor domain records:

```text
account
tenant_account
tenant_account_role
group_class_member
```

The reference pattern informs the implementation, but the Socratic Tutor ERD remains the source of truth.

---

## Email and Mailpit Reference Implementation Pattern

This use case should implement local invitation email delivery using the same proven pattern used in a production-style Spring application with Mailpit in development/testing.

The goal is not only to add Mailpit as a container, but to create a clean email architecture that can work locally with Mailpit and later be redirected to a real SMTP provider.

### Mailpit Docker Compose Service

The local Docker Compose setup should include Mailpit as a development SMTP server and webmail inbox.

Recommended service:

```yaml
mailpit:
  image: axllent/mailpit:latest
  container_name: socratic-tutor-mailpit
  volumes:
    - db_mailpit:/data
  ports:
    - "8025:8025"
    - "1025:1025"
  environment:
    MP_MAX_MESSAGES: 5000
    MP_DATABASE: /data/mailpit.db
    MP_SMTP_AUTH_ACCEPT_ANY: 1
    MP_SMTP_AUTH_ALLOW_INSECURE: 1
```

Recommended volume:

```yaml
volumes:
  db_mailpit:
```

Purpose:

```text
Port 1025 is used by the application as SMTP.
Port 8025 is used by developers to inspect captured emails.
Mailpit persists messages in a Docker volume.
Mailpit accepts insecure local SMTP auth for development convenience.
```

### Email Configuration

The application should use application-specific email properties instead of hard-coding SMTP settings.

Recommended configuration namespace:

```yaml
socratic:
  email:
    enabled: ${SOCRATIC_EMAIL_ENABLED:true}
    logo: ${SOCRATIC_EMAIL_LOGO:}
    from:
      address: ${EMAIL_FROM_ADDRESS:no-reply@socratic-tutor.local}
      name: ${EMAIL_FROM_NAME:Socratic Tutor}
    smtp:
      enabled: ${EMAIL_SMTP_ENABLED:true}
      host: ${EMAIL_SMTP_HOST:localhost}
      port: ${EMAIL_SMTP_PORT:1025}
      username: ${EMAIL_SMTP_USERNAME:}
      password: ${EMAIL_SMTP_PASSWORD:}
      auth: ${EMAIL_SMTP_AUTH:false}
      starttls: ${EMAIL_SMTP_STARTTLS:false}
```

For the `dev` profile, Mailpit should be the default SMTP target:

```yaml
socratic:
  email:
    smtp:
      enabled: true
      host: localhost
      port: 1025
      username: ""
      password: ""
      auth: false
      starttls: false
```

Environment variables should allow later replacement with a real SMTP provider:

```text
EMAIL_SMTP_HOST
EMAIL_SMTP_PORT
EMAIL_SMTP_USERNAME
EMAIL_SMTP_PASSWORD
EMAIL_FROM_ADDRESS
EMAIL_FROM_NAME
SOCRATIC_EMAIL_LOGO
```

### Email Service Interfaces

The implementation should define a small email abstraction instead of sending emails directly from invitation services.

Recommended interface:

```java
public interface EmailService {

    void sendEmail(EmailMessage emailMessage) throws EmailSendException;
}
```

Recommended email message model:

```java
public class EmailMessage {

    private Set<String> to;
    private String from;
    private String subject;
    private String body;
    private Set<Path> attachments;
}
```

Recommended higher-level templated message model:

```java
public class TemplatedEmailMessage {

    private Set<String> to;
    private String from;
    private String subject;
    private String templateId;
    private Map<String, Object> model;
}
```

Recommended exception:

```java
public class EmailSendException extends Exception {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### SMTP Implementation

The SMTP implementation may use Jakarta Mail directly through Eclipse Angus Mail.

Recommended behavior:

```text
Read SMTP host, port, username, password, auth, and starttls from configuration.
Build a Jakarta Mail Session.
Build a MimeMessage.
Send HTML emails.
Support optional attachments.
Send through Transport.send(message).
Throw EmailSendException on failure.
```

Recommended implementation class:

```text
com.wornux.services.email.impl.SmtpEmailService
```

Recommended conditional activation:

```text
The SMTP email implementation should only be active when socratic.email.smtp.enabled=true.
```

This prevents email infrastructure from breaking tests or local runs when email is intentionally disabled.

### Template Rendering

The implementation should support HTML email templates.

Recommended interface:

```java
public interface EmailTemplateService {

    String render(String templateId, Map<String, Object> model);
}
```

Recommended implementation:

```text
ThymeleafEmailTemplateService
```

Recommended template location:

```text
src/main/resources/templates/email/
```

Recommended invitation templates:

```text
src/main/resources/templates/email/invitation/tenant-admin-invitation.html
src/main/resources/templates/email/invitation/professor-invitation.html
src/main/resources/templates/email/invitation/student-invitation.html
```

The template renderer should inject shared model variables such as:

```text
appName
appUrl
logo
environment
currentDate
supportEmail
```

Invitation templates should receive:

```text
invitedEmail
invitationLink
targetRole
tenantName
groupClassName
expiresAt
```

### Invitation Email Service

Invitation creation should not send raw SMTP messages directly.

Recommended flow:

```text
InvitationService creates invitation and secure token.
InvitationEmailService builds the invitation link.
InvitationEmailService renders the correct template.
EmailService sends the rendered HTML email.
Mailpit captures the message locally.
```

Recommended service split:

```text
InvitationService
- creates invitation records
- validates invitation tokens
- accepts invitations
- creates account / tenant_account / group_class_member records

InvitationEmailService
- builds invitation email models
- renders invitation templates
- delegates actual sending to EmailService

EmailService
- sends already-rendered email messages

EmailTemplateService
- renders HTML templates
```

### Build Dependency

If the project does not use `spring-boot-starter-mail`, the recommended dependency is Eclipse Angus Mail:

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
</dependency>
```

If a version is required explicitly:

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
    <version>2.0.1</version>
</dependency>
```

For HTML template rendering, Thymeleaf should be available:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

### Local Email Verification

The development team must be able to verify invitation delivery locally:

```text
1. Start Docker Compose.
2. Open Mailpit at http://localhost:8025.
3. Trigger a tenant admin, professor, or student invitation.
4. Confirm the email appears in Mailpit.
5. Open the invitation email.
6. Click the invitation link.
7. Complete registration or login.
8. Confirm the expected role and membership are created.
```

---

## Suggested New Data Model

UC-001 already includes `group_class_join_code`, but this use case needs email-based invitations for multiple roles.

A generic `invitation` table is recommended:

```text
invitation
- id
- tenant_id
- group_class_id nullable
- invited_email
- target_role
- token_hash
- status
- expires_at
- accepted_at
- invited_by_account_id nullable
- invited_by_tenant_account_id nullable
- invited_by_group_class_member_id nullable
- created_at
- updated_at
```

This supports:

```text
SYSTEM_ADMIN -> TENANT_ADMIN
TENANT_ADMIN -> PROFESSOR
PROFESSOR -> STUDENT
```

## Role-Based Landing Logic

Recommended priority:

```text
SYSTEM_ADMIN
TENANT_ADMIN
PROFESSOR
STUDENT
```

If a user has multiple contexts, the system should route to the highest role by default while allowing context switching where appropriate.

## Context Sidebar Rules

Tenant admin:

```text
Discord-like sidebar = tenants/universities
Traditional sidebar = tenant actions
```

Professor:

```text
Discord-like sidebar = group classes
Traditional sidebar = group-class actions
```

Student:

```text
Simpler sidebar = learning actions
```

---

# Tests

- [ ] Stage 1 adds Mailpit to local Docker Compose.
- [ ] Stage 1 configures application email settings for Mailpit in dev.
- [ ] Stage 2 creates an `EmailService` abstraction.
- [ ] Stage 2 creates an SMTP email implementation.
- [ ] Stage 2 creates an `EmailTemplateService` abstraction.
- [ ] Stage 2 creates a Thymeleaf email template implementation.
- [ ] Stage 2 creates tenant admin invitation template.
- [ ] Stage 2 creates professor invitation template.
- [ ] Stage 2 creates student invitation template.
- [ ] Stage 3 defines invitation acceptance as a multi-step onboarding flow.
- [ ] Stage 3 validates a single-use invitation token.
- [ ] Stage 3 rejects an expired invitation token.
- [ ] Stage 3 stores only token hashes in the database.
- [ ] Stage 3 stores validated invitation data in a session-scoped onboarding context.
- [ ] Stage 3 routes invited users with no account to registration.
- [ ] Stage 3 routes invited users with an existing account to login.
- [ ] Stage 3 verifies authenticated account email matches invited email.
- [ ] Stage 3 creates or reuses account, tenant account, role, and group-class membership as required.
- [ ] Stage 3 clears onboarding session context after acceptance.
- [ ] Stage 3 routes accepted users to the correct workspace.
- [ ] Stage 4 redirects unauthenticated users to login.
- [ ] Stage 4 authenticates a valid account.
- [ ] Stage 4 rejects invalid credentials.
- [ ] Stage 5 resolves account context.
- [ ] Stage 5 resolves tenant accounts.
- [ ] Stage 5 resolves roles.
- [ ] Stage 5 resolves group-class memberships.
- [ ] Stage 5 routes system admin to system admin workspace.
- [ ] Stage 5 routes tenant admin to tenant admin workspace.
- [ ] Stage 5 routes professor to professor workspace.
- [ ] Stage 5 routes student to student workspace.
- [ ] Stage 6 creates tenant as system admin.
- [ ] Stage 7 sends tenant admin invitation to Mailpit.
- [ ] Stage 7 accepts tenant admin invitation with new account.
- [ ] Stage 7 accepts tenant admin invitation with existing account.
- [ ] Stage 8 shows tenant admin workspace.
- [ ] Stage 8 shows tenant context sidebar.
- [ ] Stage 9 creates academic period.
- [ ] Stage 10 creates subject.
- [ ] Stage 11 creates group class.
- [ ] Stage 12 sends professor invitation to Mailpit.
- [ ] Stage 12 creates professor group-class membership after acceptance.
- [ ] Stage 13 shows professor workspace.
- [ ] Stage 13 selects first professor group class by default.
- [ ] Stage 14 lists students for selected group class.
- [ ] Stage 14 disables/removes student membership logically.
- [ ] Stage 15 sends student invitation to Mailpit.
- [ ] Stage 15 creates student group-class membership after acceptance.
- [ ] Stage 16 shows student workspace.
- [ ] Stage 16 prevents student from accessing unassigned group class.
- [ ] Stage 17 persists invitation token hash instead of raw token.
- [ ] Mailpit SMTP port is exposed on `1025`.
- [ ] Mailpit web UI is exposed on `8025`.
- [ ] SMTP settings can be overridden through environment variables.
- [ ] SMTP email implementation sends HTML email through configured SMTP host.
- [ ] Invitation emails appear in Mailpit during local development.
- [ ] Invitation link contains raw token only in the URL.
- [ ] Database stores only token hash.
- [ ] Invitation is not accepted if email sending fails before delivery is recorded.
- [ ] AF-1 through AF-16 are covered.
- [ ] BR-01 through BR-44 are covered.
