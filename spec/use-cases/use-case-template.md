# UC-[NNN]: [Feature Title]

> Copy this template for each feature as `use-case-NNN-short-name.md`.
> Replace all `[bracketed text]` with your content. Remove sections that genuinely do not apply (e.g. no secondary actor), but do not invent shortcuts — Preconditions, Trigger, Main Flow, and Postconditions are mandatory.

---

**Goal:** As a [role], I want to [capability] so that [business value].

**Status:** Pending
**Date:** [YYYY-MM-DD]

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** [The role that initiates the use case — e.g., "Registered customer"]
- **Secondary actors:** [Other systems or roles the use case interacts with — e.g., "Payment gateway". Omit if none.]

---

## Preconditions

- [What must be true before the use case can start — e.g., "User is signed in"]
- [State that other use cases or setup steps must have established]

---

## Trigger

[The event that starts the use case — e.g., "User clicks 'Checkout' on the cart page".]

---

## Main Flow

> Numbered steps alternating between actor and system. Each step is one observable action. Keep steps atomic so alternative flows can branch from a specific step number.

1. [Actor] [does X].
2. [System] [responds with Y].
3. [Actor] [does Z].
4. [System] [confirms / persists / navigates].
5. ...

---

## Alternative Flows

> Branches off the main flow. Reference the step number where the branch occurs. Cover validation failures, permission denials, empty states, and external-system errors.

### AF-1: [Short name — e.g., "Invalid input"]

**Branches from:** Main Flow step [N]
**Condition:** [What makes this branch fire — e.g., "Email field is empty"]

1. [System] [shows error / takes corrective action].
2. [Actor] [...].
3. Returns to Main Flow step [N] / Use case ends.

### AF-2: [Short name]

**Branches from:** Main Flow step [N]
**Condition:** [...]

1. ...

---

## Postconditions

- **On success:** [What is true after the main flow completes — e.g., "Order is persisted with status PENDING"]
- **On failure:** [What is true if any alternative flow ends the use case — e.g., "No order is created; cart is unchanged"]

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | [Business rule — e.g., "All fields are mandatory"] |
| BR-02 | [Business rule — e.g., "Sold-out items are visible but cannot be selected"] |
| BR-03 | [Business rule — e.g., "Maximum 6 items per transaction"] |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [ ] Main Flow covered (steps [N–M])
- [ ] AF-1, AF-2, … covered
- [ ] BR-01, BR-02, … covered

---

## UI Surface

> What the user sees and how they reach it. Keep this implementation-agnostic — no framework annotations, component class names, or file paths. If the flow starts from a URL or route, record it here as an entry point; if it is backend-only, say so explicitly.

- [Page / screen description and what is on it]
- [Key interaction or state]

| Surface | Access | Entry Point |
|---------|--------|-------------|
| [Short name — e.g., "Movie catalog"] | [Anonymous / Authenticated / Admin] | [Route / deep link / menu path / "Backend-only"] |
