---
name: spring-ai-session
description: Use whenever Spring AI Session, ai_session, ai_session_event, session memory, compaction, event_version, or chat history persistence is mentioned.
---

# Spring AI Session

Use this skill for any task involving Spring AI Session or the JDBC-backed chat memory tables.

## Fast rules

- Read project docs first: `spring-ai-session-docs/`.
- Start with `reference/core-concepts.md` for the mental model.
- Use `reference/jdbc-schema.md` before touching DB rows.
- Use `reference/compaction.md` before changing triggers or strategies.
- Events are append-only and ordered by DB `seq`, not timestamps.
- Increment `ai_session.event_version` when appending events manually.
- Prefer app APIs/services over raw SQL; use SQL only for local data setup/debugging.
- Never delete compacted history; compaction archives events.

## Key project files

- Dependency/config: `pom.xml`, `src/main/resources/application.yml`
- Local docs: `spring-ai-session-docs/index.md`
- JDBC docs: `spring-ai-session-docs/session-jdbc/index.md`
- Concepts: `spring-ai-session-docs/session-management/concepts.md`
- Compaction: `spring-ai-session-docs/session-management/compaction.md`
