# Specification Folder

This folder is the repo-local source of truth for feature intent, workflow, and verification.
Use it to describe *what* Socratic Tutor should do before changing *how* the code does it.

## Quick path

1. Read `README.md` to anchor the feature in the product's learning goals and tone.
2. Draft or update a use case in `spec/use-cases/` before implementation.
3. Implement from the use case, then verify behaviour and tests against the same document.

## What is in this base migration

| Path | Purpose | Notes |
|------|---------|-------|
| `use-cases/README.md` | Index of feature use cases | Update it whenever a new use case is created |
| `use-cases/use-case-template.md` | Template for new use cases | Copy it as `use-case-NNN-short-name.md` |
| `datamodel/` | Reserved space for domain data docs | Intentionally empty in this base migration |

## Deliberate non-goals of this migration

This base setup does **not** create the broader spec documents yet.
Until they exist, use the existing repo context as follows:

| Need | Current source |
|------|----------------|
| Product purpose and user value | `PRODUCT.md` |
| Technical constraints and code structure | Existing code + `pom.xml` + `package.json` |
| Feature-level requirements | Individual files in `spec/use-cases/` |

Create `spec/project-context.md`, `spec/architecture.md`, or `spec/datamodel/datamodel.md` only when the team explicitly decides to expand the spec system.

## Workflow expectations

- Keep specs in English.
- Keep changes surgical and traceable to a concrete use case.
- Update the matching use case when implementation meaningfully changes behaviour.
- Do not add parallel planning systems or alternate spec formats unless the team approves them.

## Next step

Create the first concrete use case in `spec/use-cases/` when the team is ready to capture a feature.
