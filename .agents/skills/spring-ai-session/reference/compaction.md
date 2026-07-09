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

## Token-budget recursive summarization

Thesis: token-budget compaction keeps a coherent recent suffix, not an
arbitrary list of messages. The retained budget, for example 2,000 tokens,
applies to the newest real, non-synthetic events. Events older than the
selected boundary are archived and summarized; the retained suffix remains in
the active prompt context.

Thesis: synthetic summary turns are not ordinary history for the budget cut.
Strategies first separate synthetic events from real events. Prior synthetic
assistant summaries are used as summary input, then the strategy writes a fresh
synthetic summary turn before the retained active window. The raw token-budget
walk is performed over real events only.

Thesis: the raw token boundary is only a candidate. A backward token walk can
stop on an assistant message, tool call, tool response, or branched/internal
message. Keeping from that point would expose the model to an orphaned tail of
a turn. The strategy must move the cut forward until the retained active window
starts at a root-level `USER` event.

Thesis: `isRootEvent()` is required because not every `USER` event is a student
turn boundary. Branched agents and tool-like workflows may record user-shaped
messages on non-root branches. Those messages can be prompts to internal agents,
not the beginning of the main conversation turn. Treating them as valid cut
points can retain a subtree without its parent turn.

A tree shape to avoid:

```text
root conversation
├─ USER: "Explain pointers"                  ← valid root turn start
├─ ASSISTANT: calls helper/tool
├─ branch: researcher
│  ├─ USER: "Find a compact explanation"     ← not a root turn start
│  └─ ASSISTANT: "Use address/value language"
├─ TOOL: helper result
└─ ASSISTANT: final answer
```

If the token budget raw cut lands at the branched `USER`, the active context
would start inside the work caused by `"Explain pointers"`. That loses the
student's actual question and keeps an internal prompt as if it were the main
turn. Snapping forward to the next root-level `USER` prevents this. If no such
root user exists after the raw cut, the active real window can be empty and the
strategy summarizes the real events instead of keeping an incoherent tail.

## Archive semantics

Compacted events are marked `archived=true`; they are excluded from active prompt context but remain available for recall/search. Do not delete them.

## Synthetic summaries

Summary turns are marked synthetic and usually placed before the retained active window. Strategies preserve synthetic events specially.

## Docs

- `spring-ai-session-docs/session-management/compaction.md`
- `spring-ai-session-docs/session-management/recall-storage.md`
