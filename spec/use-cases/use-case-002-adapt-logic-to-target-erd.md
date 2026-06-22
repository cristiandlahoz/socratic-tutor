# UC-002: Adapt Active Logic to Target ERD

---

**Goal:** As the development team, I want to adapt the active business logic, services, DTOs, and UI flows to operate on the target ERD introduced by UC-001 so that the application runs on the new academic multi-tenant model without depending on obsolete legacy persistence.

**Status:** Pending  
**Date:** 2026-06-22

---

## Scope

This use case covers the runtime and application-layer adaptation required after UC-001.

UC-001 defines the target academic multi-tenant schema. UC-002 adapts the active code so the application actually uses that schema.

This use case includes:

- Adapting the active chat flow from the obsolete `chat`, `chat_transcript`, and `chat_message` persistence model to:
  - `conversation`
  - `conversation_snapshot`
- Adapting active conversation listing, loading, creation, message appending, snapshotting, token usage, and compaction behavior to the new conversation model.
- Preserving the current chat UI where possible while changing the backing services to use `conversation` and `conversation_snapshot`.
- Adapting document ingestion, document retrieval, and document-grounding logic from the obsolete document ingestion model to:
  - `grounding_collection`
  - `grounding_document`
  - `grounding_chunk`
- Adapting evaluation logic from the obsolete global evaluation/evaluation-run model to:
  - `evaluation`
  - `evaluation_assignment`
- Replacing old `client_id` persistence assumptions with the new academic identity chain:
  - `account`
  - `tenant_account`
  - `group_class_member`
- Isolating only the truly obsolete student-profile persistence and logic as legacy.
- Keeping reusable UI, service, DTO, guardrail, routing, and AI logic active when it can be adapted to the target ERD.
- Removing active runtime dependencies on old repositories, old JPA entities, and old persistence tables.
- Fixing Spring/Vaadin wiring so active beans are scanned and legacy beans are not.
- Running application verification with `mvn`.

This use case does not include:

- Creating a full onboarding flow.
- Creating a full professor invitation flow.
- Creating a full student join-code flow.
- Creating a complete production authentication system.
- Designing new UI screens from scratch.
- Migrating historical legacy data into the new tables.
- Reintroducing obsolete legacy tables as active persistence.
- Rebuilding the learner-profile/misconception-tracking feature.
- Reintroducing `evaluation_run`.
- Reintroducing `conversation_message`.
- Treating browser cookies or anonymous client identifiers as the new source of identity.

---

## Relationship to UC-001

UC-001 establishes the target academic multi-tenant ERD.

UC-002 makes the running application conform to that ERD.

UC-001 is schema-oriented.

UC-002 is runtime-oriented.

The key difference is:

```text
UC-001 creates the target schema.
UC-002 adapts the application to use the target schema.
```

UC-002 must not undo UC-001 by reactivating obsolete persistence.

---

## Current Runtime Problem

The application has a clean target ERD direction, but parts of the runtime still assume the old persistence model.

Examples of obsolete assumptions:

```text
Browser cookie -> client_id
client_id -> chat
chat -> chat_transcript
chat_transcript -> chat_message

client_id -> ingested_document
ingested_document -> document_segment
document_segment -> vector_store

evaluation -> evaluation_run
evaluation_run -> student_client_id

client_id -> student_profile
student_profile -> student_misconception
student_profile_signal -> old chat
```

The target runtime must instead use:

```text
account
  -> tenant_account
      -> group_class_member
          -> conversation
              -> conversation_snapshot
```

For documents:

```text
group_class
  -> grounding_collection
      -> grounding_document
          -> grounding_chunk
```

For evaluations:

```text
group_class
  -> evaluation
      -> evaluation_assignment
```

---

## Target Runtime Direction

The active application must move from this:

```text
BrowserClientService resolves client_id
  -> ChatService uses legacy Chat
  -> ConversationService uses legacy Chat/Transcript/Message
  -> ChatUsageService reads legacy transcript token usage
  -> ChatCompactionService creates legacy transcripts
```

To this:

```text
ApplicationContextResolver resolves account / tenant_account / group_class_member
  -> Active conversation services use Conversation
  -> Messages are persisted inside ConversationSnapshot.messages
  -> Carry context is persisted inside ConversationSnapshot.carry_context
  -> Token usage is computed from ConversationSnapshot.token_count
  -> Compaction creates a new ConversationSnapshot
```

The active application must move from this:

```text
DocumentIngestionService creates ingested_document
DocumentEmbeddingService creates document_segment / vector_store entries
DocumentCatalogPromptService reads legacy document catalog
```

To this:

```text
DocumentIngestionService creates grounding_document
DocumentEmbeddingService creates grounding_chunk
Document retrieval reads grounding chunks scoped to group class
Document catalog reads grounding documents scoped to group class
```

The active application must move from this:

```text
EvaluationService creates global evaluation
EvaluationRunService creates evaluation_run for student_client_id
EvaluationChatService uses old evaluation_run state
```

To this:

```text
EvaluationService creates group-class evaluation
EvaluationAssignmentService manages student evaluation_assignment
Evaluation execution updates evaluation_assignment status
```

---

## Legacy Policy

UC-002 must not treat all old packages as legacy by default.

The correct policy is:

```text
Move or isolate obsolete persistence models.
Adapt reusable active business logic to the target ERD.
```

The main legacy block is:

```text
student_profile
student_misconception
student_profile_signal
```

This block has no direct target equivalent in the current ERD.

It may return later as a redesigned learner-profile feature, but it must not participate in the active runtime now.

The old chat persistence model is legacy as persistence, but the chat feature is not legacy.

Therefore:

```text
Old Chat entity/repository code becomes legacy.
Active chat UI and services are adapted to conversation/conversation_snapshot.
```

The old document ingestion persistence model is legacy as persistence, but document grounding is not legacy.

Therefore:

```text
Old ingested_document/document_segment persistence becomes legacy.
Active document ingestion/retrieval logic is adapted to grounding_*.
```

The old evaluation-run persistence model is legacy as persistence, but evaluation is not legacy.

Therefore:

```text
Old evaluation_run persistence becomes legacy.
Active evaluation behavior is adapted to evaluation/evaluation_assignment.
```

---

## Actors

- **Primary actor:** Development team

---

## Preconditions

- UC-001 exists and defines the target academic multi-tenant ERD.
- `src/main/resources/db/migration/V1__baseline.sql` is the active baseline migration.
- The active baseline contains the target ERD tables required for:
  - identity
  - tenants
  - authorization
  - academic structure
  - group classes
  - conversations
  - grounding
  - evaluations
- Target JPA entities exist under the active data model packages.
- Target repositories exist under the active repository packages.
- Legacy JPA entities and repositories have been identified.
- Existing runtime code still contains services, UI flows, advisors, DTOs, or config classes that assume the old persistence model.
- The development team accepts that onboarding, invitation, and full authorization workflows are handled by later use cases.
- The application must still be able to start with `mvn` after the adaptation.
- The adaptation must not reintroduce obsolete tables as active schema dependencies.

---

## Trigger

The development team needs to adapt the active application flows and business logic to the target ERD so the system can operate on the new schema instead of the obsolete legacy persistence model.

In practical terms, the trigger is:

```text
We need to do this adaptation so the application becomes functional after UC-001.
```

---

# Main Flow

---

## Stage 1: Establish Active Runtime Boundaries

### Purpose

Determine which code remains active, which code is adapted, and which code is isolated as legacy.

### Flow

1. **Development team** reviews UC-001 and confirms the target ERD as the source of truth.
2. **Development team** reviews the current package structure.
3. **Development team** identifies active runtime code that still imports or injects legacy entities, repositories, services, or config.
4. **Development team** classifies each code area into one of these categories:

```text
Adapt to target ERD
Move/isolate as legacy
Keep active without changes
Delete only if proven unused and safe
```

5. **Development team** confirms that active packages must not depend on `com.wornux.legacy`.
6. **Development team** confirms that `Application.java` must not exclude active UI or service packages from component scanning.
7. **Development team** confirms that only legacy packages should be excluded from active startup.

### Result

```text
The application has a clear runtime boundary:
active code uses the target ERD;
legacy code is isolated and not scanned as active runtime.
```

---

## Stage 2: Replace Browser `client_id` Persistence Assumptions

### Purpose

Stop treating the browser-generated `client_id` as the persistence identity for conversations, documents, evaluations, or student state.

### Current State

The old runtime assumes:

```text
Browser cookie -> client_id -> chat/document/evaluation/profile data
```

### Target State

The runtime must use:

```text
account
  -> tenant_account
      -> group_class_member
```

for persisted academic activity.

### Flow

1. **Development team** reviews `BrowserClientService`.
2. **Development team** removes direct dependency on old chat configuration such as `ChatProperties#getClientIdCookieName`.
3. **Development team** keeps browser identity concerns separate from chat persistence.
4. **Development team** introduces or uses a neutral browser/session identity configuration if needed.
5. **Development team** ensures browser cookies are not treated as the source of academic identity.
6. **Development team** creates or identifies an active context resolver responsible for resolving the current:
   - account
   - tenant account
   - group-class member
7. **System** blocks persisted academic operations if no valid group-class member context exists.
8. **System** does not create conversations, grounding documents, or evaluation assignments only from a browser client identifier.

### Result

```text
The old client_id assumption is removed from active persistence.
Browser identity may exist only as browser/session infrastructure, not as the domain identity model.
```

---

## Stage 3: Adapt Chat Domain to Conversation Model

### Purpose

Keep the chat feature active while replacing old chat persistence with `conversation` and `conversation_snapshot`.

### Current State

The old chat domain uses:

```text
chat
chat_transcript
chat_message
```

Old service dependencies may include:

```text
ChatRepository
ChatTranscriptRepository
ChatMessageRepository
ChatService
ConversationService
ChatUsageService
ChatCompactionService
PostgresChatMemory
```

### Target State

The active chat domain uses:

```text
conversation
conversation_snapshot
```

Target service dependencies should include:

```text
ConversationRepository
ConversationSnapshotRepository
```

### Flow

1. **Development team** identifies all active services that currently use old chat entities or repositories.
2. **Development team** rewrites conversation creation to create a `conversation` row linked to a valid `group_class_member`.
3. **Development team** rewrites message persistence to update or create `conversation_snapshot` rows instead of inserting `chat_message` rows.
4. **Development team** stores the current message list in `conversation_snapshot.messages`.
5. **Development team** stores compacted conversation memory in `conversation_snapshot.carry_context`.
6. **Development team** stores the approximate token count in `conversation_snapshot.token_count`.
7. **Development team** stores message count in `conversation_snapshot.message_count`.
8. **Development team** updates `conversation.current_snapshot_id` after creating a new current snapshot.
9. **Development team** rewrites conversation loading to return existing DTOs from the new model:
   - `ConversationSummary`
   - `StoredChatMessage`
   - `ResolvedConversation`
10. **Development team** preserves DTO contracts where possible so UI changes remain minimal.
11. **Development team** removes active use of old chat repositories.
12. **System** can list conversations for the current group-class member using the target ERD.
13. **System** can load a conversation from `conversation` and its current snapshot.
14. **System** can append a user message and assistant message into a new or updated snapshot.
15. **System** can render the chat UI without depending on old chat persistence.

### Result

```text
The active chat flow uses conversation and conversation_snapshot as its source of truth.
Old chat tables and repositories are no longer active runtime dependencies.
```

---

## Stage 4: Adapt Conversation Snapshot and Compaction Logic

### Purpose

Replace legacy transcript compaction with snapshot-based conversation compaction.

### Current State

The old compaction model uses:

```text
chat_transcript.memory
chat_transcript.input_tokens
chat_transcript.compacted_from_transcript_id
chat_transcript.compaction_level
```

### Target State

The new compaction model uses:

```text
conversation_snapshot.previous_snapshot_id
conversation_snapshot.snapshot_no
conversation_snapshot.carry_context
conversation_snapshot.messages
conversation_snapshot.message_count
conversation_snapshot.token_count
conversation_snapshot.compacted_at
conversation.current_snapshot_id
```

### Flow

1. **Development team** reviews `ChatUsageService`.
2. **Development team** reviews `ChatCompactionService`.
3. **Development team** replaces transcript token usage calculations with snapshot token usage calculations.
4. **Development team** replaces transcript compaction with snapshot creation.
5. **System** creates a new `conversation_snapshot` when compaction occurs.
6. **System** sets the new snapshot's `previous_snapshot_id` to the old current snapshot.
7. **System** increments `snapshot_no`.
8. **System** writes compacted context to `carry_context`.
9. **System** writes retained messages to `messages`.
10. **System** updates `message_count`.
11. **System** updates `token_count`.
12. **System** sets `compacted_at` when compaction occurs.
13. **System** updates `conversation.current_snapshot_id` to the new snapshot.
14. **Development team** moves any purely legacy transcript DTOs to legacy if they cannot describe the new snapshot model cleanly.
15. **Development team** keeps DTOs that represent active conversation concepts.

### Result

```text
Conversation compaction is represented by immutable or versioned snapshots instead of legacy transcript rows.
```

---

## Stage 5: Adapt Chat UI and View Models

### Purpose

Keep the existing chat UI operational while replacing its persistence backing.

### Current State

The chat UI uses active Vaadin classes such as:

```text
ChatView
ChatViewModel
ChatState
MainLayout
ChatTurnOrchestrator
ChatNavigationOrchestrator
ChatThemeOrchestrator
```

These may currently depend on services that still use old chat persistence.

### Target State

The UI remains active, but its backing services use the target conversation model.

### Flow

1. **Development team** reviews all chat UI constructor dependencies.
2. **Development team** ensures active Vaadin UI state classes are scanned by Spring.
3. **Development team** removes component-scan exclusions that hide active UI beans.
4. **Development team** keeps route-scoped UI state active when it does not depend directly on legacy persistence.
5. **Development team** adapts UI calls to use conversation-oriented services.
6. **Development team** ensures `MainLayout` no longer requires legacy-only state or services.
7. **System** can create `ChatState` as a Spring/Vaadin bean.
8. **System** can instantiate `MainLayout`.
9. **System** can navigate to the chat route without `NoSuchBeanDefinitionException`.
10. **System** shows an appropriate empty state if no active academic context exists.
11. **System** does not create a persisted conversation without a valid group-class member.

### Result

```text
The chat UI remains active and uses services backed by conversation/conversation_snapshot.
```

---

## Stage 6: Adapt Document Ingestion to Grounding

### Purpose

Replace the old document ingestion persistence model with group-class scoped grounding.

### Current State

The old document ingestion model uses:

```text
ingested_document
document_ingestion_job
document_segment
vector_store
```

Old services may include:

```text
DocumentIngestionService
DocumentEmbeddingService
DocumentCatalogPromptService
DoclingClientService
```

### Target State

The active grounding model uses:

```text
grounding_collection
grounding_document
grounding_chunk
```

### Flow

1. **Development team** reviews document ingestion services.
2. **Development team** replaces old document entity persistence with `grounding_document`.
3. **Development team** ensures each grounding document belongs to a `grounding_collection`.
4. **Development team** ensures each grounding collection belongs to a `group_class`.
5. **Development team** ensures each grounding collection has a `created_by_group_class_member_id`.
6. **Development team** replaces old segment persistence with `grounding_chunk`.
7. **Development team** stores chunk content in `grounding_chunk.content`.
8. **Development team** stores embedding data in `grounding_chunk.embedding` when available.
9. **Development team** updates document status using the target statuses:
   - `PROCESSING`
   - `READY`
   - `FAILED`
   - `INACTIVE`
10. **Development team** adapts document catalog queries to read grounding documents by group class.
11. **Development team** adapts retrieval logic to retrieve grounding chunks scoped to the active group class.
12. **System** does not read from old document ingestion tables.
13. **System** does not require old `document_segment` or `vector_store` tables as active persistence.

### Result

```text
Document ingestion and retrieval are backed by group-class grounding entities.
```

---

## Stage 7: Adapt Document Ingestion UI

### Purpose

Keep the document ingestion UI operational where possible while changing its backing model to grounding.

### Flow

1. **Development team** reviews document ingestion UI classes.
2. **Development team** keeps UI components that can work with grounding DTOs.
3. **Development team** replaces old document identifiers with grounding document identifiers.
4. **Development team** updates displayed statuses to match `grounding_document.status`.
5. **Development team** scopes document lists to the active group class.
6. **System** shows uploaded or text grounding documents for the active group class.
7. **System** shows an empty state if no group-class context exists.
8. **System** prevents grounding uploads if the current user cannot resolve to a professor or authorized group-class member.

### Result

```text
The document ingestion UI remains conceptually active but is backed by grounding_* persistence.
```

---

## Stage 8: Adapt Evaluation Domain to Evaluation Assignment

### Purpose

Replace old global evaluation runs with group-class evaluations and student assignments.

### Current State

The old evaluation runtime uses:

```text
evaluation
evaluation_run
student_client_id
```

The old evaluation model may include:

```text
questions_json
answers_json
report_markdown
```

### Target State

The target model uses:

```text
evaluation
evaluation_assignment
```

with:

```text
evaluation.group_class_id
evaluation.created_by_group_class_member_id
evaluation_assignment.group_class_member_id
```

### Flow

1. **Development team** reviews evaluation services.
2. **Development team** separates old evaluation-run behavior from the target assignment model.
3. **Development team** adapts evaluation creation to create a group-class scoped `evaluation`.
4. **Development team** ensures evaluation creation requires a professor or authorized group-class member context.
5. **Development team** adapts assignment creation to create one `evaluation_assignment` per target student group-class member.
6. **Development team** replaces `evaluation_run` lifecycle with `evaluation_assignment.status`.
7. **System** supports the assignment lifecycle:

```text
ASSIGNED -> STARTED -> SUBMITTED
```

8. **System** also supports terminal or administrative states:

```text
SKIPPED
EXPIRED
EXCUSED
```

9. **Development team** adapts student evaluation execution to update the student's own assignment.
10. **Development team** prevents a student from updating another student's assignment.
11. **Development team** prevents use of `evaluation_run` in active services.
12. **System** can list evaluations by group class.
13. **System** can list assignments by student group-class member.
14. **System** can update assignment status through allowed transitions.

### Result

```text
Active evaluation behavior uses evaluation and evaluation_assignment.
evaluation_run is no longer an active runtime concept.
```

---

## Stage 9: Adapt Evaluation UI

### Purpose

Keep the evaluation UI aligned with the new evaluation assignment model.

### Flow

1. **Development team** reviews existing evaluation UI classes.
2. **Development team** identifies UI assumptions tied to old global evaluations or evaluation runs.
3. **Development team** changes professor-facing evaluation screens to work with group-class evaluations.
4. **Development team** changes student-facing evaluation execution screens to work with `evaluation_assignment`.
5. **System** shows evaluations available in the current group-class context.
6. **System** shows a student's own assigned evaluations.
7. **System** prevents students from seeing or updating assignments that do not belong to them.
8. **System** shows an empty or setup-required state when no group-class context exists.

### Result

```text
Evaluation UI uses the target assignment model instead of legacy evaluation runs.
```

---

## Stage 10: Isolate Student Profile as Legacy

### Purpose

Remove the obsolete student-profile persistence block from active runtime.

### Current State

The obsolete profile block includes:

```text
student_profile
student_misconception
student_profile_signal
```

Related active or semi-active code may include:

```text
StudentProfileService
ProfileAwareResponseAdvisor
StudentProfilePromptMapper
ProfileProperties
ThemePreference
```

### Target State

The student-profile block is isolated as legacy.

It does not participate in:

```text
identity
membership
authorization
conversation ownership
evaluation assignment
grounding scope
```

### Flow

1. **Development team** identifies all code that directly depends on `StudentProfile`, `StudentMisconception`, or `StudentProfileSignal`.
2. **Development team** moves or isolates this code under a legacy package.
3. **Development team** removes student-profile services from active advisors and active startup.
4. **Development team** ensures active code does not inject `StudentProfileService`.
5. **Development team** ensures active code does not rely on `ThemePreference` if it belongs to the obsolete profile model.
6. **Development team** documents that learner-profile behavior requires a future use case.
7. **System** starts without attempting to create student-profile beans.
8. **System** starts without requiring student-profile tables.

### Result

```text
Student profile is legacy-only.
Active runtime does not use it.
```

---

## Stage 11: Adapt AI Advisors, Guards, Tools, and Prompt Services

### Purpose

Keep AI behavior active when it is independent of legacy persistence and isolate only advisor logic that still depends on obsolete models.

### Flow

1. **Development team** reviews AI advisors and tools.
2. **Development team** keeps guardrail code active if it does not depend on legacy persistence.
3. **Development team** keeps pedagogical routing active if it does not depend on legacy persistence.
4. **Development team** keeps tool execution auditing active if it does not depend on legacy persistence.
5. **Development team** keeps retrieval tools active only if they query target grounding data.
6. **Development team** adapts document retrieval tools to grounding chunks where needed.
7. **Development team** removes or isolates profile-aware advisors that depend on `StudentProfileService`.
8. **Development team** removes or isolates subject-config advisors that depend on obsolete `subject_config_revision`.
9. **Development team** removes or adapts document catalog advisors that depend on old ingested documents.
10. **System** still applies tutor safety and guardrails.
11. **System** does not require old profile, old subject config, or old document ingestion services to generate a response.

### Result

```text
AI guardrails and tools remain active when compatible with the target ERD.
Legacy-dependent advisors are isolated or adapted.
```

---

## Stage 12: Fix Spring and Vaadin Startup Wiring

### Purpose

Ensure active beans are scanned and legacy beans are excluded correctly.

### Current State

The application may fail if `Application.java` excludes entire active packages such as:

```text
com.wornux.ui.chat
com.wornux.ui.evaluation
com.wornux.ui.ingestion
com.wornux.services.chat
com.wornux.services.document
com.wornux.services.evaluation
```

This is too broad if those packages contain active code adapted to the target ERD.

### Target State

Spring scanning excludes only the actual legacy package area.

### Flow

1. **Development team** reviews `Application.java`.
2. **Development team** removes broad package exclusions that hide active UI and service beans.
3. **Development team** ensures legacy-only packages are excluded from active component scanning.
4. **Development team** ensures target entities are included in `@EntityScan`.
5. **Development team** ensures target repositories are included in `@EnableJpaRepositories`.
6. **Development team** ensures legacy repositories are not active repository beans.
7. **Development team** ensures Vaadin route-scoped beans are registered when they belong to active UI flows.
8. **System** can create `ChatState` or equivalent active route-scoped UI state.
9. **System** can create `MainLayout` or equivalent active layout without missing-bean errors.
10. **System** can start without trying to initialize legacy JPA repositories.

### Result

```text
Spring and Vaadin startup wiring reflects the active target ERD runtime.
```

---

## Stage 13: Verify Runtime Startup

### Purpose

Confirm that the application runs after the adaptation.

### Flow

1. **Development team** runs compile verification.
2. **Development team** runs test verification.
3. **Development team** runs the application with Maven.
4. **System** validates Flyway migrations.
5. **System** initializes Hibernate with target entities only.
6. **System** initializes Spring Data repositories for target repositories only.
7. **System** initializes Vaadin active routes and scoped beans.
8. **System** starts Tomcat.
9. **System** exposes the application at the configured port.
10. **Development team** records the final Maven outcome.

### Required command

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

### Result

```text
The app starts on the target ERD without active legacy persistence dependencies.
```

---

# Alternative Flows

---

## AF-1: Missing Active Academic Context

**Branches from:** Stage 2, Stage 3, Stage 6, Stage 8  
**Condition:** The current request cannot resolve a valid `group_class_member`.

1. **System** does not create a persisted conversation, grounding document, or evaluation assignment.
2. **System** shows a setup-required or no-active-class-context state.
3. **System** avoids falling back to `client_id`.
4. **Use case continues** after the team implements or connects the required context resolver.

---

## AF-2: Active Service Still Imports Legacy Entity

**Branches from:** Any adaptation stage  
**Condition:** An active service imports or injects an entity/repository from the legacy package.

1. **Development team** stops the adaptation for that service.
2. **Development team** replaces the dependency with the target repository/entity.
3. **Development team** moves the old dependency to legacy if it is not reusable.
4. **Development team** reruns compile verification.
5. **Use case continues**.

---

## AF-3: DTO Depends on Legacy Shape

**Branches from:** Stage 3, Stage 4, Stage 8  
**Condition:** A DTO assumes fields that only exist in the legacy model.

1. **Development team** determines whether the DTO represents an active concept or a legacy-only concept.
2. If the DTO represents an active concept, **Development team** adapts the DTO to target ERD terminology.
3. If the DTO represents a legacy-only concept, **Development team** moves it to legacy.
4. **Development team** updates consumers.
5. **Use case continues**.

---

## AF-4: Conversation Has No Current Snapshot

**Branches from:** Stage 3  
**Condition:** A conversation row exists without `current_snapshot_id`.

1. **System** treats the conversation as empty.
2. **System** creates an initial snapshot when the first message is persisted.
3. **System** sets `conversation.current_snapshot_id`.
4. **Use case continues**.

---

## AF-5: Snapshot Messages Cannot Be Parsed

**Branches from:** Stage 3  
**Condition:** `conversation_snapshot.messages` is malformed or incompatible with the expected message DTO.

1. **System** marks the conversation load as failed.
2. **System** does not overwrite the snapshot.
3. **System** displays an error state or fails safely.
4. **Development team** adds validation or migration logic if needed.
5. **Use case ends for that request**.

---

## AF-6: Compaction Fails

**Branches from:** Stage 4  
**Condition:** Snapshot compaction fails because of LLM failure, token estimation failure, serialization failure, or database failure.

1. **System** keeps the previous current snapshot unchanged.
2. **System** does not update `conversation.current_snapshot_id`.
3. **System** logs the failure.
4. **System** continues with the previous snapshot if safe.
5. **Use case continues**.

---

## AF-7: Grounding Embedding Cannot Be Generated

**Branches from:** Stage 6  
**Condition:** The system cannot generate an embedding for a grounding chunk.

1. **System** records the document or chunk as failed where appropriate.
2. **System** does not mark the document as `READY`.
3. **System** logs the error.
4. **System** allows retry if later implemented.
5. **Use case continues**.

---

## AF-8: Grounding Document Has No Group-Class Context

**Branches from:** Stage 6 or Stage 7  
**Condition:** A professor or user attempts to upload grounding content without an active group class.

1. **System** blocks the upload.
2. **System** shows a setup-required state.
3. **System** does not create a grounding collection or document.
4. **Use case ends for that request**.

---

## AF-9: Evaluation Assignment Targets Non-Student Member

**Branches from:** Stage 8  
**Condition:** The system attempts to assign an evaluation to a group-class member whose role is not `STUDENT`.

1. **System** rejects the assignment.
2. **System** does not create an `evaluation_assignment`.
3. **System** reports a validation failure.
4. **Use case continues** after the invalid target is corrected.

---

## AF-10: Student Attempts to Update Another Student's Assignment

**Branches from:** Stage 8 or Stage 9  
**Condition:** A student attempts to view or update an assignment owned by another group-class member.

1. **System** denies the operation.
2. **System** does not reveal assignment details.
3. **System** logs or records the authorization failure if appropriate.
4. **Use case ends for that request**.

---

## AF-11: Legacy Advisor Still Starts

**Branches from:** Stage 11  
**Condition:** A profile, subject-config, document-catalog, or memory advisor still depends on obsolete services and starts in the active context.

1. **System** may fail startup or runtime advisor creation.
2. **Development team** identifies the advisor dependency chain.
3. **Development team** adapts the advisor to target ERD data or isolates it as legacy.
4. **Development team** reruns startup verification.
5. **Use case continues**.

---

## AF-12: Vaadin Bean Missing After Component Scan Change

**Branches from:** Stage 12  
**Condition:** A Vaadin route, layout, route-scoped bean, or Spring component cannot be created.

1. **Development team** identifies whether the missing bean is active or legacy.
2. If active, **Development team** fixes component scanning or bean scope.
3. If legacy, **Development team** removes the route dependency or moves the route to legacy.
4. **Development team** reruns `mvn`.
5. **Use case continues**.

---

## AF-13: Maven Starts but Route Navigation Fails

**Branches from:** Stage 13  
**Condition:** Spring Boot starts, but navigating to an active route fails.

1. **Development team** captures the route-level stack trace.
2. **Development team** identifies the failing route and dependency.
3. **Development team** adapts the dependency if it belongs to active functionality.
4. **Development team** isolates it if it belongs to legacy functionality.
5. **Development team** reruns the app.
6. **Use case continues**.

---

## AF-14: Adaptation Requires Product Decision

**Branches from:** Any stage  
**Condition:** A service cannot be adapted because the target product behavior is not yet defined.

Examples:

```text
No current group-class context exists.
No rule exists for professor access to student conversations.
No rule exists for document retrieval across multiple grounding collections.
No rule exists for evaluation answer storage.
No rule exists for learner-profile replacement.
```

1. **Development team** stops the specific adaptation.
2. **Development team** documents the blocker.
3. **Development team** avoids reintroducing legacy persistence.
4. **Use case ends as blocked for that part only**.

---

# Postconditions

---

## On Success

- The application starts successfully with `mvn`.
- Active chat services use `conversation` and `conversation_snapshot`.
- Active chat UI no longer depends on old chat persistence.
- Active conversation listing and loading read from the target conversation model.
- Active conversation message persistence stores messages in `conversation_snapshot.messages`.
- Active compaction uses snapshot chaining.
- Active document ingestion uses `grounding_collection`, `grounding_document`, and `grounding_chunk`.
- Active document retrieval is scoped to group-class grounding data.
- Active evaluation behavior uses `evaluation` and `evaluation_assignment`.
- Student assignment progress is represented by updating `evaluation_assignment.status`.
- `evaluation_run` is not used by active runtime.
- `client_id` is not used as the persisted domain identity.
- Student-profile logic is isolated as legacy.
- Active Spring component scanning does not hide active Vaadin or service beans.
- Legacy entities and repositories do not participate in active JPA startup.
- The runtime aligns with the schema direction established by UC-001.

---

## On Failure

- The application does not start or one or more active routes still fail.
- The failing domain, service, route, advisor, or repository is identified.
- The team knows whether the blocker is:
  - a missing adaptation,
  - a wrong component scan exclusion,
  - an unresolved identity/context decision,
  - an unresolved product rule,
  - or a legacy dependency that still needs isolation.
- No obsolete tables are reintroduced just to make the app start.
- No compatibility adapter is added that makes legacy persistence active again.

---

# Business Rules

| ID | Rule |
|----|------|
| BR-01 | UC-002 must preserve UC-001 as the source of truth for the active schema. |
| BR-02 | Active runtime code must use target ERD entities and repositories. |
| BR-03 | Active runtime code must not inject legacy repositories. |
| BR-04 | Active runtime code must not require obsolete legacy tables. |
| BR-05 | `client_id` must not be used as persisted academic identity. |
| BR-06 | Persisted academic activity must resolve through `account`, `tenant_account`, and `group_class_member`. |
| BR-07 | A `conversation` must belong to a `group_class_member`. |
| BR-08 | A `conversation_snapshot` must belong to a `conversation`. |
| BR-09 | Conversation messages must be stored in `conversation_snapshot.messages`. |
| BR-10 | Conversation carry context must be stored in `conversation_snapshot.carry_context`. |
| BR-11 | The current conversation state must be reachable through `conversation.current_snapshot_id`. |
| BR-12 | Snapshot compaction must create or select a new current snapshot instead of mutating old transcript rows. |
| BR-13 | Snapshot chaining must use `conversation_snapshot.previous_snapshot_id`. |
| BR-14 | Active chat UI may remain, but its backing persistence must be the target conversation model. |
| BR-15 | The system must not reintroduce `chat`, `chat_transcript`, or `chat_message` as active persistence. |
| BR-16 | Grounding content must be scoped to a `group_class`. |
| BR-17 | A `grounding_collection` must belong to a group class. |
| BR-18 | A `grounding_document` must belong to a grounding collection. |
| BR-19 | A `grounding_chunk` must belong to a grounding document. |
| BR-20 | Active document retrieval must use target grounding data, not obsolete document ingestion tables. |
| BR-21 | Active document ingestion must create target grounding entities. |
| BR-22 | Evaluation must be scoped to a group class. |
| BR-23 | Evaluation must be created by a valid group-class member. |
| BR-24 | Evaluation assignment must target a valid group-class member. |
| BR-25 | Student evaluation progress must be represented through `evaluation_assignment.status`. |
| BR-26 | `evaluation_run` must not be used by active runtime. |
| BR-27 | Students must not update another student's evaluation assignment. |
| BR-28 | Students must not view another student's private conversation data unless a later use case explicitly allows it. |
| BR-29 | Professors may only operate within group classes where they have valid membership and permissions. |
| BR-30 | The old student-profile block must be isolated as legacy. |
| BR-31 | Student profile must not determine identity, membership, authorization, conversation ownership, grounding scope, or evaluation assignment ownership. |
| BR-32 | Guardrail logic should remain active when it does not depend on legacy persistence. |
| BR-33 | AI advisors that depend on obsolete persistence must be adapted or isolated. |
| BR-34 | Component scanning must not exclude active Vaadin UI packages. |
| BR-35 | Component scanning may exclude legacy-only packages. |
| BR-36 | Target JPA entity scanning must not include legacy JPA entities. |
| BR-37 | Target repository scanning must not include legacy repositories. |
| BR-38 | Browser identity infrastructure may exist, but it must not become the academic persistence identity. |
| BR-39 | If no active group-class context exists, the system must fail safely with an empty/setup-required state. |
| BR-40 | No compatibility adapter may make old persistence active again. |
| BR-41 | Reusable DTOs that describe target concepts may remain active even if their previous implementation was legacy-backed. |
| BR-42 | DTOs that describe legacy-only concepts must be moved, renamed, or adapted. |
| BR-43 | The application must be verified with Maven after adaptation. |

---

# Tests

- [ ] Stage 1 identifies active, adapted, legacy, and unchanged code areas.
- [ ] Stage 1 confirms active code does not depend on legacy packages.
- [ ] Stage 2 removes persisted `client_id` assumptions from active services.
- [ ] Stage 2 verifies browser cookies are not used as persisted domain identity.
- [ ] Stage 2 verifies persisted academic operations require a valid group-class member context.
- [ ] Stage 3 adapts conversation creation to use `conversation`.
- [ ] Stage 3 adapts conversation loading to use `conversation_snapshot`.
- [ ] Stage 3 adapts conversation summaries to load from the target model.
- [ ] Stage 3 verifies no active chat service injects old chat repositories.
- [ ] Stage 3 verifies chat UI can load without old chat persistence.
- [ ] Stage 4 adapts token usage to `conversation_snapshot.token_count`.
- [ ] Stage 4 adapts message count to `conversation_snapshot.message_count`.
- [ ] Stage 4 adapts compaction to create a new snapshot.
- [ ] Stage 4 verifies `previous_snapshot_id` is set when compaction creates a new snapshot.
- [ ] Stage 4 verifies `conversation.current_snapshot_id` points to the current snapshot.
- [ ] Stage 5 verifies `ChatState` or equivalent active UI state is available as a Vaadin/Spring bean.
- [ ] Stage 5 verifies `MainLayout` or equivalent active layout can be created.
- [ ] Stage 5 verifies chat route navigation does not fail from missing beans.
- [ ] Stage 6 adapts document ingestion to create `grounding_document`.
- [ ] Stage 6 adapts chunk persistence to create `grounding_chunk`.
- [ ] Stage 6 verifies grounding documents are scoped to group class.
- [ ] Stage 6 verifies active retrieval does not use old document ingestion tables.
- [ ] Stage 7 verifies document ingestion UI uses grounding statuses.
- [ ] Stage 7 verifies document ingestion UI blocks uploads with no group-class context.
- [ ] Stage 8 adapts evaluation creation to target `evaluation`.
- [ ] Stage 8 adapts student evaluation execution to `evaluation_assignment`.
- [ ] Stage 8 verifies valid assignment lifecycle transitions.
- [ ] Stage 8 verifies a student cannot update another student's assignment.
- [ ] Stage 8 verifies active runtime does not use `evaluation_run`.
- [ ] Stage 9 verifies evaluation UI loads target evaluations.
- [ ] Stage 9 verifies student evaluation UI works with assignments.
- [ ] Stage 10 isolates student-profile code as legacy.
- [ ] Stage 10 verifies no active service injects `StudentProfileService`.
- [ ] Stage 10 verifies profile-aware advisors do not start if still legacy-dependent.
- [ ] Stage 11 keeps guardrail services active.
- [ ] Stage 11 adapts or isolates legacy-dependent advisors.
- [ ] Stage 12 fixes component scanning so active UI packages are not excluded.
- [ ] Stage 12 verifies legacy packages are excluded from active startup.
- [ ] Stage 12 verifies target entities and repositories are the active JPA model.
- [ ] Stage 13 runs compile verification.
- [ ] Stage 13 runs application startup verification with `CHAT_MODEL=tutor-socratico-8b:latest mvn`.
- [ ] AF-1 through AF-14 are covered.
- [ ] BR-01 through BR-43 are covered.

---

# UI Surface

This use case adapts existing UI flows instead of introducing new user-facing screens.

| Surface | Access | Entry Point | Expected Behavior |
|--------|--------|-------------|-------------------|
| Chat UI | Student or active group-class member | `/` | Existing chat interface remains available where possible, but conversations are backed by `conversation` and `conversation_snapshot`. |
| Login UI | Anonymous / unauthenticated user | `/login` | Existing login route remains active. It must not depend on legacy chat state. |
| Document/Grounding UI | Professor or authorized group-class member | Existing document route | Existing document ingestion UI is adapted to grounding collections, documents, and chunks. |
| Evaluation UI | Professor or student depending on view | Existing evaluation routes | Existing evaluation UI is adapted to group-class evaluations and student assignments. |
| Startup/runtime verification | Development team | `mvn` | Application starts without active legacy persistence dependencies. |

---

# Technical Notes

## Active vs Legacy Is Not the Same as Feature vs No Feature

The chat feature remains active.

The old chat persistence model becomes legacy.

The document grounding feature remains active.

The old document ingestion persistence model becomes legacy.

The evaluation feature remains active.

The old evaluation-run persistence model becomes legacy.

The student-profile feature becomes legacy because the current ERD does not yet include a redesigned learner-profile model.

---

## Preferred Package Policy

Active code should live under normal active packages such as:

```text
com.wornux.config
com.wornux.data.entities
com.wornux.data.repositories
com.wornux.services
com.wornux.ai
com.wornux.ui
com.wornux.infrastructure
```

Legacy-only code should live under:

```text
com.wornux.legacy
```

Active code must not depend on legacy code.

Legacy code may depend on old concepts but must not be scanned as active runtime.

---

## Component Scan Policy

The application must not exclude broad active packages such as:

```text
com.wornux.ui.chat
com.wornux.ui.evaluation
com.wornux.ui.ingestion
com.wornux.services.chat
com.wornux.services.document
com.wornux.services.evaluation
```

if those packages contain adapted active code.

The preferred component-scan exclusion is:

```text
com.wornux.legacy.*
```

or a similarly precise legacy-only boundary.

---

## Migration Mapping

| Old Runtime Concept | Target Runtime Concept |
|--------------------|------------------------|
| `client_id` | `account` / `tenant_account` / `group_class_member` |
| `chat` | `conversation` |
| `chat_transcript` | `conversation_snapshot` |
| `chat_message` | `conversation_snapshot.messages` |
| `chat_transcript.memory` | `conversation_snapshot.carry_context` |
| `chat_transcript.input_tokens` | `conversation_snapshot.token_count` |
| `chat.current_transcript_id` | `conversation.current_snapshot_id` |
| `ingested_document` | `grounding_document` |
| `document_segment` | `grounding_chunk` |
| `vector_store` | `grounding_chunk.embedding` or target grounding retrieval implementation |
| old global `evaluation` | group-class scoped `evaluation` |
| `evaluation_run` | `evaluation_assignment` |
| `student_profile` | legacy-only until redesigned |

---

## Verification Command

Final verification must include:

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

The final report must include:

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

---

# Suggested File Path

```text
spec/use-cases/use-case-002-adapt-logic-to-target-erd.md
```
