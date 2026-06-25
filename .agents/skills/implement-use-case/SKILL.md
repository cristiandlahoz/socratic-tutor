---
name: implement-use-case
description: Implement a use case from the spec/ folder. Use when asked to implement, build, or work on a use case.
argument-hint: "[use-case-name or number]"
---

# Use Case Implementation

Implement the use case specified by $ARGUMENTS.

A use case is the unit of work, not a view. A single use case may produce one view, several views, or no UI at all — let the spec drive that, and keep tests grouped per use case as defined by `/use-case-tests`.

## Input

1. The use case document itself (in `spec/use-cases/`)
2. Related use cases
3. Generic specification files in `spec/`
4. Any potential images or other resources provided for the use case

## Implementation

The following steps are mandatory and sequential. **Do not skip or reorder them.** Each step must be completed before proceeding to the next.

### Step 1: Create or switch to a use-case branch
- Work from a dedicated branch before editing.
- Branch names must use conventional names such as `feat/uc-006-manage-categories`, `fix/uc-006-category-validation`, or `test/uc-006-category-coverage`.
- Do **not** create branches with the `b/` prefix.

### Step 2: Write the code
- Whenever you are even slightly unsure about Vaadin API usage, component behavior, theme variables, styling, or best practices -- use the Vaadin MCP server to look it up before guessing. Do not rely on memory for Vaadin specifics.

### Step 3: Visually verify with Playwright MCP
- **This step is mandatory.** Do not skip it, do not defer it.
- Follow the visual-verification workflow end-to-end: start the app, navigate every route, walk through the main flow, take screenshots, and validate the visual appearance.
- Fix any issues found before moving on.

### Step 4: Write and run automated tests
- Follow the use-case-tests workflow and ensure all tests pass.

### Step 5: Iterate
- Keep iterating until everything looks and works great. Prefer great results over finishing quickly.

### Step 6: Commit
- Once all steps are complete and everything works, create a git commit with the changes.

### Step 7: Squash to main and clean up
- After the implementation branch is committed and verified, switch to `main`.
- Squash the use-case branch into `main`.
- Create the final squash commit on `main`.
- Delete the completed implementation branch after the squash commit succeeds.
- Leave the repository on `main` with a clean working tree.

**All steps must be completed before a use case is considered implemented.**
