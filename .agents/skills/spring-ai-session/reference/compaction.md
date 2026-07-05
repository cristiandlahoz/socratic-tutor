# Compaction, triggers, and strategies

Compaction reduces active prompt history without deleting the full event log.

## Entry point

`SessionService.compact(sessionId, trigger, strategy)`:

1. Reads current `event_version`.
2. Fetches events.
3. Evaluates the trigger.
4. Runs the strategy only if triggered.
5. Writes with compare-and-swap semantics; if the version changed, compaction is skipped.

## Triggers

- `TurnCountTrigger`: fires when complete real root turns exceed a threshold.
- `TokenCountTrigger`: fires when estimated tokens exceed a threshold.
- `CompositeCompactionTrigger`: OR-combines triggers.

## Strategies

- `SlidingWindowCompactionStrategy`: keep the last N real events.
- `TokenBudgetCompactionStrategy`: keep events within token budget.
- `SummarizationCompactionStrategy`: replace older context with a summary.
- `RecursiveSummarizationCompactionStrategy`: rolling summary plus active window for long sessions.

## Archive semantics

Compacted events are marked `archived=true`; they are excluded from active prompt context but remain available for recall/search. Do not delete them.

## Synthetic summaries

Summary turns are marked synthetic and usually placed before the retained active window. Strategies preserve synthetic events specially.

## Docs

- `spring-ai-session-docs/session-management/compaction.md`
- `spring-ai-session-docs/session-management/recall-storage.md`
