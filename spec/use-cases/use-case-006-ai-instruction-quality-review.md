# UC-006: AI Instruction Quality Review for Training Activities

---

**Goal:** As a professor, I want the system to review my training activity instructions before saving or launching the activity, highlight exactly what needs improvement, explain why it matters, and let me apply model-suggested replacements so that only pedagogically useful instructions are saved and later used by the adaptive student tutor runtime.

**Status:** Pending
**Date:** 2026-07-07

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor
- **Secondary actors:** Backend training activity service, AI instruction quality review service, instruction-review model, Vaadin Flow professor workspace, TypeScript instruction editor/linter component

---

## Preconditions

- The professor is authenticated.
- The professor has an active group-class context where they can manage training activities.
- The professor has permission to create, update, save, or launch training activities in the active class context.
- The training activity form has at least a title field and an instructions field.
- The normal training activity lifecycle already exists: draft creation, draft saving, launch/publish, assignment creation, student execution, transcript persistence, and report generation.
- The system has deterministic backend validation for required fields, even if the AI review model is unavailable.
- The system has access to an instruction quality review model or configured model endpoint for reviewing professor-written instructions.
- Professor instructions are treated as untrusted user content and must not override backend/system rules.
- The final saved professor instructions are later used by the student tutor runtime as pedagogical context.
- The professor workspace supports Vaadin Flow plus a required TypeScript instruction editor/linter component for inline diagnostics, underlined issue ranges, hover cards, and suggestion replacement actions.

---

## Trigger

The professor clicks **Guardar borrador**, requests an instruction review, clicks **Lanzar actividad**, or modifies the instructions from a training activity dialog opened from the activities grid/detail surface.

---

## Main Flow

> This use case extends the normal training activity creation, edit, and launch flow. It does not replace required backend validation, permission checks, assignment creation, safe-browser rules, student execution, or report generation.

1. Professor opens the training activity creation screen, edit screen, or a training activity action/detail dialog from the activities grid.
2. System shows the training activity form or dialog with fields such as title, instructions, and optional configuration/actions.
3. System renders the instructions field using the TypeScript instruction editor/linter component, not a plain textarea, because the field must support issue ranges, red/yellow underlines, hover cards, and replacement actions.
4. Professor enters or updates the training activity title.
5. Professor enters or updates instructions that should guide how the tutor questions and evaluates students.
6. System tracks whether the instructions field has changed since the latest stored review.
7. System marks the review state as stale while the professor edits the instructions.
8. Professor clicks **Guardar borrador** or explicitly requests review.
9. System validates required fields using deterministic backend validation.
10. If title or instructions are blank, system rejects the action without calling the AI review model.
11. If the instructions field changed, or no valid `GOOD` review exists for the current instructions, system sends the current title and instructions to the AI instruction quality review service.
12. AI instruction quality review service builds a controlled prompt for the configured instruction-review model.
13. The model first determines whether the text is usable as activity instructions at all.
14. If the text is unusable nonsense, random text, spam, unrelated filler, or not an instruction, the model returns an invalid-instruction result.
15. If the text is usable, the model classifies the instructions as either `GOOD` or `NEEDS_IMPROVEMENT`.
16. The model returns a structured review result with usability, quality status, summary, issues, pedagogical reasons, suggested replacements, source ranges/fragments, and improved full instructions when applicable.
17. Backend validates and normalizes the model response before trusting, storing, or displaying it.
18. If the review result is invalid-instruction, system blocks saving and shows inline linter feedback explaining that the text is not a usable instruction for an activity.
19. If the review status is `NEEDS_IMPROVEMENT`, system blocks saving for now and shows visible improvement feedback directly on the instruction editor.
20. The editor underlines the problematic instruction fragments in red or warning color when source ranges are available.
21. Below the editor, system shows the model verdict explaining what needs improvement and why the current instruction would weaken tutor questions and final report quality.
22. When professor hovers over an underlined fragment, system shows a floating IDE/linter-style card with the issue message, why it matters, suggested replacement, and an apply action.
23. The feedback card uses wording such as: **“Creo que esto se alinea más con lo que buscas:”**, followed by the replacement text in a monospace block.
24. Professor clicks **Aplicar reemplazo** for a specific issue or **Usar instrucción sugerida** for the full improved instructions.
25. System replaces the targeted text range when range metadata exists; otherwise, system applies the full suggested instruction only after professor confirmation.
26. System marks the review as stale because the instruction changed.
27. Professor reviews or edits the updated instructions.
28. Professor clicks **Guardar borrador** again.
29. System repeats the review loop until the instructions are `GOOD`.
30. If the review status is `GOOD`, system stores the review result and allows saving the activity as `DRAFT`.
31. If the `GOOD` review includes optional refinements, system may show them as non-blocking advice, but they do not prevent saving.
32. Professor later clicks **Lanzar actividad**.
33. System validates that the activity is eligible to launch.
34. System verifies that the latest stored instruction review matches the current instructions and has status `GOOD`.
35. If the latest review is `GOOD`, system proceeds through the normal launch and assignment creation flow.
36. If the review is missing, stale, invalid, unavailable, or `NEEDS_IMPROVEMENT`, launch is blocked until the professor reviews and saves `GOOD` instructions.
37. Student assignments are created only after normal launch rules and instruction quality rules pass.
38. The final saved professor instructions become the instructions used later by the adaptive student tutor runtime.

---

## Alternative Flows

### AF-1: Professor has no permission to manage training activities

**Branches from:** Main Flow step 1, 8, 32, or grid/detail dialog actions
**Condition:** The current user is not allowed to create, update, save, review, or launch training activities in the active class context.

1. System denies access to the requested action.
2. System shows a permission/no-access message.
3. No instruction review is executed.
4. No training activity changes are saved.
5. Use case ends.

### AF-2: No active class context

**Branches from:** Main Flow step 1
**Condition:** Professor is authenticated but has no valid active group-class context selected.

1. System redirects the professor to context selection or no-access state.
2. Professor must select a valid class context before managing training activities.
3. No instruction review is executed.
4. No activity changes are saved.
5. Use case ends or returns to Main Flow step 1 after context selection.

### AF-3: Required activity fields are missing

**Branches from:** Main Flow step 9
**Condition:** Professor clicks save, review, or launch with missing required fields, such as blank title or blank instructions.

1. System performs deterministic backend validation.
2. System rejects the requested action.
3. System shows field-level validation errors.
4. No AI review is executed when required inputs are blank.
5. Professor corrects the required fields.
6. Flow returns to Main Flow step 4 or step 5.

### AF-4: Instructions are unusable nonsense

**Branches from:** Main Flow step 13 or step 14
**Condition:** Instructions are random text, spam, repeated characters, keyboard mashing, unrelated filler, or otherwise not usable as activity instructions, such as `asdasdasd`.

1. Model returns an invalid-instruction result.
2. Backend validates that the invalid result includes a clear reason and does not provide fake pedagogical guidance.
3. System blocks save, update, and launch.
4. System underlines the invalid text when possible and shows a linter card explaining that real activity instructions are required.
5. System may show a short example of a valid instruction format.
6. Use case ends or returns to editing.

### AF-5: Instructions are usable but need improvement

**Branches from:** Main Flow step 15 or step 19
**Condition:** Instructions contain a usable topic or intent, but lack enough pedagogical detail, expected evidence, common misconceptions, difficulty guidance, or desired Socratic behavior.

1. Model returns `NEEDS_IMPROVEMENT`.
2. System blocks save/update for now.
3. System always shows visible improvement feedback; it must not hide the feedback merely because the text is usable.
4. System displays what is missing, why it matters, and one or more concrete suggested rewrites.
5. System underlines the problematic fragments if source ranges are available.
6. Professor may apply a suggestion, edit manually, or request review again.
7. Flow returns to Main Flow step 24 or step 27.

### AF-6: Instructions are good but optional refinements exist

**Branches from:** Main Flow step 30 or step 31
**Condition:** Instructions are clear enough to guide the tutor, but the model detects optional refinements.

1. Model returns `GOOD` with optional suggestions.
2. System allows save/update/launch without confirmation.
3. System may display optional suggestions as non-blocking advice.
4. Professor may ignore, apply, or edit suggestions manually.
5. Use case ends or continues with editing.

### AF-7: Professor applies a specific suggestion

**Branches from:** Main Flow step 24
**Condition:** Professor clicks **Aplicar reemplazo** for a specific issue or replacement.

1. System replaces only the targeted instruction fragment if a range is available.
2. If no range is available, system asks professor to confirm replacing the full instruction or applies the full improved instructions according to the component behavior.
3. System marks the review as stale because the instruction changed.
4. Professor reviews the changed instruction.
5. Flow returns to Main Flow step 27.

### AF-8: Professor edits manually instead of using a suggestion

**Branches from:** Main Flow step 21 or step 24
**Condition:** Professor chooses to edit the instruction manually.

1. System keeps the current instruction text.
2. Professor changes the instructions.
3. System marks the previous review as stale.
4. Professor saves or requests review again.
5. Flow returns to Main Flow step 9.

### AF-9: Professor tries to save with improvement warnings unresolved

**Branches from:** Main Flow step 19
**Condition:** Review status is `NEEDS_IMPROVEMENT`, and professor tries to save/update without applying changes or reaching `GOOD`.

1. System blocks save/update.
2. System keeps the current unsaved text in the editor.
3. System explains that the activity cannot be saved until instructions are good enough to guide the tutor.
4. System keeps the linter feedback visible and actionable.
5. Use case ends or returns to editing.

### AF-10: Professor tries to launch without a current GOOD review

**Branches from:** Main Flow step 36
**Condition:** Latest review is `NEEDS_IMPROVEMENT`, invalid, stale, unavailable, missing, or not associated with the current instructions.

1. System blocks launch.
2. System does not publish the training activity.
3. System does not create student assignments.
4. System explains that launch requires saved instructions with a current `GOOD` review.
5. Professor must write usable instructions, apply suggestions, retry review, and save successfully.
6. Use case ends or returns to editing.

### AF-11: No review exists when saving or launching

**Branches from:** Main Flow step 11 or step 34
**Condition:** The training activity has no stored instruction review result, for example because it was created before this feature existed.

1. System runs an instruction quality review before completing the save/update/launch action.
2. System stores the review result only if it is valid.
3. If instructions are `GOOD`, flow proceeds according to Main Flow step 30 or step 35.
4. If instructions need improvement, flow follows AF-5.
5. If instructions are invalid, flow follows AF-4.
6. If review cannot be completed, flow follows AF-14 or AF-15.

### AF-12: Instructions changed after latest review

**Branches from:** Main Flow step 26 or step 34
**Condition:** Professor modified the instructions after the latest stored review.

1. System detects that the stored review no longer matches the current instructions.
2. System marks the review as stale.
3. System runs a new review before save/update/launch.
4. Flow proceeds according to the new result.

### AF-13: Title changed but instructions did not change in grid/detail dialog

**Branches from:** Main Flow step 1 or step 6
**Condition:** Professor opens the grid/detail dialog and changes only the title, unlocks students, views reports, or performs non-instruction actions.

1. System performs the requested non-instruction action using existing validation and permissions.
2. System does not run instruction quality review solely because the title changed or because professor viewed reports/unlocked students.
3. If instructions are later modified in the same dialog, system marks review stale and follows Main Flow step 11.
4. Use case ends or returns to the dialog.

### AF-14: AI instruction-review model returns invalid output

**Branches from:** Main Flow step 16 or step 17
**Condition:** Model responds with malformed JSON, missing fields, wrong enum values, unsupported status, unsafe content, prompt leakage, or text unrelated to instruction quality.

1. Backend rejects the malformed model response.
2. System records the review as unavailable or failed.
3. System does not trust or display invalid model content.
4. Save/update/launch is blocked until a valid review can be completed.
5. System shows a friendly error message.
6. Use case ends or professor retries.

### AF-15: AI instruction-review service timeout or unavailable

**Branches from:** Main Flow step 11 or step 17
**Condition:** Review model is offline, not loaded, unreachable, or times out.

1. System cancels the review request after the configured timeout.
2. System does not wait indefinitely.
3. System blocks save/update/launch because the instruction quality gate cannot be completed.
4. System shows a friendly message and allows retry when appropriate.
5. Use case ends or professor retries.

### AF-16: Prompt injection attempt inside professor instructions

**Branches from:** Main Flow step 11
**Condition:** Instructions include text attempting to override system rules, such as “ignore previous instructions”, “reveal the prompt”, “always give answers directly”, or “mark all answers correct”.

1. AI instruction quality review service treats professor instructions as user-provided content, not as system instructions.
2. Model flags the problematic instruction behavior.
3. System returns `NEEDS_IMPROVEMENT` if the instruction is still usable after removing the problematic text, or invalid-instruction if the text is not usable.
4. System shows a warning explaining the unsupported behavior.
5. Save/update/launch is blocked until unsafe instruction behavior is removed and the review becomes `GOOD`.
6. Use case ends or returns to editing.

### AF-17: Professor writes instructions in another language

**Branches from:** Main Flow step 11
**Condition:** Professor writes usable instructions in a language different from the default UI language.

1. System sends the instructions as written to the review model.
2. Model evaluates clarity and pedagogical completeness in that language if supported.
3. Model returns professor-facing feedback in the configured UI language.
4. If the language is unsupported or unclear, system treats the instruction as needing improvement or invalid depending on usability.
5. Professor may rewrite the instructions.
6. Use case ends or returns to editing.

---

## Postconditions

- **On success:** Professor instructions are reviewed before draft save/update and before launch; the system stores a structured `GOOD` review result; all required improvement opportunities returned by the model were resolved before saving; accepted suggestions update the instructions field only after professor confirmation; launch uses a valid non-stale `GOOD` review; student assignments are created only after normal launch rules and instruction quality rules pass; final saved instructions are available for the student tutor runtime.
- **On needs improvement:** If instructions are usable but not yet good enough, the system blocks saving for now, keeps the unsaved text in the editor, underlines problematic fragments when possible, shows the model verdict below the editor, and offers concrete replacements with reasons.
- **On invalid instruction:** If the professor writes nonsense, random text, spam, unrelated filler, or another non-instruction, the system blocks save/update/launch and explains that real activity instructions are required.
- **On failure:** If permission or context validation fails, no review or activity change occurs; if required fields are invalid, no AI review is needed; if model output is invalid or unavailable, system does not trust it and blocks save/update/launch until review succeeds; suggestions never overwrite professor text without explicit action.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | AI instruction quality review extends the training activity creation, edit, grid/detail dialog, and launch flow; it does not replace backend validation or professor permissions. |
| BR-02 | Required field validation is deterministic and must work even if the AI review model is unavailable. |
| BR-03 | Blank title or blank instructions must be rejected before AI review. |
| BR-04 | The review model must first determine whether the instructions are usable activity instructions at all. |
| BR-05 | Usable instructions may have quality status `GOOD` or `NEEDS_IMPROVEMENT`. |
| BR-06 | `POOR` is not a valid quality status; nonsense or non-instruction content must be represented as invalid instruction content, not as low-quality valid instructions. |
| BR-07 | Nonsense, spam, random text, repeated characters, or unrelated filler must be treated as invalid instruction content and must block save/update/launch. |
| BR-08 | The system must not represent nonsense instructions as merely improvable activity instructions. |
| BR-09 | The review model must return concrete suggestions and the pedagogical reason for each suggestion whenever improvement is possible. |
| BR-10 | `GOOD` instructions may show optional refinements, but those refinements must not be required to save or launch. |
| BR-11 | `NEEDS_IMPROVEMENT` instructions must always show visible feedback explaining what should improve and why. |
| BR-12 | `NEEDS_IMPROVEMENT` instructions must block draft save/update until the professor edits or applies suggestions and obtains a `GOOD` review. |
| BR-13 | Launch must be blocked for `NEEDS_IMPROVEMENT`, invalid instructions, stale review, unavailable review, or missing review. |
| BR-14 | Launch must require a current stored `GOOD` review for the exact saved instructions. |
| BR-15 | The review output must include summary, issue list, severity, message, why-it-matters explanation, suggested replacement, source range metadata, and optional full improved instructions. |
| BR-16 | Each issue must include source location metadata when the problematic text can be located, such as start/end offsets and problem text, so the UI can highlight the affected instruction fragment. |
| BR-17 | The instruction field must be implemented as a TypeScript instruction editor/linter component capable of rendering range diagnostics, underlines, hover cards, and replacement actions; a plain textarea is not sufficient for this use case. |
| BR-18 | CodeMirror 6 is the preferred editor implementation for this use case because it supports diagnostics with ranges, severity, messages, and diagnostic actions, and it can be used as a web editor component. |
| BR-19 | The editor must render `ERROR` or invalid-instruction ranges with red underline/highlight and `WARNING` or improvement ranges with warning styling, according to the design system. |
| BR-20 | Hovering an underlined range must show a floating advisory card with the issue, why it matters, suggested replacement, and apply action. |
| BR-21 | The advisory card below the editor must show the model verdict whenever the result is not `GOOD`. |
| BR-22 | Suggested replacement text should be shown in a monospace block after copy such as “Creo que esto se alinea más con lo que buscas:”. |
| BR-23 | Improved instructions and replacements are suggestions, not automatic replacements. |
| BR-24 | The professor must explicitly choose to apply a suggestion. |
| BR-25 | Applying a range suggestion must replace only the targeted range when range metadata exists. |
| BR-26 | If no reliable range exists, applying a full suggestion must require confirmation before replacing the whole instruction. |
| BR-27 | The review model may use the title as context, but the review freshness rule is based on the instructions text unless product rules explicitly include title changes. |
| BR-28 | In the grid/detail dialog, review is triggered only when the instructions field changes or when launch requires a missing/stale review; changing title alone, unlocking students, or viewing reports must not trigger review. |
| BR-29 | Review metadata should include instructions hash, usability flag, quality status, summary, issues, suggestions, improved instructions, reviewed timestamp, model name, and rubric version when implemented. |
| BR-30 | Review metadata is for professor guidance and system quality control; it should not be shown to students. |
| BR-31 | Students should not see warnings about professor instruction quality. |
| BR-32 | The tutor should use the final saved instructions, not rejected suggestions. |
| BR-33 | If professor accepts a suggestion, the resulting text becomes the activity instructions only after save/update succeeds. |
| BR-34 | If activity instructions are immutable after publish, no instruction changes or new reviews should be allowed after launch unless an explicit product rule supports edits. |
| BR-35 | Review prompts, model configuration, and fallback rules should be centralized in the backend, not hardcoded in the Vaadin view or TypeScript component. |
| BR-36 | Professor instructions must be treated as untrusted input. |
| BR-37 | Prompt injection attempts inside professor instructions must be flagged, neutralized, or blocked. |
| BR-38 | Backend must validate model output before storing or showing it. |
| BR-39 | Invalid JSON or invalid model output must not be treated as a valid review. |
| BR-40 | Model timeouts must not block the UI indefinitely. |
| BR-41 | The TypeScript instruction editor/linter must keep review state reactive using project conventions such as signals/stores for instruction text, pending review state, stale review flag, selected issue, highlighted ranges, review result, and apply-suggestion action. |
| BR-42 | The professor UI should prefer helping the professor improve the brief rather than silently accepting weak guidance. |

The instruction quality review service must produce a backend-validated object equivalent to:

```json
{
  "validInstruction": true,
  "qualityStatus": "GOOD | NEEDS_IMPROVEMENT",
  "canSave": false,
  "canLaunch": false,
  "summary": "Las instrucciones son utilizables, pero necesitan mayor precisión sobre la evidencia esperada.",
  "issues": [
    {
      "id": "issue-1",
      "severity": "WARNING",
      "category": "MISSING_EXPECTED_EVIDENCE",
      "problemText": "hacer preguntas sobre strings",
      "startOffset": 12,
      "endOffset": 40,
      "message": "La instrucción menciona el tema, pero no define qué debe demostrar el estudiante.",
      "whyItMatters": "El tutor necesita criterios concretos para generar preguntas útiles y producir un reporte con evidencia.",
      "suggestedReplacement": "Evalúa si el estudiante comprende arreglos de char, terminador nulo y uso básico de strlen/strcmp en C.",
      "suggestionReason": "Esta versión especifica conceptos y evidencia esperada."
    }
  ],
  "improvedInstructions": "Evalúa si el estudiante comprende strings en C, incluyendo arreglos de char, terminador nulo, lectura con scanf/fgets y comparación con strcmp. El tutor debe pedir explicación, ejemplo y justificación, y detectar confusiones entre char, arreglo de char y cadena."
}
```

For `GOOD`, `canSave` and `canLaunch` must be true. For `NEEDS_IMPROVEMENT`, `canSave` and `canLaunch` must be false until the professor obtains a `GOOD` review.

For invalid instruction content, the service must produce a backend-validated object equivalent to:

```json
{
  "validInstruction": false,
  "qualityStatus": null,
  "canSave": false,
  "canLaunch": false,
  "summary": "El texto no es una instrucción usable para una actividad formativa.",
  "issues": [
    {
      "id": "issue-1",
      "severity": "ERROR",
      "category": "INVALID_INSTRUCTION_CONTENT",
      "problemText": "asdasdasd",
      "startOffset": 0,
      "endOffset": 9,
      "message": "El texto parece aleatorio y no indica qué debe evaluar el tutor.",
      "whyItMatters": "Sin instrucciones reales, el tutor no puede generar preguntas ni un reporte confiable.",
      "suggestedReplacement": "Escribe el tema, los conceptos a evaluar y qué evidencia esperas del estudiante.",
      "suggestionReason": "La actividad necesita una guía pedagógica mínima antes de guardarse."
    }
  ],
  "improvedInstructions": ""
}
```

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [ ] Main Flow covered: professor writes title/instructions, review runs on save, result is displayed in the editor, professor applies suggestion, review runs again, status becomes `GOOD`, draft save succeeds, and launch uses latest valid `GOOD` review.
- [ ] Permission and context alternatives covered: AF-1 and AF-2.
- [ ] Required validation alternative covered: AF-3.
- [ ] Invalid instruction alternative covered: AF-4.
- [ ] Improvement alternatives covered: AF-5 and AF-6.
- [ ] Suggestion application alternatives covered: AF-7 and AF-8.
- [ ] Save blocked with unresolved improvement covered: AF-9.
- [ ] Launch without current `GOOD` review covered: AF-10.
- [ ] Missing/stale review alternatives covered: AF-11 and AF-12.
- [ ] Grid/detail dialog title-only/non-instruction action alternative covered: AF-13.
- [ ] AI failure alternatives covered: AF-14 and AF-15.
- [ ] Prompt-safety and language alternatives covered: AF-16 and AF-17.
- [ ] Business rule covered: usable instructions produce only `GOOD` or `NEEDS_IMPROVEMENT` quality status.
- [ ] Business rule covered: `POOR` is not accepted as a quality status.
- [ ] Business rule covered: nonsense such as `asdasdasd` blocks save/update/launch as invalid instruction content.
- [ ] Business rule covered: `NEEDS_IMPROVEMENT` always blocks save/update until the review becomes `GOOD`.
- [ ] Business rule covered: `GOOD` optional suggestions do not block save or launch.
- [ ] Business rule covered: each issue includes explanation of why it matters and an actionable suggestion.
- [ ] Business rule covered: each locatable issue includes range metadata.
- [ ] UI test covered: problematic ranges are underlined in the TypeScript editor/linter component.
- [ ] UI test covered: hovering an underlined range shows the floating advisory card.
- [ ] UI test covered: applying a range suggestion replaces only the targeted range.
- [ ] UI test covered: applying full improved instructions requires explicit professor action.
- [ ] Business rule covered: rejected suggestions do not change saved instructions.
- [ ] Business rule covered: instruction changes invalidate previous review.
- [ ] Business rule covered: title-only changes in the grid/detail dialog do not trigger review unless instructions also changed.
- [ ] Business rule covered: invalid model JSON is rejected and not shown directly.
- [ ] Business rule covered: model timeout does not freeze the UI indefinitely and blocks save/update/launch until retry succeeds.
- [ ] Business rule covered: prompt injection inside professor instructions is treated as untrusted input.
- [ ] Business rule covered: students never see professor instruction quality warnings.
- [ ] Business rule covered: tutor uses the final saved instructions when the activity is executed.

---

## UI Surface

> This use case uses authenticated professor workspace surfaces implemented with Vaadin Flow and a required TypeScript instruction editor/linter component. There is no public anonymous route.

- Professor training activity management screen: professor creates or edits a training activity, writes title and instructions, reviews instruction diagnostics, applies suggested improved instructions, and saves drafts only when instruction quality is `GOOD`.
- Required TypeScript instruction editor/linter: enhanced instruction field powered by a browser editor capable of range diagnostics, underlines, hover cards, and replacement actions. The preferred implementation is a CodeMirror 6 plain-text editor embedded as a Vaadin-compatible web component.
- Inline issue highlighting: problematic instruction fragments are underlined or highlighted using range metadata from the backend-validated review result.
- Hover advisory card: shown when professor hovers over an underlined issue. Displays severity, problem, why it matters, suggested replacement, and **Aplicar reemplazo**.
- Below-editor model verdict: shown whenever the result is not `GOOD`. Displays the summary, pedagogical reason, and suggested replacement text in a monospace block.
- Full instruction suggestion card: shows **“Creo que esto se alinea más con lo que buscas:”** followed by the improved instruction in monospace, plus **Usar instrucción sugerida**.
- Training activity grid/detail dialog: allows editing title/instructions, unlocking students, and viewing reports according to existing permissions; the instruction review mechanism is triggered only when instructions are modified or launch requires review.
- Launch blocked state: shown when professor attempts to launch without a current saved `GOOD` instruction review.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Professor training activity management | Authenticated professor or authorized training activity manager | `/training-activities` |
| TypeScript instruction editor/linter | Authenticated professor or authorized training activity manager | Embedded in create/edit/dialog surfaces |
| Hover advisory card | Authenticated professor or authorized training activity manager | Hover over underlined instruction issue |
| Below-editor model verdict | Authenticated professor or authorized training activity manager | Below the instructions editor after review |
| Training activity grid/detail dialog | Authenticated professor or authorized training activity manager | Opened from activities grid/detail actions |
| Launch blocked state | Authenticated professor or authorized training activity manager | Opened from launch action |
