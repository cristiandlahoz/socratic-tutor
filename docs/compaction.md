# Chat Compaction

This document explains how Socratic Tutor compacts long chat sessions so the model
can continue with a **summary of older tutoring state** plus **recent raw turns**.

## Purpose

Compaction keeps conversations inside the model context window without deleting the
student-facing transcript.

It preserves:

- learning goal
- current task/topic
- student misconceptions and demonstrated understanding
- hints, questions, examples, and explanations already used
- course material or retrieved facts already referenced
- constraints and preferences
- next best Socratic step

## Budgets

```yaml
app:
  chat:
    context-window-tokens: ${CHAT_CONTEXT_WINDOW_TOKENS:8192}
    compaction-threshold-ratio: ${CHAT_COMPACTION_THRESHOLD_RATIO:0.70}
    recent-history-retention-ratio: ${CHAT_RECENT_HISTORY_RETENTION_RATIO:0.25}
```

Default budget with an `8192` token context window:

```text
┌────────────────────── 8192 token context window ──────────────────────┐
│                                                                       │
│  compaction trigger at 70%                                            │
│  ┌────────────────────────── 5734 tokens ──────────────────────────┐  │
│  │                                                                 │  │
│  │  recent raw history retained after compaction                   │  │
│  │  ┌────────────── 2048 tokens / 25% ──────────────┐              │  │
│  │  └───────────────────────────────────────────────┘              │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘

compactionThresholdTokens    = contextWindowTokens * 0.70 = 5734
recentHistoryRetentionTokens = contextWindowTokens * 0.25 = 2048
```

Meaning:

- When estimated active context exceeds about `5734` tokens, compaction runs.
- After compaction, up to about `2048` tokens of recent real conversation are kept verbatim.
- Older real conversation is summarized and archived.

## Runtime flow

```text
Student submits prompt
        │
        ▼
┌─────────────────────────────────────────┐
│ ChatService.chatStream(...)             │
│                                         │
│ conversationId ──► session id           │
│ member id      ──► session user id      │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ SessionMemoryAdvisor.before(...)        │
│                                         │
│ 1. find/create session                  │
│ 2. load active events                   │
│ 3. prepend active history to prompt     │
│ 4. append current UserMessage           │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ Model generates assistant response      │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ SessionMemoryAdvisor.after(...)         │
│                                         │
│ 1. append AssistantMessage              │
│ 2. TokenCountTrigger checks threshold   │
│ 3. if above threshold: compact          │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ Active context after compaction         │
│                                         │
│ synthetic summary + recent real turns   │
└─────────────────────────────────────────┘
```

## Before and after compaction

Before compaction, active session history may contain many real events:

```text
entry:   0      1      2      3      4      5      6      7      8
       ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
       │ user │ asst │ user │ asst │ tool │ asst │ user │ asst │ asst │
       └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘
        └────────────────────┬───────────────────┘ └──────────┬─────────┘
                  summarize, then archive             keep verbatim
```

Entries `0..5` are summarized and archived. Entries `6..8` are recent enough
to stay in the active context exactly as they happened.

The retention scan walks backward from the newest event while events fit inside the
recent-history token budget, then snaps forward to a complete root user turn:

```text
entry:     0      1      2      3      4      5      6      7      8
         ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
event:   │ user │ asst │ user │ asst │ tool │ asst │ user │ asst │ asst │
         └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘
                                                       ▲      ▲      ▲
                                                       │      │      │
                                     scan direction:   └──────┴──────┘
                                     newest events are counted first

firstKeptEntry = entry 6
reason         = nearest root user event at or after the token-budget cut
```

After compaction, older real events are archived and a synthetic summary turn is
added to the active context:

```text
Stored event log after compaction:

entry:   0      1      2      3      4      5      6      7      8      9     10
       ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬─────┬─────┐
       │ user │ asst │ user │ asst │ tool │ asst │ user │ asst │ asst │ syn │ syn │
       │ real │ real │ real │ real │ real │ real │ real │ real │ real │ usr │ ast │
       └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴─────┴─────┘
        └────────────────────┬───────────────────┘ └──────────┬─────────┘ └──┬───┘
                  archived real events              active real events  summary turn
```

Archived real events stay in storage, but are no longer part of active model
context. The synthetic pair carries the summary forward.

What the model sees on the next request:

```text
┌─────────────┬──────────────┬─────────────────────┬──────────────┐
│ system      │ summary turn │ user │ asst │ asst  │ current user │
└─────────────┴──────────────┴──────┴──────┴───────┴──────────────┘
      │              │          └──────────┬────────┘       │
 base tutor     compacted memory      recent raw       submitted
  prompt        from synthetic turn    events kept       prompt
                                      verbatim
```


## Synthetic summary turn

The compacted memory is stored as a synthetic user/assistant pair:

```text
┌─────────────────────────────────────┐   ┌─────────────────────────────────────┐
│ Synthetic UserMessage               │   │ Synthetic AssistantMessage          │
├─────────────────────────────────────┤   ├─────────────────────────────────────┤
│ Resume la conversación de tutoría   │   │ <summary generated by the           │
│ hasta ahora.                        │   │  compaction model>                  │
└─────────────────────────────────────┘   └─────────────────────────────────────┘
```

This keeps the active model history structurally coherent:

```text
user → assistant → user → assistant → ...
```

## Token-budget retention

Core class:

```text
src/main/java/com/wornux/ai/session/
TokenBudgetRecursiveSummarizationCompactionStrategy.java
```

Algorithm:

```text
1. Split events:

     syntheticEvents = events where event.isSynthetic()
     realEvents      = events where !event.isSynthetic()

2. Walk backward from newest real event.
3. Estimate formatted-event tokens.
4. Stop before adding an event that would exceed recentHistoryRetentionTokens.
5. Snap forward to the nearest root UserMessage.
6. Archive everything before that cut point.
7. Keep everything from that cut point onward verbatim.
```

## Summarization call

Compaction makes **one model call** with two messages:

```text
┌──────────────────────────── one LLM request ────────────────────────────┐
│                                                                         │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ SYSTEM                                                             │ │
│  │ src/main/resources/prompt/compaction/system.st                     │ │
│  │                                                                    │ │
│  │ Instructions for how to summarize tutoring state.                  │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ USER                                                               │ │
│  │ rendered src/main/resources/prompt/compaction/user.st              │ │
│  │                                                                    │ │
│  │ <prior-summary>...</prior-summary>                                 │ │
│  │ <conversation-to-summarize>...</conversation-to-summarize>         │ │
│  │ <upcoming-context purpose="continuity-only">...</upcoming-context> │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   ▼
                          summary string
```

```java
chatClient.prompt()
    .system(systemPrompt)
    .user(renderedUserPrompt)
    .call()
    .content();
```

## Prompt templates

```text
src/main/resources/prompt/compaction/system.st
src/main/resources/prompt/compaction/user.st
```

The user template is rendered with:

- `priorSummary`: previous synthetic assistant summaries, if the conversation was
  compacted before.
- `conversationToSummarize`: older real events that will be archived after this
  compaction.
- `upcomingContext`: first retained recent events. These help continuity, but are
  not old context to summarize.

Template shape:

```xml
<prior-summary>
$priorSummary$
</prior-summary>

<conversation-to-summarize>
$conversationToSummarize$
</conversation-to-summarize>

<upcoming-context purpose="continuity-only">
$upcomingContext$
</upcoming-context>
```

## Repeated compaction

```text
First compaction:

entry:  0      1      2      3      4
      ┌──────┬──────┬──────┬──────┬──────┐
      │ user │ asst │ user │ asst │ synA │
      └──────┴──────┴──────┴──────┴──────┘
       └────────┬────────┘ └────┬────┘  ▲
                │               │       │
             archived          kept  summary A

Second compaction:

entry:  4      5      6      7      8
      ┌──────┬──────┬──────┬──────┬──────┐
      │ synA │ user │ asst │ user │ synA │
      └──────┴──────┴──────┴──────┴──────┘
          ▲    └──────┬──────┘ └────┬────┘
          │           │             │
   prior summary   newly old      summary B
                   events

Summary B incorporates Summary A plus newly archived events.
```

## Tool output handling

Each formatted event is truncated at `2000` characters before it is sent to the
summarizer.

```text
Tool [responses: searchCourseMaterial -> The first 2000 chars...
... [truncated 18342 chars]]
```

## Storage semantics

```text
archived event
  - not sent to the model by EventFilter.active()
  - still stored in Spring AI Session
  - still available to display/history queries that do not exclude archived

synthetic event
  - generated by compaction
  - useful for model context
  - excluded from student-facing transcript
```

```text
                    ┌──────────────────────────────┐
                    │ Spring AI Session storage    │
                    └───────────────┬──────────────┘
                                    │
          ┌─────────────────────────┴─────────────────────────┐
          │                                                   │
          ▼                                                   ▼
┌──────────────────────────────┐          ┌──────────────────────────────┐
│ Model context                │          │ Chat UI transcript           │
│ EventFilter.active()         │          │ excludeSynthetic(true)       │
└──────────────┬───────────────┘          └──────────────┬───────────────┘
               │                                         │
               ▼                                         ▼
┌──────────────────────────────┐          ┌──────────────────────────────┐
│ summary + recent real turns  │          │ full real transcript         │
└──────────────────────────────┘          └──────────────────────────────┘
```

## Implementation map

Core strategy:

```text
src/main/java/com/wornux/ai/session/
TokenBudgetRecursiveSummarizationCompactionStrategy.java
```

Wiring:

```text
src/main/java/com/wornux/config/AIConfig.java
```

Properties:

```text
src/main/java/com/wornux/config/ChatProperties.java
src/main/resources/application.yml
```

Prompt resources:

```text
src/main/java/com/wornux/ai/prompt/PromptResources.java
src/main/java/com/wornux/ai/prompt/PromptUtil.java
src/main/resources/prompt/compaction/system.st
src/main/resources/prompt/compaction/user.st
```

Focused test:

```text
src/test/java/com/wornux/ai/session/
TokenBudgetRecursiveSummarizationCompactionStrategyTest.java
```

## Future improvements

- Preflight compaction before model calls when estimated request size is too large.
- Overflow recovery: compact and retry when the provider returns a context-length error.
- Adaptive retention: keep 20-35% recent history depending on current prompt and
  dynamic context size.
- Metrics: compaction count, latency, tokens saved, summary size, and failure count.
- Summary quality tests using representative tutoring conversations.
