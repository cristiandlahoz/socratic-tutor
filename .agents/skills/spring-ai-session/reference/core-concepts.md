# Spring AI Session core concepts

Spring AI Session stores conversation memory as a session metadata row plus an append-only event log.

## Main model

- `Session`: metadata only, such as `id`, `userId`, TTL, metadata, and event version.
- `SessionEvent`: one stored message/event in the conversation log.
- `SessionService`: high-level API for create, append, read, compact, and delete.
- `SessionRepository`: persistence SPI; this project uses JDBC.
- `SessionMemoryAdvisor`: integrates with `ChatClient`, reads history before a call, appends user/assistant events around the model response.

## Turns

A turn is one real root `USER` event plus following assistant/tool events until the next `USER` event. Turn counting ignores synthetic events and branch-specific user events.

## Ordering

Events are returned in logical insertion order. JDBC uses the monotonic `seq` column for this. Timestamps are metadata and must not be used as the canonical order.

## Synthetic events

Compaction may create synthetic summary turns. They are kept as normal context but marked synthetic so triggers and strategies can treat them specially.

## Where to read more

- `spring-ai-session-docs/session-management/concepts.md`
- `spring-ai-session-docs/session-management/chat-client.md`
- `spring-ai-session-docs/migration.md`
