# UC-006: Advisory AI Instruction Quality Review

---

**Goal:** As a professor, I want fast, actionable AI feedback about my formative activity instructions while retaining the final decision so that I can improve the tutor brief without an unreliable model preventing my work.

**Status:** Pending  
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor or another authorized training activity manager
- **Secondary actors:** Instruction review worker and configured review model

---

## Preconditions

- The professor is authenticated and has a valid active class context.
- The professor can create or update the draft activity.
- The activity editor contains title and instructions fields.
- Deterministic field validation exists independently from AI review.
- SPEC-005 asynchronous job, review-history, and override persistence are available.

---

## Trigger

The professor requests instruction review, pauses after changing instructions if automatic review is enabled, saves a draft, or attempts to publish a draft whose current instructions do not have a favorable review.

---

## Main Flow

1. Professor opens a new or existing draft activity.
2. System displays the title, instructions editor, current review state, and review action.
3. Professor enters or modifies the instructions.
4. System immediately marks any previous review as stale for the edited instructions.
5. Professor clicks **Revisar instrucción**.
6. System performs deterministic validation and rejects blank title or instructions before any model work.
7. System creates an idempotent review request for the exact normalized instructions hash and returns a `REVIEWING` UI state without blocking the Vaadin request/session thread.
8. Review worker builds a controlled prompt using the title as context and the instructions as untrusted content.
9. Review model returns a structured candidate result.
10. Backend validates enums, required fields, diagnostic ranges, lengths, and safe display content.
11. System stores the validated immutable review result for the instructions hash and rubric version.
12. UI receives or polls the completed result and first verifies that its hash still matches the editor text.
13. If the result is `GOOD`, system shows a favorable, nonblocking verdict and any optional refinements.
14. If the result is `NEEDS_IMPROVEMENT` or `INVALID`, system highlights reliable issue ranges and shows what could improve, why it matters, and proposed replacement text.
15. Professor may apply a specific replacement or the full suggested instructions.
16. System changes only the professor-visible candidate text after explicit action and marks the previous review stale.
17. Professor may review again until satisfied.
18. Professor clicks **Guardar borrador**.
19. If the current review is `GOOD`, system allows UC-003 to save immediately.
20. If the current review recommends changes, professor clicks **Guardar de todos modos** in an explicit confirmation dialog.
21. System records an override for `SAVE_DRAFT`, actor, activity/candidate, and exact instructions hash.
22. UC-003 persists the professor's chosen instructions as the draft definition.

---

## Alternative Flows

### AF-1: Missing permission or invalid class context

**Branches from:** Main Flow step 1, 5, 18, or 20  
**Condition:** Professor cannot manage the draft in the active class.

1. System denies the operation.
2. No review request, override, or activity mutation is persisted.
3. System shows a no-access or context-selection state.
4. Use case ends.

### AF-2: Blank deterministic input

**Branches from:** Main Flow step 6 or 18  
**Condition:** Title or instructions are null, empty, or whitespace-only after normalization.

1. System displays field-level errors.
2. System does not call or enqueue the review model.
3. **Guardar de todos modos** is not offered for deterministic validation failures.
4. Professor corrects the fields.
5. Returns to Main Flow step 3.

### AF-3: Professor saves before a current review exists

**Branches from:** Main Flow step 18  
**Condition:** Review is missing, stale, still pending, or belongs to another instructions hash.

1. System explains that no current AI recommendation is available.
2. Professor may wait/request review, return to editing, or select **Guardar sin revisión**.
3. If professor selects **Guardar sin revisión**, system requests explicit confirmation.
4. System records a `SAVE_DRAFT` override with no review id and the exact instructions hash.
5. UC-003 saves the draft.

### AF-4: Review model timeout or unavailable

**Branches from:** Main Flow step 8 or 9  
**Condition:** The model is unavailable or exceeds the configured deadline.

1. Worker records the review request as failed with a safe error code.
2. UI leaves the editor responsive and shows a retryable unavailable state.
3. Professor may retry or follow AF-3 to save without review.
4. Existing draft data remains available.

### AF-5: Invalid model output

**Branches from:** Main Flow step 10  
**Condition:** Output is malformed, incomplete, unsafe, or violates the backend contract.

1. Backend rejects the candidate output.
2. System stores only failure metadata, not a trusted review verdict.
3. Raw model output is not rendered to the professor.
4. Professor may retry or save without review through AF-3.

### AF-6: Instructions change while review is running

**Branches from:** Main Flow step 7 or 12  
**Condition:** Editor text no longer matches the completed review hash.

1. System stores the historical review result but marks it stale for the current editor value.
2. System does not apply its ranges or suggestions to the changed text.
3. Professor may request a new review.
4. Returns to Main Flow step 5.

### AF-7: Duplicate review request

**Branches from:** Main Flow step 7  
**Condition:** The same actor/activity candidate, instructions hash, model policy, and rubric version already have pending or completed work.

1. System reuses the pending job or current validated result.
2. System does not schedule duplicate model work.
3. UI shows the shared current state.

### AF-8: Professor applies a suggestion

**Branches from:** Main Flow step 15  
**Condition:** Professor selects a range replacement or full rewrite.

1. System replaces only a reliable selected range when one exists.
2. A full rewrite requires explicit professor action and confirmation if it replaces all instructions.
3. System never autosaves model-proposed text.
4. Review becomes stale.
5. Returns to Main Flow step 17.

### AF-9: Professor cancels the override dialog

**Branches from:** Main Flow step 20  
**Condition:** Professor selects **Volver a editar** or closes the dialog.

1. System keeps the current editor text.
2. System does not record an override and does not save because of that attempt.
3. Professor may edit, apply suggestions, or review again.

### AF-10: Title changes without instruction changes

**Branches from:** Main Flow step 3 or 4  
**Condition:** Professor changes only the title.

1. System keeps the current instruction review fresh.
2. System does not enqueue review solely because of the title change.
3. Normal deterministic title validation still applies on save.

### AF-11: Prompt injection or unsupported instruction behavior

**Branches from:** Main Flow step 8  
**Condition:** Instructions attempt to override system rules, reveal prompts, force correct grading, or direct the tutor to give answers.

1. Review service treats the text only as untrusted content.
2. Validated feedback may identify the risky behavior as `NEEDS_IMPROVEMENT` or `INVALID`.
3. System rules remain authoritative even if the professor saves anyway.
4. Professor's override never changes tutor security policy.

### AF-12: Publication has a non-favorable or unavailable review

**Branches from:** UC-008 publication validation  
**Condition:** Current saved instructions lack a current `GOOD` review.

1. System presents the current warning or unavailable state.
2. Professor may cancel, request review, or choose **Publicar de todos modos**.
3. On confirmation, system records a `PUBLISH` override for the exact saved instructions hash.
4. UC-008 continues publication if all deterministic rules pass.

---

## Postconditions

- **On favorable review:** A validated immutable review is available for the exact instructions hash and can guide the professor without mutating the activity automatically.
- **On override:** The professor's explicit choice, action, actor, review when available, and instructions hash are auditable; the chosen text may be saved or published through its owning use case.
- **On failure:** The editor stays responsive, invalid model output is not trusted, and the professor can retry or proceed explicitly without AI review.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Instruction review is advisory; it never overrides the professor's final decision. |
| BR-02 | Blank title or instructions are deterministic errors and cannot be bypassed through an AI override. |
| BR-03 | `GOOD`, `NEEDS_IMPROVEMENT`, and `INVALID` describe a model recommendation, not authorization or lifecycle state. |
| BR-04 | A professor may save or publish despite `NEEDS_IMPROVEMENT`, `INVALID`, missing, failed, or stale review only after explicit confirmation. |
| BR-05 | Every override is scoped to `SAVE_DRAFT` or `PUBLISH` and to the exact instructions hash. |
| BR-06 | Applying a suggestion always requires an explicit professor action and never autosaves. |
| BR-07 | Review freshness uses the instructions hash plus review policy/rubric version; title-only changes do not invalidate it. |
| BR-08 | A completed stale review remains historical but cannot highlight or authorize assumptions about changed text. |
| BR-09 | Review requests are asynchronous and must not hold a Vaadin session/request thread or database transaction during model execution. |
| BR-10 | Model calls have a configured deadline and bounded concurrency. |
| BR-11 | Duplicate review requests for the same semantic input are idempotent. |
| BR-12 | Backend validation is required before any review output is persisted as successful or rendered. |
| BR-13 | Reliable source ranges may be highlighted; unreliable ranges must not modify text. |
| BR-14 | Full replacement requires confirmation; range replacement changes only the selected range. |
| BR-15 | Professor instructions are untrusted prompt content and cannot weaken tutor/system rules. |
| BR-16 | Students never see instruction-review results, warnings, internal errors, or professor overrides. |
| BR-17 | Review history and overrides are separate records; they are not flattened into many mutable columns on `training_activity`. |
| BR-18 | The review UI must communicate `REVIEWING`, `GOOD`, warning, stale, unavailable, and overridden states accessibly. |

### Validated review result

The backend contract is equivalent to:

```json
{
  "outcome": "GOOD | NEEDS_IMPROVEMENT | INVALID",
  "summary": "...",
  "issues": [
    {
      "id": "...",
      "severity": "INFO | WARNING | ERROR",
      "category": "...",
      "startOffset": 0,
      "endOffset": 10,
      "message": "...",
      "whyItMatters": "...",
      "suggestedReplacement": "..."
    }
  ],
  "improvedInstructions": "..."
}
```

Offsets are optional unless the backend validates them against the reviewed text. Hidden reasoning is not part of this contract.

---

## Tests

- [ ] Main Flow covered from edit through asynchronous review, suggestion, and explicit override save.
- [ ] AF-1 authorization and scope covered.
- [ ] AF-2 deterministic validation covered without model invocation.
- [ ] AF-3 missing/stale/pending review override covered.
- [ ] AF-4 and AF-5 model failure paths covered without UI blocking.
- [ ] AF-6 stale in-flight result covered.
- [ ] AF-7 review idempotency covered.
- [ ] AF-8 suggestion application covered.
- [ ] AF-9 cancelled override produces no save/override.
- [ ] AF-10 title-only freshness covered.
- [ ] AF-11 prompt-content isolation covered.
- [ ] AF-12 publication override covered with UC-008.
- [ ] BR-01 through BR-18 covered.

---

## UI Surface

- Draft instructions editor with explicit review action and visible asynchronous state.
- Inline diagnostics only where source ranges are reliable.
- Advisory panel with summary, rationale, specific replacements, and optional full rewrite.
- Confirmation dialog with **Volver a editar** and **Guardar de todos modos**.
- Publication confirmation with **Volver a editar** and **Publicar de todos modos**.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Instruction review editor | Authenticated authorized professor | Draft form at `/training-activities` |
| Save override dialog | Authenticated authorized professor | Save with non-favorable/missing current review |
| Publish override dialog | Authenticated authorized professor | Publish with non-favorable/missing current review |
