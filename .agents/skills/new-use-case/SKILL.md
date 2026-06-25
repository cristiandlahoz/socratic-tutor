---
name: new-use-case
description: Define a new use case by interviewing the user, then write a filled-in `spec/use-cases/use-case-NNN-short-name.md`. Use when the user wants to add, draft, or specify a new feature/use case.
---

# New Use Case

Interview the user to capture a new use case, then write it to `spec/use-cases/use-case-NNN-short-name.md` using `spec/use-cases/use-case-template.md` as the structure.

The goal is a complete, implementation-ready spec. A use case that lacks actors, preconditions, a trigger, a main flow, alternative flows, postconditions, business rules, or routes is not done — keep interviewing until every section is filled in.

## Before You Ask Anything

1. Read `spec/project-context.md` and `spec/architecture.md` so questions stay consistent with the project's vision, users, and stack.
2. Read `spec/datamodel/datamodel.md` if it exists — refer to actual entities when discussing data.
3. List `spec/use-cases/` to:
   - See existing use cases (don't duplicate them; suggest references where flows overlap).
   - Determine the next free `NNN` number (zero-padded, e.g. `004`).
4. Read `spec/use-cases/use-case-template.md` — it is the authoritative shape of the output.

If any of these files are missing, tell the user and stop; the project isn't ready for a use case yet.

## Interview

Drive a focused conversation. Ask one topic at a time, in the order below. Keep questions short. When there is a clear small set of choices (primary actor, access level, UI tech, status), present them as a multiple-choice question. Use plain chat for open-ended answers (main flow, alternative flows, business rules). Never ask the user to "fill in the template" themselves — you are doing that for them.

After each answer, restate your understanding in one sentence before moving on, so misunderstandings surface early.

### 1. Goal sentence (capability and value)
- What capability is this use case about? Who is the actor? What value do they get?
- Produce the `As a … I want to … so that …` sentence and confirm it. This becomes the **Goal:** line in the template.

### 2. Short name (derive silently)
- Derive a kebab-case short name (2–4 words) from the goal sentence. Use it as the filename suffix. Do not ask the user — just pick a sensible one (e.g. "browse movies" → `browse-movies`, "user buys ticket" → `buy-ticket`). It will show up in the final summary; the user can correct it there if they care.

### 3. Actors
- Confirm the **primary actor** (the role that initiates the use case). The goal sentence usually names it — read it back.
- Ask whether there are any **secondary actors** (external systems or other roles the use case interacts with — payment gateway, email service, second role). If none, omit the line.

### 4. Preconditions
- What must already be true for this use case to start? Authentication, prior use cases having run, data that must exist.
- If the user genuinely has none, write "None".

### 5. Trigger
- What event starts the use case? A click, a route navigation, a scheduled job, an external event.
- Often inferable from the main flow's first step — propose and confirm.

### 6. Main flow (happy path, numbered)
- Ask the user to walk through what happens, step by step.
- Write the steps as numbered actor/system pairs that alternate (`1. User clicks X. 2. System shows Y. 3. User submits Z. 4. System persists and navigates to W.`). Each step is one observable action.
- If the user is vague, ask targeted follow-ups: entry point, what they see, what they click/type, what the system does, when the flow ends.
- Keep steps atomic — alternative flows will branch off specific step numbers, so coarse steps are hard to extend later.

### 7. Alternative flows
- Walk each main-flow step and ask: *"what can go wrong at this step?"* Look for: validation failures, permission denials, empty states, external-system errors, conflicts.
- For each alt flow, capture: short name, the step it branches from, the condition that triggers it, the mini-flow, and whether it returns to the main flow or ends the use case.
- If the user genuinely has nothing for a step, accept that and move on. Do not invent failures. But ask for every step.

### 8. Postconditions
- On success: what is true after the main flow completes? (E.g. "Order is persisted with status PENDING", "Email is sent".)
- On failure: what is true if any alternative flow ends the use case? (E.g. "No order is created; cart is unchanged".)

### 9. Business rules
- Ask what rules constrain the flow beyond what's already in the flows: required fields, limits, visibility/access, validation, edge cases, time/ordering rules.
- Encourage at least two or three. Each rule should be testable. Reference the data model and existing use cases when relevant.

### 10. UI / routes
- Ask whether this is a public (React/Hilla) or admin (Vaadin Flow) view — this drives routing, access annotations, and test style. Ask this as a multiple-choice question.
- Capture the route path(s), access level (public / authenticated / ADMIN), and any layout requirements (component types, key interactions, responsive needs).
- If the user has a mockup or image, ask them to point at it and reference it in the file.

### 11. Tests (placeholder)
- Suggest a test class name based on the short name (e.g. `BrowseMoviesTest`, `BuyTickets.test.tsx`).
- List planned coverage as bullets: Main Flow steps, each Alternative Flow, each Business Rule. The actual tests are written later by `/use-case-tests`; here we are just declaring intent.

## Draft and Confirm

Once all sections are gathered:

1. Show the user a compact summary of every section: goal, actors, preconditions, trigger, main flow (numbered), alt flows, postconditions, business rules, route table, planned tests. Do not show the full markdown yet — keep the review skimmable.
2. Ask for any corrections or additions.
3. Only after the user approves, write the file.

## Writing the File

- Path: `spec/use-cases/use-case-<NNN>-<short-name>.md`, where `<NNN>` is the next free number from step 3 of "Before You Ask Anything".
- Start from `spec/use-cases/use-case-template.md` — keep the same section order, headings, and tables. Remove the leading `> Copy this template …` instruction block.
- Fill every `[bracketed placeholder]`. Do not leave any behind. If something is genuinely unknown, ask the user before writing — don't invent.
- Set `**Status:** Pending` and `**Date:** <today's date>`.
- Use `- [ ]` checkboxes only in the Tests section.
- Leave the line about Implemented status untouched — it is a guardrail, not a placeholder.

## After Writing

- Print the path of the new file and a one-line summary.
- Suggest the obvious next step: ask to implement use case `<NNN>` when ready to build it.
- Do **not** start implementing, do **not** commit, do **not** modify any other spec files. This skill's job ends at a written use case.
