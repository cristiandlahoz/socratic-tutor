# UC-002: Consistent Codebase Coding Rules

**Goal:** As a developer, I want to work with consistent codebase coding rules so that I can make changes more smoothly and intuitively.

**Status:** Implemented
**Date:** 2026-06-25

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Developer.
- **Secondary actor:** Code reviewer / maintainer.

---

## Preconditions

- The current codebase exists with its present Java UI, CSS, naming, and style inconsistencies.
- The developer has repository access.
- Project context, architecture, and data model specs are available.

---

## Trigger

The developer explicitly requests that the coding-rules cleanup be run against the current codebase state.

---

## Main Flow

> Numbered steps alternating between actor and system. Each step is one observable action. Keep steps atomic so alternative flows can branch from a specific step number.

1. Developer requests a coding-rules cleanup for the current codebase.
2. System analyzes Java, CSS, and UI code for naming, duplication, dead classes, unsafe CSS usage, and Vaadin Flow misuse.
3. System reports the affected areas and proposed cleanup scope.
4. Developer confirms the cleanup scope.
5. System renames old domain terms where they conflict with the current model, especially chat-facing names that should become conversation-facing names.
6. System converts CSS class names to consistent BEM-style naming.
7. System removes dead or duplicated CSS classes.
8. System simplifies CSS variables by replacing unnecessary chained aliases with direct, readable values or meaningful tokens.
9. System creates or uses a local CSS utility for type-safe class-name usage from Java UI code.
10. System renames methods and variables to communicate clear intent.
11. System adjusts Vaadin Flow usage to follow the framework API correctly and idiomatically.
12. System runs formatting, compilation, and tests.
13. Code reviewer / maintainer reviews the resulting diff for consistency.

---

## Alternative Flows

> Branches off the main flow. Reference the step number where the branch occurs. Cover validation failures, permission denials, empty states, and external-system errors.

### AF-1: Cleanup scope is too broad

**Branches from:** Main Flow step 4
**Condition:** Developer decides the proposed cleanup touches too many unrelated areas at once.

1. Developer narrows the cleanup scope.
2. System updates the proposed scope to match the requested boundary.
3. Returns to Main Flow step 5.

### AF-2: CSS class appears unused but is dynamically referenced

**Branches from:** Main Flow step 7
**Condition:** A class selected for removal is referenced dynamically by Java, TypeScript, JavaScript, Vaadin internals, or runtime-generated markup.

1. System preserves the class or converts the dynamic reference to local type-safe utility usage.
2. System documents the reason the class is still required in the cleanup notes.
3. Returns to Main Flow step 8.

### AF-3: Rename would break route compatibility

**Branches from:** Main Flow step 5
**Condition:** A domain or UI rename would change an existing route, query parameter, or navigation entry point that users or tests still depend on.

1. System keeps route compatibility while renaming internal code where safe.
2. System records any intentionally retained public route or parameter name.
3. Returns to Main Flow step 6.

### AF-4: Tests or compilation fail

**Branches from:** Main Flow step 12
**Condition:** Formatting, compilation, or tests fail because of the cleanup.

1. System identifies the cleanup-caused failure.
2. System fixes the failure or reverts the specific cleanup that caused it.
3. Returns to Main Flow step 12.

### AF-5: Reviewer finds unclear naming or non-compliant Flow usage

**Branches from:** Main Flow step 13
**Condition:** Code reviewer / maintainer finds naming that is not intent-revealing or Vaadin Flow usage that is not compliant with the framework API.

1. Code reviewer / maintainer requests a correction.
2. System updates the affected code without broadening the cleanup scope.
3. Returns to Main Flow step 12.

---

## Postconditions

- **On success:** Coding conventions are applied consistently to the cleaned scope; code compiles; tests pass; reviewer can understand the naming and styling rules.
- **On failure:** No partially applied cleanup is accepted; any cleanup-caused breakage is reverted or fixed before completion.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | CSS classes must use BEM-style names for new or cleaned UI surfaces. |
| BR-02 | Dead or duplicated CSS classes must be removed unless they are dynamically referenced. |
| BR-03 | CSS variable chains that only alias another alias must be flattened unless the token adds clear semantic value. |
| BR-04 | Java UI code must avoid stringly typed CSS class names where a local type-safe utility is available. |
| BR-05 | Domain terminology must follow the current data model; conversation replaces obsolete chat naming where it represents domain conversation behavior. |
| BR-06 | Method and variable names must be intent-revealing. |
| BR-07 | Vaadin Flow APIs must be used idiomatically and compliantly. |
| BR-08 | Cleanup must be surgical: no unrelated feature behavior changes. |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1-13)
- [x] AF-1 through AF-5 covered
- [x] BR-01 through BR-08 covered
- [x] Formatting, compilation, and existing tests pass after cleanup

---

## UI Surface

> What the user sees and how they reach it. Keep this implementation-agnostic — no framework annotations, component class names, or file paths. If the flow starts from a URL or route, record it here as an entry point; if it is backend-only, say so explicitly.

- Developer-maintenance workflow over source code and review diffs.
- No application user route is introduced by this use case.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Source code cleanup and review diff | Developer repository access | Explicit developer/agent instruction |
