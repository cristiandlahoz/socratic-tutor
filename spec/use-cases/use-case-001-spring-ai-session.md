# UC-001: Persist Tutor Conversation History with Spring AI Session

**Goal:** As an authenticated group-class member, I want my tutor conversation history to persist reliably so that I can continue an authorized conversation without application-managed message or compaction logic.

**Status:** Implemented
**Date:** 2026-06-24

## Actors

- **Primary actor:** Authenticated professor or student with an active group-class membership.
- **Secondary actor:** Spring AI Session and the configured chat model provider.

## Preconditions

- The actor is authenticated and has an active academic context.
- The domain conversation belongs to the actor's active group-class membership.
- PostgreSQL contains the Flyway-managed Spring AI Session schema.

## Trigger

The actor opens an existing tutor conversation or submits a prompt in a new conversation.

## Main Flow

1. The actor creates or selects a domain conversation.
2. The system verifies ownership through the active group-class membership.
3. The system uses the domain conversation id as the Spring AI Session id and the membership id as its owner id.
4. The Spring AI Session advisor loads active context, records the user and assistant events, and compacts the context when the configured token threshold is reached.
5. The system loads display history from the full Spring AI Session event log.
6. The UI shows only real root user messages and displayable assistant replies.
7. When provider response metadata reports prompt tokens, the system stores and displays that usage for the domain conversation.

## Alternative Flows

### AF-1: Conversation is not owned

**Branches from:** Main Flow step 2
**Condition:** The requested conversation does not belong to the active group-class membership.

1. The system denies access without reading or mutating Session events.
2. Use case ends.

### AF-2: Conversation has no Session events

**Branches from:** Main Flow step 5
**Condition:** The domain conversation is new or has not completed a tutor turn.

1. The system returns an empty display history.
2. The domain conversation remains available for the first prompt.

### AF-3: Internal events exist

**Branches from:** Main Flow step 6
**Condition:** The event log contains tool, synthetic summary, branched, blank, or tool-call assistant events.

1. The system excludes those events from normal user-visible history.
2. The events remain available to Spring AI Session for context, compaction, or recall behavior.

### AF-4: Provider omits token usage

**Branches from:** Main Flow step 7
**Condition:** The streamed response has no prompt-token metadata.

1. The system does not invent or overwrite token usage.
2. Conversation history still persists through the advisor.

## Postconditions

- **On success:** Domain ownership and metadata remain in `conversation`; history and compaction state are in Spring AI Session event tables.
- **On failure:** Unauthorized Session data is not exposed or mutated, and no approximate token usage is persisted.

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | `conversation.id` is the Spring AI Session id. |
| BR-02 | `group_class_member.id` is passed as the Session user id on every chat request. |
| BR-03 | The Session advisor exclusively persists normal user and assistant history. |
| BR-04 | Spring AI Session compaction replaces the removed application-managed compaction flow. |
| BR-05 | Display history includes archived real events but excludes synthetic, tool, branched, blank, and tool-call events. |
| BR-06 | Domain ownership is checked before any Session history read or chat request. |
| BR-07 | Token usage is updated only from actual provider response metadata. |

## Tests

- [x] Main Flow covered (steps 1-7)
- [x] AF-1 through AF-4 covered
- [x] BR-01 through BR-07 covered

## UI Surface

- Existing authenticated tutor chat and conversation history surfaces.
- The compacted-context indicator remains, without custom snapshot generation or lineage details.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Tutor chat | Authenticated group-class member | Chat workspace |
