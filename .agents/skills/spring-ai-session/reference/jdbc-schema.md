# JDBC schema and manual DB work

This project uses the Spring AI Session JDBC repository.

## Tables

- `ai_session`: session metadata.
- `ai_session_event`: append-only event log.

There is no `ai_events` table in the current schema; check actual DB with `\dt` before writing SQL.

## Important columns

`ai_session`:

- `id`: session id.
- `user_id`: owner.
- `created_at`, `expires_at`: lifecycle.
- `metadata`: JSON text.
- `event_version`: optimistic-lock/version counter.

`ai_session_event`:

- `seq`: generated identity, canonical event order.
- `id`: event id, usually UUID.
- `session_id`: FK to `ai_session`.
- `timestamp`: event time, not canonical order.
- `message_type`: usually `USER`, `ASSISTANT`, `SYSTEM`, or tool-related types.
- `message_content`: text/markdown content.
- `message_data`: JSON data for tool calls/responses when needed.
- `synthetic`, `archived`, `branch`, `metadata`.

## Manual insert checklist

1. Ensure the `ai_session` row exists.
2. Insert into `ai_session_event` without specifying `seq`.
3. Use UUIDs for `id`.
4. Set `synthetic=false`, `archived=false`, `branch=NULL`, `metadata='{}'` for normal root messages.
5. Increment `ai_session.event_version` by the number of appended events.

## Docs

- `spring-ai-session-docs/session-jdbc/index.md`
- bundled schema path: `org/springframework/ai/session/jdbc/schema-postgresql.sql`
