# UC-003: Training Activity Lifecycle

---

**Goal:** As a professor, I want to create, review, edit, publish, close, and inspect formative activities in my active class so that students receive a stable evaluation brief and I can monitor their progress safely.

**Status:** Pending  
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor or another authorized training activity manager
- **Secondary actors:** AI instruction review service through UC-006; publication and delivery workflow through UC-008

---

## Preconditions

- The professor is authenticated.
- A valid active group-class context is selected.
- The professor has permission to view and manage training activities in that class.
- SPEC-005 persistence and concurrency rules are available.

---

## Trigger

The professor opens `/training-activities`, creates a new activity, or opens an existing grid row.

---

## Main Flow

1. Professor opens the formative activities screen.
2. System loads activities belonging to the active group class and displays their title, status, publication window, and student progress summary.
3. Professor clicks **Nueva actividad**.
4. System opens an editable draft form with title, instructions, and Safe Browser configuration.
5. Professor enters a nonblank title and nonblank instructions.
6. Professor clicks **Guardar borrador**.
7. System performs deterministic field, permission, and class-scope validation.
8. System follows UC-006 to obtain or display advisory instruction feedback without treating the AI result as an absolute authority.
9. If the review is favorable, or the professor explicitly confirms **Guardar de todos modos**, system persists the activity as `DRAFT` in a short transaction.
10. System closes the form and displays the draft in the grid.
11. Professor double-clicks the activity row.
12. System opens the activity detail and shows its title, instructions, lifecycle status, configuration, and version-appropriate actions.
13. Because the activity is `DRAFT`, system allows the professor to edit its definition.
14. Professor modifies the draft and saves it.
15. System validates the expected version, follows UC-006 when instructions changed, and persists one updated draft version.
16. Professor chooses **Publicar actividad**.
17. System delegates publication, assignment creation, dashboard delivery, and notifications to UC-008.
18. System refreshes the detail as a read-only `PUBLISHED` activity and displays the assigned students and their current statuses.
19. Professor may return later and open the same activity from the grid.
20. System shows assignment progress, Safe Browser incidents through UC-005, and available reports through UC-009 without allowing the published definition to change.
21. Professor closes the activity when students must no longer start or continue it.
22. System asks for confirmation, changes the activity to `CLOSED`, records `closed_at`, and makes nonterminal assignments non-answerable according to SPEC-005.
23. Professor may archive the closed activity while retaining assignments, turns, incidents, and reports.

---

## Alternative Flows

### AF-1: Missing permission or invalid class context

**Branches from:** Main Flow step 1, 6, 11, 16, 21, or 23  
**Condition:** The user lacks permission or the activity is outside the active authorized class.

1. System denies the operation without revealing restricted activity data.
2. System shows a no-access or context-selection state.
3. No activity state changes.
4. Use case ends or returns after a valid context is selected.

### AF-2: Required title or instructions are blank

**Branches from:** Main Flow step 7 or 15  
**Condition:** Title or instructions are null, empty, or whitespace-only after normalization.

1. System rejects the save using deterministic validation.
2. System shows field-level errors.
3. System does not request AI review and does not persist the invalid definition.
4. Professor corrects the fields.
5. Returns to Main Flow step 5 or 14.

### AF-3: AI review recommends changes

**Branches from:** Main Flow step 8 or 15  
**Condition:** Current instruction review returns `NEEDS_IMPROVEMENT` or `INVALID`.

1. System shows the review diagnostics and proposed improvement.
2. Professor may edit or apply a suggestion and request another review.
3. Professor may instead choose **Guardar de todos modos**.
4. System asks for explicit confirmation and records the acknowledged review/hash.
5. On confirmation, flow returns to Main Flow step 9 or 15.

### AF-4: AI review is unavailable

**Branches from:** Main Flow step 8 or 15  
**Condition:** Review times out, fails validation, or the model is unavailable.

1. System shows that advisory review is temporarily unavailable.
2. Professor may retry, continue editing, or choose **Guardar sin revisión**.
3. On explicit confirmation, system records an override for the current instructions hash.
4. Flow returns to Main Flow step 9 or 15.

### AF-5: Concurrent draft edit

**Branches from:** Main Flow step 15  
**Condition:** The persisted activity version changed after the professor opened the form.

1. System rejects the stale update.
2. System preserves the professor's unsaved text in the UI.
3. System asks the professor to reload the latest activity and reconcile the changes.
4. No later version is overwritten.
5. Use case ends or returns to Main Flow step 11.

### AF-6: Professor attempts to edit a published, closed, or archived activity

**Branches from:** Main Flow step 12 or 20  
**Condition:** Activity status is not `DRAFT`.

1. System renders the definition read-only.
2. Backend rejects any forged update command.
3. Existing assignments and evidence remain unchanged.
4. Professor may use only actions valid for the current lifecycle state.

### AF-7: Professor deletes a draft

**Branches from:** Main Flow step 12  
**Condition:** Activity is `DRAFT` and has never been published.

1. Professor clicks **Eliminar borrador**.
2. System asks for destructive-action confirmation.
3. Professor confirms.
4. System verifies status, permission, and version, then physically deletes the draft and dependent draft-only reviews.
5. System removes the row from the grid.
6. Use case ends.

### AF-8: Professor attempts to delete historical activity data

**Branches from:** Main Flow step 20 or 23  
**Condition:** Activity was published or has assignments/evidence.

1. System does not offer physical deletion as the normal action.
2. Backend rejects a forged delete command.
3. System offers close or archive where valid.
4. Assignments, turns, incidents, and reports remain intact.
5. Use case ends or returns to the detail.

### AF-9: Activity is already closed or archived

**Branches from:** Main Flow step 21 or 23  
**Condition:** A duplicate or stale lifecycle command is submitted.

1. System treats an identical already-applied command idempotently or rejects an invalid transition.
2. System refreshes the latest state.
3. No duplicate lifecycle side effects are produced.
4. Use case ends.

### AF-10: Detail has no assigned students yet

**Branches from:** Main Flow step 12  
**Condition:** Activity is still a draft.

1. System shows a draft-specific empty state instead of a misleading student grid.
2. System explains that assignments are created at publication.
3. Professor may continue editing or publish.

---

## Postconditions

- **On draft save:** One valid, class-scoped `training_activity` exists as `DRAFT`; its latest definition and optimistic version are persisted; any decision to proceed despite AI advice is auditable.
- **On publication:** UC-008 owns the atomic transition and assignment delivery; the activity definition becomes read-only.
- **On close/archive:** Students cannot continue nonterminal assignments after closure; historical activity and evaluation evidence remain available to authorized professors.
- **On failure:** Deterministic invalid data, unauthorized access, stale writes, and invalid lifecycle transitions do not change persisted state.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Every activity belongs to exactly one group class and is managed only through an authorized active context. |
| BR-02 | Title and instructions are required and must contain non-whitespace text; AI is not used to enforce these deterministic requirements. |
| BR-03 | A newly saved activity is `DRAFT`. |
| BR-04 | Title, instructions, Safe Browser configuration, and scheduling fields are editable only while `DRAFT`. |
| BR-05 | AI instruction review is advisory; the professor may save after explicit confirmation as defined by UC-006. |
| BR-06 | AI failure must not make draft persistence permanently unavailable. |
| BR-07 | Published activity definitions are immutable so every assigned student is evaluated against the same brief. |
| BR-08 | The supported transitions are `DRAFT -> PUBLISHED -> CLOSED -> ARCHIVED`, plus `PUBLISHED -> ARCHIVED` only after explicit close semantics are applied. |
| BR-09 | Only an unpublished draft may be physically deleted through normal product behavior. |
| BR-10 | Published or historical activities use close/archive and preserve assignments, turns, reports, and Safe Browser audit history. |
| BR-11 | Closing an activity makes every nonterminal assignment non-answerable; submitted assignments remain submitted. |
| BR-12 | Every update and lifecycle command validates the expected optimistic version. |
| BR-13 | Double-clicking a grid row opens detail; edit controls are present only for drafts, but backend lifecycle checks remain authoritative. |
| BR-14 | Student assignment delivery, email, and dashboard behavior belong to UC-008, not to the draft-save transaction. |
| BR-15 | There is no global rule limiting a professor to only one published activity unless a later approved use case introduces such a rule. |

---

## Tests

- [ ] Main Flow covered from grid load through draft, detail, publication, close, and archive.
- [ ] AF-1 authorization and class scope covered.
- [ ] AF-2 deterministic blank validation covered.
- [ ] AF-3 and AF-4 advisory override paths covered.
- [ ] AF-5 optimistic concurrency covered.
- [ ] AF-6 immutable published definition covered in UI and service tests.
- [ ] AF-7 draft deletion covered.
- [ ] AF-8 historical data preservation covered.
- [ ] AF-9 lifecycle idempotency and invalid transitions covered.
- [ ] AF-10 draft assignment empty state covered.
- [ ] BR-01 through BR-15 covered.

---

## UI Surface

- Activity grid with class-scoped rows, lifecycle badges, progress summary, and double-click detail.
- Draft create/edit dialog with title, instructions, Safe Browser setting, review state, save, publish, and delete actions.
- Published/closed detail with immutable definition, student assignment statuses, incident summary, and report access.
- Explicit confirmation dialogs for save despite AI advice, destructive draft deletion, close, and archive.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Training activity grid | Authenticated authorized professor | `/training-activities` |
| Draft create/edit | Authenticated authorized professor | **Nueva actividad** or draft grid row |
| Published activity detail | Authenticated authorized professor/reviewer | Double-click published grid row or activity deep link |
