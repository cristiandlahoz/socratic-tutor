# Specification Folder

Specs are written first, then used as input for AI-driven implementation and verification.
They are the single source of truth. Keep them up to date as the project evolves.

## File Overview

| File | Purpose | When to Read |
|------|---------|--------------|
| `project-context.md` | Vision, problem, users, scope, risks | First — before anything else |
| `architecture.md` | Technology stack and application structure | After project context is filled in |
| `datamodel/datamodel.md` | Entity definitions and relationships | When adding data-driven features |
| `design-system.md` | Theme, component usage, and visual standards | When building or reviewing UI |

## Workflow

### Create Specs

1. **Define context** — Fill in `project-context.md` with problem, vision, scope, and constraints.
2. **Outline architecture** — Fill in `architecture.md` with tech stack and application structure.
3. **Specify features** — Update the canonical context, architecture, data model, and product specification directly.

### Implement Specs

1. Ask the AI to implement a scoped change from the current canonical specs.
2. Verify the change against the affected architectural boundaries and acceptance checks.

If implementation is lacking, add more information to specs files and ask again.

Avoid providing project related information in a prompt to the AI as those details will not be recorded in the project itself.
