# Use Cases

This folder stores feature specifications for Socratic Tutor.
Each use case should describe the actor, trigger, main flow, alternative flows, postconditions, business rules, tests, and UI surface for one reviewable piece of behaviour.

## Quick path

1. Pick the next free `UC-NNN` number.
2. Copy `use-case-template.md` into `use-case-NNN-short-name.md`.
3. Fill in every required section before implementation starts.
4. Add the new use case to the index table below.

## Use case index

| ID | Title | Status | Primary Actor | Notes |
|----|-------|--------|---------------|-------|
| UC-001 | Authenticated Account Gains Tenant-Scoped Tutor Capabilities Through Roles and Permissions | Pending | Authenticated account holder (student or professor) | Establishes the non-Keycloak `account` / `tenant` / role / permission foundation for tutor chat, document, and evaluation access, with professor-owned tenant spaces and student chat ownership still scoped by account inside the tenant. |

## Status legend

- **Pending** — drafted but not yet implemented.
- **In Progress** — implementation is underway.
- **Implemented** — code and automated checks are complete.
- **Verified** — implementation has been reviewed against the use case.

## Maintenance rule

When you add a new use case file, add one row here in the same change so the index stays trustworthy.
