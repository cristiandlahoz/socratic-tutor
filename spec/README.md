# Specification Folder

Specs are written first, then used as input for implementation, review, and verification.

This folder is the source of truth for Socratic Tutor's product direction, architecture, data model, design context, and feature use cases.

Do not rely on prompt-only project details. If a rule matters, put it in one of these files.

---

## Reading Order

| Step | File | Purpose |
|---|---|---|
| 1 | `project-context.md` | Product vision, users, scope, constraints, and canonical domain language. |
| 2 | `spec.md` | Top-level system specification and implementation baseline. |
| 3 | `architecture.md` | Technology stack, runtime architecture, package boundaries, and security architecture. |
| 4 | `datamodel/datamodel.md` | Schema, relationships, constraints, seed data, and persistence rules. |
| 5 | `design_context.md` | UX, navigation, workspace behavior, and design intent. |
| 6 | `use-cases/README.md` | Index of feature use cases. |
| 7 | `use-cases/use-case-template.md` | Template for new use cases. |

---

## Current Foundation

Socratic Tutor is an academic multi-tenant tutor platform.

The canonical identity chain is:

```text
account
  -> tenant_account
      -> group_class_member
```

The canonical academic chain is:

```text
tenant
  -> subject
  -> academic_period
  -> group_class
      -> group_class_member
```

The canonical tutor activity chain is:

```text
group_class_member
  -> conversation
      -> conversation_snapshot
```

The canonical grounding chain is:

```text
group_class
  -> grounding_collection
      -> grounding_document
          -> grounding_chunk
```

The canonical formative activity chain is:

```text
group_class
  -> evaluation
      -> evaluation_assignment
```

---

## Document Responsibilities

### `project-context.md`

Use for:

- project vision,
- problem statement,
- users,
- constraints,
- canonical product vocabulary,
- what is in/out of scope.

Do not put detailed schema implementation here.

### `spec.md`

Use for:

- top-level system requirements,
- Mermaid ERD baseline,
- security intent,
- business rules,
- evaluation criteria,
- final expected result.

This is the main always-readable specification file.

### `architecture.md`

Use for:

- technology stack,
- package organization,
- runtime boundaries,
- Spring Security architecture,
- AI architecture,
- Vaadin architecture,
- legacy isolation strategy.

Do not duplicate every business rule here unless it affects architecture.

### `datamodel/datamodel.md`

Use for:

- entities,
- relationships,
- constraints,
- indexes,
- identifier strategy,
- seed data,
- obsolete table mapping.

The Mermaid ERD should be present here and in `spec.md` because the schema is foundational.

### `design_context.md`

Use for:

- role-based UX,
- navigation,
- workspace layouts,
- empty states,
- chat interaction design,
- grounding UI,
- formative activity UI,
- design review checklist.

### `use-cases/`

Use for:

- one implementation slice at a time,
- actors,
- preconditions,
- trigger,
- main flow,
- alternative flows,
- postconditions,
- business rules,
- tests,
- UI surface.

Use cases should remain atomic enough to implement and verify.

---

## Current Use Case Baseline

| ID | Purpose |
|---|---|
| UC-001 | Target academic multi-tenant ERD and schema foundation. |
| UC-002 | Active runtime adaptation from old persistence to target ERD. |
| UC-003 | Role-based onboarding and workspace setup. |
| UC-004 | Corrective alignment after implementation drift from UC-001/UC-002/UC-003. |

UC-004 should hold implementation corrections such as accidental config deletion, wrong relationships, naming drift, or legacy reintroduction. Those corrections should not pollute the top-level project spec unless they change the intended foundation.

---

## Workflow

### 1. Update Context Before Implementation

Before implementing a new feature, check whether it changes:

- product vocabulary,
- architecture,
- data model,
- security model,
- UI/UX model.

If yes, update the relevant context file before implementing.

### 2. Create or Update a Use Case

Use cases must describe:

- actor,
- trigger,
- main flow,
- alternative flows,
- postconditions,
- business rules,
- tests,
- UI surface.

Implementation should follow the use case, not the other way around.

### 3. Implement From Specs

Implementation prompts should reference the use case and the relevant context documents.

Avoid embedding essential project rules only in the prompt.

### 4. Verify Against Specs

Verification should check:

- schema alignment,
- runtime behavior,
- security rules,
- UI behavior,
- tests,
- legacy isolation,
- final Maven result.

### 5. Update Specs If Reality Changes

If implementation reveals that the spec is wrong or incomplete, update the spec before continuing.

Do not let code, use cases, and context files drift apart.

---

## Maintenance Rules

- Keep specs in English.
- Keep use cases focused and reviewable.
- Do not make use cases so large that they stop being atomic.
- Do not reintroduce obsolete `client_id` persistence as active identity.
- Do not reintroduce old chat/document/evaluation-run tables as active runtime.
- Do not treat tenant as professor workspace.
- Do not add global professor access unless a use case explicitly approves it.
- Do not allow UI hiding to replace service-layer authorization.
- Keep Mermaid schema synchronized between `spec.md` and `datamodel/datamodel.md`.
- Keep architecture and project context synchronized with UC-001 foundation.

---

## Local Verification

Final implementation reports should include:

```text
status
executive_summary
changed_files
implementation_notes
tests_run
test_results
final_mvn_result
risks_or_followups
```

Expected Maven command:

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

---

## Related Files

- [Project Context](project-context.md)
- [System Specification](spec.md)
- [Architecture](architecture.md)
- [Design Context](design_context.md)
- [Data Model](datamodel/datamodel.md)
- [Use Cases](use-cases/README.md)
