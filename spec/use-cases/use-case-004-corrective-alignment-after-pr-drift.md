# UC-004: Corrective Alignment After UC-001/UC-002/UC-003 Implementation Drift

---

**Goal:** As the development team, I want to correct the implementation drift introduced after the first three use cases so that the running code, configuration, entity mappings, prompts, and documentation return to the academic multi-tenant foundation defined by UC-001, activated by UC-002, and surfaced through UC-003.

**Status:** Pending  
**Date:** 2026-06-22

---

## Scope

This use case covers corrective work after the implementation of the first three use cases.

It exists because some changes introduced during implementation drifted away from the intended architecture and schema.

This use case includes:

- Restoring and correctly adapting AI configuration instead of deleting it.
- Removing unnecessary browser identity configuration and duplicate browser-cookie identity assumptions.
- Preserving Lombok usage where it is the project convention.
- Reviewing the current branch before modifying security/config-heavy files.
- Aligning `SecurityConfig` with the latest existing implementation instead of reintroducing obsolete helper logic.
- Correcting the `Conversation` / `ConversationSnapshot` relationship mapping.
- Correcting BIGINT identity Java types such as `conversation_snapshot.id` and the other internal bigint identifiers.
- Aligning product-facing naming for evaluations as formative activities where appropriate.
- Removing incorrect prompt vocabulary such as `legacySubject` from active prompts.
- Restoring PostgreSQL/pgvector configuration required by grounding retrieval unless an explicit tested replacement exists.
- Removing duplicate or obsolete cookie configuration from `application.yml`.
- Verifying that active runtime remains on the target ERD.
- Running Maven verification after the corrections.

This use case does not include:

- Redesigning the ERD.
- Reintroducing old `client_id` persistence.
- Reintroducing old chat tables as active runtime.
- Reintroducing old document ingestion tables as active runtime.
- Reintroducing `evaluation_run`.
- Creating new onboarding features.
- Rebuilding the full UI.
- Rewriting all prompts from scratch.
- Changing the target multi-tenant hierarchy.
- Adding Mailhog/Mailpit work, because local email setup was already handled before this correction use case.

---

## Relationship to Previous Use Cases

UC-001 defines the target academic multi-tenant ERD.

UC-002 adapts runtime services to the target ERD.

UC-003 adds role-based onboarding and workspace setup.

UC-004 corrects implementation drift after those use cases.

The intended sequence is:

```text
UC-001: Create target schema.
UC-002: Adapt active runtime to target schema.
UC-003: Add onboarding/workspaces.
UC-004: Correct drift and restore alignment.
```

UC-004 must not replace UC-001, UC-002, or UC-003. It must bring the implementation back into alignment with them.

---

## Current Problems Being Corrected

The following problems were identified during PR review.

### Problem 1: AI configuration was removed

AI configuration was removed entirely instead of being adapted to the new model.

Expected correction:

```text
Restore AI configuration and adapt it to target conversation, grounding, guardrail, and prompt services.
```

### Problem 2: New browser identity properties were introduced unnecessarily

A new `BrowserIdentityProperties` / `app.browser` cookie configuration was introduced even though browser cookies are not the target academic identity.

Expected correction:

```text
Remove unnecessary browser identity configuration and avoid replacing one client cookie with another.
```

### Problem 3: Lombok was replaced with manual getters/setters

Configuration classes were changed to manual boilerplate even though Lombok is an accepted project convention.

Expected correction:

```text
Use Lombok where the project already uses Lombok and avoid unnecessary manual boilerplate.
```

### Problem 4: SecurityConfig was modified without syncing current branch context

The PR appeared to miss recent security configuration changes.

Expected correction:

```text
Fetch/review the current branch before changing SecurityConfig and keep the latest intended security behavior.
```

### Problem 5: Conversation relationship was mapped incorrectly

`Conversation.currentSnapshot` was modeled as a `@ManyToOne`, but the desired relationship is a current snapshot pointer plus a separate snapshot collection.

Expected correction:

```text
Conversation 1 -> * ConversationSnapshot
Conversation 1 -> 0..1 currentSnapshot
ConversationSnapshot 0..1 -> previous ConversationSnapshot
```

### Problem 6: Evaluation naming is product-confusing

The code/UI surfaced `evaluation` too literally even though the product-facing concept should be formative activities.

Expected correction:

```text
Use "Formative Activities" for UI/navigation/copy where appropriate while preserving the physical schema unless a later migration renames it.
```

### Problem 7: `documentId` was typed incorrectly

A DTO or search result used `UUID documentId` even though the referenced grounding row identifier was not a UUID.

Expected correction:

```text
Use Long for grounding document IDs and chunk IDs.
```

### Problem 8: Specs and context drifted from UC-001

Several always-read specs still described professor-owned tenants, `chat`, `document`, and `evaluation_run`.

Expected correction:

```text
Ensure context docs and implementation follow UC-001 as the new truth.
```

### Problem 9: Prompts were changed to use `legacySubject`

Prompt text was changed to use `legacySubject`, which is obsolete or migration vocabulary and not appropriate for active tutor prompts.

Expected correction:

```text
Use subject/course/class context vocabulary, not legacySubject.
```

### Problem 10: PostgreSQL/pgvector configuration was removed

Vector store or pgvector configuration was removed from `application.yml` without a confirmed replacement.

Expected correction:

```text
Restore required Spring AI PgVectorStore configuration for active grounding retrieval, while keeping target persistence on `grounding_vector_store.embedding`.
```

### Problem 11: Duplicate cookie configuration remained in application.yml

Both old chat client cookie configuration and new browser identity configuration appeared together.

Expected correction:

```text
Remove academic identity cookie configuration. Browser/session details must not be domain identity.
```

---

## Actors

- **Primary actor:** Development team
- **Secondary actors:** Spring Boot runtime, Vaadin runtime, PostgreSQL/Flyway, AI tutor services

---

## Preconditions

- UC-001 has defined the target academic multi-tenant ERD.
- UC-002 has defined the runtime adaptation direction.
- UC-003 has defined onboarding and workspace setup direction.
- The current branch contains implementation changes from those use cases.
- The PR review notes from `Ajustes al pr.pdf` are available.
- The development team accepts that UC-001 remains the source of truth for schema and domain boundaries.
- The development team accepts that this use case is corrective, not a redesign.

---

## Trigger

The development team begins a corrective pass after PR review identifies implementation drift from the approved specs and use cases.

In practical terms:

```text
Before implementing the next feature use case, fix the drift created after UC-001, UC-002, and UC-003.
```

---

# Main Flow

---

## Stage 1: Sync and Inspect Current Branch

### Purpose

Avoid overwriting or reverting valid existing changes.

### Flow

1. **Development team** fetches the latest branch state.
2. **Development team** compares the PR changes against the current branch.
3. **Development team** identifies valid existing code that should be preserved.
4. **Development team** identifies drift that should be corrected.
5. **Development team** lists impacted files before editing.

### Result

```text
Corrections are based on the current codebase, not stale assumptions.
```

---

## Stage 2: Restore and Adapt AI Configuration

### Purpose

Restore AI orchestration instead of removing it.

### Flow

1. **Development team** identifies the previous working AI configuration.
2. **Development team** restores the AI configuration class or equivalent wiring.
3. **Development team** keeps active guardrails.
4. **Development team** keeps pedagogical routing where compatible.
5. **Development team** keeps document/grounding retrieval advisors only if they use target grounding data.
6. **Development team** removes only dependencies that still require obsolete persistence.
7. **Development team** adapts memory/conversation context to `conversation` and `conversation_snapshot`.
8. **Development team** verifies the chat client can start with the configured model.

### Result

```text
AI tutor configuration is active and aligned with target ERD services.
```

---

## Stage 3: Remove Browser Identity Drift

### Purpose

Stop replacing old `client_id` identity with another browser-cookie identity.

### Flow

1. **Development team** removes unnecessary `BrowserIdentityProperties` if it only exists to create another browser identity cookie.
2. **Development team** removes `app.browser.id-cookie-name` or equivalent academic identity cookie configuration.
3. **Development team** keeps only technical browser/session behavior that Spring Security or Vaadin requires.
4. **Development team** verifies active academic context resolves from:
   - `account`
   - `tenant_account`
   - `group_class_member`
5. **Development team** verifies persisted academic activity does not use browser cookie identity.

### Result

```text
Academic identity is account/tenant/group-class based, not browser-cookie based.
```

---

## Stage 4: Restore Lombok Conventions

### Purpose

Avoid unnecessary boilerplate and inconsistent code style.

### Flow

1. **Development team** identifies classes where Lombok annotations were removed without reason.
2. **Development team** restores Lombok annotations such as:
   - `@Getter`
   - `@Setter`
   - `@NoArgsConstructor`
   - `@AllArgsConstructor`
   - `@Builder`
   - `@ConfigurationProperties`
3. **Development team** removes redundant manual getters and setters.
4. **Development team** preserves manual methods only when they contain real logic.
5. **Development team** verifies compilation.

### Result

```text
Project code style returns to the established Lombok convention.
```

---

## Stage 5: Reconcile SecurityConfig

### Purpose

Preserve the correct current security configuration while keeping login-first, protected workspace behavior.

### Flow

1. **Development team** reviews current `SecurityConfig`.
2. **Development team** identifies changes already made on the branch.
3. **Development team** removes obsolete helper methods or local-development bypass logic if the current security design no longer uses them.
4. **Development team** keeps public static asset routes.
5. **Development team** keeps `/login` public.
6. **Development team** keeps invitation/onboarding routes public only as needed.
7. **Development team** ensures workspaces require authentication.
8. **Development team** verifies Vaadin security integration still points to the login view.
9. **Development team** verifies security configuration does not depend on browser identity cookies.

### Result

```text
SecurityConfig reflects current branch behavior and target authentication/authorization rules.
```

---

## Stage 6: Correct Conversation Entity Relationships

### Purpose

Fix JPA relationship cardinality and ID types for conversation snapshots.

### Flow

1. **Development team** opens `Conversation`.
2. **Development team** changes `currentSnapshot` to a one-to-one pointer:

```java
@OneToOne
@JoinColumn(name = "current_snapshot_id")
private ConversationSnapshot currentSnapshot;
```

3. **Development team** adds or preserves the snapshot collection:

```java
@OneToMany(mappedBy = "conversation")
private List<ConversationSnapshot> snapshots = new ArrayList<>();
```

4. **Development team** opens `ConversationSnapshot`.
5. **Development team** ensures it belongs to `Conversation`:

```java
@ManyToOne(optional = false)
@JoinColumn(name = "conversation_id", nullable = false)
private Conversation conversation;
```

6. **Development team** ensures `previousSnapshot` is self-referential and nullable.
7. **Development team** ensures `ConversationSnapshot.id` is `Long`, not UUID.
8. **Development team** verifies Hibernate validates against the target schema.

### Result

```text
Conversation and snapshot mappings match the ERD and snapshot model.
```

---

## Stage 7: Correct Grounding Document ID Usage

### Purpose

Fix DTO/entity ID type drift for grounding documents and chunks.

### Flow

1. **Development team** identifies DTOs using `UUID documentId` for grounding documents.
2. **Development team** changes grounding document IDs to `Long`.
3. **Development team** changes grounding chunk IDs to `Long` where applicable.
4. **Development team** renames ambiguous DTO fields if needed:
   - `documentId` -> `groundingDocumentId`
   - `chunkId` -> `groundingChunkId`
5. **Development team** updates mapper code.
6. **Development team** updates tests.
7. **Development team** verifies queries still compile.

### Result

```text
Java ID types match BIGINT identity schema for grounding records.
```

---

## Stage 8: Align Evaluation Naming to Formative Activities

### Purpose

Make the user-facing product language clearer without changing the schema unexpectedly.

### Flow

1. **Development team** identifies UI labels, menu items, page titles, and copy that expose `Evaluation` awkwardly.
2. **Development team** changes user-facing copy to:
   - `Formative Activities`
   - `Activity`
   - `Assigned Activity`
3. **Development team** confirms physical table names have been renamed from `evaluation`/`evaluation_assignment` to `training_activity`/`training_activity_assignment` by UC-001.
4. **Development team** preserves backend package names unless changing them is low-risk and covered by tests.
5. **Development team** updates tests that assert labels or routes.

### Result

```text
The product reads as formative activities to users while the current ERD remains stable.
```

---

## Stage 9: Correct Prompt Vocabulary

### Purpose

Remove obsolete `legacySubject` language from active prompts.

### Flow

1. **Development team** searches active prompt files and prompt resources for `legacySubject`.
2. **Development team** replaces it with appropriate active vocabulary:
   - subject
   - course
   - group class
   - academic context
3. **Development team** ensures prompts do not mention internal table names unless needed for debugging.
4. **Development team** verifies prompt services use active subject/group-class context.
5. **Development team** verifies prompts do not depend on obsolete `subject_config_revision`.

### Result

```text
Tutor prompts use active academic vocabulary and no longer reference legacySubject as user-facing or model-facing context.
```

---

## Stage 10: Restore PostgreSQL / pgvector Configuration

### Purpose

Avoid breaking grounding retrieval by removing vector configuration without replacement.

### Flow

1. **Development team** reviews `application.yml`.
2. **Development team** restores required PostgreSQL/pgvector configuration if active grounding retrieval depends on it.
3. **Development team** ensures configuration does not reintroduce obsolete `vector_store` table as active target persistence.
4. **Development team** aligns vector settings with `grounding_vector_store.embedding` or the currently approved scoped retrieval implementation.
5. **Development team** verifies dimensions and index type match the embedding model and schema.
6. **Development team** removes obsolete duplicated cookie config from the same file.

### Result

```text
Grounding retrieval configuration is present and aligned with target grounding persistence.
```

---

## Stage 11: Verify Legacy Isolation

### Purpose

Ensure corrections did not reactivate obsolete persistence.

### Flow

1. **Development team** verifies active JPA scanning does not include old chat entities.
2. **Development team** verifies active JPA scanning does not include old document ingestion entities.
3. **Development team** verifies active JPA scanning does not include `evaluation_run`.
4. **Development team** verifies active services do not inject legacy repositories.
5. **Development team** verifies active AI advisors do not require legacy services.
6. **Development team** verifies active academic identity does not use `client_id`.

### Result

```text
Legacy code remains isolated and inactive.
```

---

## Stage 12: Run Verification

### Purpose

Confirm the corrected application compiles and starts.

### Flow

1. **Development team** runs compile verification.
2. **Development team** runs tests.
3. **Development team** runs Maven startup verification:

```bash
CHAT_MODEL=tutor-socratico-8b:latest mvn
```

4. **System** applies Flyway migrations.
5. **System** validates Hibernate mappings.
6. **System** initializes Spring Security.
7. **System** initializes AI configuration.
8. **System** initializes active Vaadin routes.
9. **System** starts without legacy persistence dependencies.
10. **Development team** records final result.

### Result

```text
The application is realigned and verified after corrective changes.
```

---

# Alternative Flows

---

## AF-1: Current Branch Already Contains a Correct Fix

**Branches from:** Any stage  
**Condition:** The current branch already corrected an issue.

1. **Development team** keeps the existing correct implementation.
2. **Development team** documents that no code change was needed for that item.
3. **Development team** continues with the next stage.

---

## AF-2: AI Configuration Cannot Be Fully Restored

**Branches from:** Stage 2  
**Condition:** AI configuration depends on services that are not yet adapted to the target ERD.

1. **Development team** restores the configuration structure.
2. **Development team** disables or isolates only the incompatible advisor/tool.
3. **Development team** keeps guardrails active where possible.
4. **Development team** records the remaining blocker.
5. **Use case continues**.

---

## AF-3: SecurityConfig Behavior Is Ambiguous

**Branches from:** Stage 5  
**Condition:** There are competing security changes and the intended current behavior is unclear.

1. **Development team** stops editing `SecurityConfig`.
2. **Development team** documents the ambiguity.
3. **Development team** asks for a decision.
4. **Use case pauses for this stage**.

---

## AF-4: Entity Relationship Fix Requires Migration Adjustment

**Branches from:** Stage 6  
**Condition:** JPA mapping and Flyway schema disagree.

1. **Development team** compares entity mapping with the Mermaid ERD.
2. **Development team** updates Flyway SQL or entity mapping to match the approved ERD.
3. **Development team** reruns Hibernate validation.
4. **Use case continues**.

---

## AF-5: Renaming Evaluation to Formative Activity Is Too Broad

**Branches from:** Stage 8  
**Condition:** Full package/class/table rename is too risky for this corrective pass.

1. **Development team** updates only user-facing labels and navigation.
2. **Development team** keeps backend names stable.
3. **Development team** records a follow-up use case for deeper rename if wanted.
4. **Use case continues**.

---

## AF-6: Vector Configuration Conflicts With Target Grounding Schema

**Branches from:** Stage 10  
**Condition:** Existing vector-store configuration expects the old `vector_store` table.

1. **Development team** does not blindly restore obsolete table usage.
2. **Development team** adapts configuration toward `grounding_vector_store.embedding` or the approved scoped vector retrieval path.
3. **Development team** records any missing implementation as a blocker.
4. **Use case continues**.

---

## AF-7: Maven Starts But Route Navigation Fails

**Branches from:** Stage 12  
**Condition:** Spring Boot starts but Vaadin route navigation fails.

1. **Development team** captures the route-level stack trace.
2. **Development team** identifies whether the failing dependency is active or legacy.
3. If active, **Development team** adapts the dependency.
4. If legacy, **Development team** removes or isolates the dependency.
5. **Development team** reruns verification.

---

# Postconditions

---

## On Success

- AI configuration exists and starts.
- AI advisors/tools are adapted or safely isolated.
- Browser identity cookie configuration is removed or no longer used for academic persistence.
- Lombok conventions are restored where appropriate.
- SecurityConfig matches current branch expectations and protects workspaces.
- Conversation and snapshot mappings match the ERD.
- `ConversationSnapshot.id` is `Long`.
- `GroundingDocument.id` and related DTO IDs are `Long`.
- Evaluation UI/copy uses formative activity language where appropriate.
- Active prompts no longer use `legacySubject`.
- PostgreSQL/pgvector configuration required for grounding retrieval is present and aligned.
- Duplicate/obsolete cookie configuration is removed.
- Legacy persistence remains inactive.
- Maven verification result is recorded.

---

## On Failure

- The specific unresolved drift area is documented.
- No obsolete persistence is reactivated as a workaround.
- No new browser-cookie identity becomes the target academic identity.
- The team knows whether the blocker is:
  - AI configuration dependency,
  - security ambiguity,
  - JPA/Flyway mismatch,
  - vector retrieval mismatch,
  - naming-scope risk,
  - or legacy dependency still leaking into active startup.

---

# Business Rules

| ID | Rule |
|----|------|
| BR-01 | UC-001 remains the source of truth for schema and academic hierarchy. |
| BR-02 | UC-002 remains the source of truth for active runtime adaptation. |
| BR-03 | UC-003 remains the source of truth for onboarding and workspace direction. |
| BR-04 | Corrective work must not redesign the target ERD. |
| BR-05 | `client_id` must not be used as persisted academic identity. |
| BR-06 | Browser cookies must not replace account/tenant/group-class identity. |
| BR-07 | AI configuration must be adapted, not deleted without replacement. |
| BR-08 | AI guardrails should remain active when compatible with the target runtime. |
| BR-09 | AI retrieval must use target grounding data or be isolated until adapted. |
| BR-10 | Lombok should be preserved where it is the project convention. |
| BR-11 | SecurityConfig changes must be based on the current branch. |
| BR-12 | Login and onboarding routes may be public; workspaces must be protected. |
| BR-13 | Service-layer authorization remains mandatory. |
| BR-14 | `Conversation.currentSnapshot` is a current snapshot pointer, not the snapshot collection. |
| BR-15 | `Conversation.snapshots` is the one-to-many collection of snapshots. |
| BR-16 | `ConversationSnapshot.id` must match BIGINT identity and use Java `Long`. |
| BR-17 | `GroundingDocument.id` must match BIGINT identity and use Java `Long`. |
| BR-18 | `GroundingChunk.id` must match BIGINT identity and use Java `Long`. |
| BR-19 | User-facing evaluation copy should use formative activity language where appropriate. |
| BR-20 | Physical table names must not be renamed in this corrective pass unless covered by explicit migration and tests. |
| BR-21 | Active prompts must not use `legacySubject` as current vocabulary. |
| BR-22 | PostgreSQL/pgvector configuration must not be removed while active grounding retrieval depends on it. |
| BR-23 | Restoring vector configuration must not reintroduce old `vector_store` as active target persistence. |
| BR-24 | Legacy repositories must not be active runtime dependencies. |
| BR-25 | Active code must not depend on `student_profile` for identity, authorization, membership, conversation ownership, grounding scope, or assignment ownership. |
| BR-26 | Corrections must be verified with Maven. |

---

# Tests

- [ ] Stage 1 verifies current branch is fetched/reviewed before changes.
- [ ] Stage 2 verifies AI configuration bean exists.
- [ ] Stage 2 verifies guardrails still start.
- [ ] Stage 2 verifies target grounding services are used or incompatible retrieval advisors are isolated.
- [ ] Stage 3 verifies no new browser identity cookie is used for academic persistence.
- [ ] Stage 3 verifies `account -> tenant_account -> group_class_member` resolves persisted academic context.
- [ ] Stage 4 verifies Lombok annotations are restored where appropriate.
- [ ] Stage 4 verifies removed boilerplate had no custom logic.
- [ ] Stage 5 verifies `/login` is public.
- [ ] Stage 5 verifies invitation/onboarding routes needed for acceptance are public or onboarding-guarded.
- [ ] Stage 5 verifies workspaces require authentication.
- [ ] Stage 5 verifies SecurityConfig does not depend on browser identity properties.
- [ ] Stage 6 verifies `Conversation.currentSnapshot` uses one-to-one mapping.
- [ ] Stage 6 verifies `Conversation.snapshots` uses one-to-many mapping.
- [ ] Stage 6 verifies `ConversationSnapshot.conversation` uses many-to-one mapping.
- [ ] Stage 6 verifies `ConversationSnapshot.previousSnapshot` maps nullable self-reference.
- [ ] Stage 6 verifies `ConversationSnapshot.id` is `Long`.
- [ ] Stage 7 verifies grounding document DTO IDs are `Long`.
- [ ] Stage 7 verifies grounding chunk DTO IDs are `Long`.
- [ ] Stage 8 verifies user-facing navigation/copy says formative activities where appropriate.
- [ ] Stage 8 verifies physical schema is unchanged unless an explicit migration is included.
- [ ] Stage 9 verifies no active prompt uses `legacySubject`.
- [ ] Stage 9 verifies prompts use subject/group-class/academic context vocabulary.
- [ ] Stage 10 verifies required pgvector/PostgreSQL grounding config exists.
- [ ] Stage 10 verifies old `vector_store` table is not required as active target persistence.
- [ ] Stage 10 verifies duplicate old/new cookie config is removed.
- [ ] Stage 11 verifies legacy JPA repositories are inactive.
- [ ] Stage 11 verifies active services do not inject legacy repositories.
- [ ] Stage 12 runs compile verification.
- [ ] Stage 12 runs test verification.
- [ ] Stage 12 runs `CHAT_MODEL=tutor-socratico-8b:latest mvn`.
- [ ] AF-1 through AF-7 are covered.
- [ ] BR-01 through BR-26 are covered.

---

# UI Surface

| Surface | Access | Entry Point | Expected Behavior |
|---|---|---|---|
| Login | Anonymous | `/login` | Renders correctly and authenticates without browser identity drift. |
| Workspaces | Authenticated users | Role-based routes | Protected by Spring Security and service-layer scope checks. |
| Chat UI | Student/professor where allowed | Chat route | Uses conversation/snapshot model and active AI config. |
| Grounding UI | Professor where allowed | Grounding route | Uses target grounding IDs and group-class scope. |
| Formative Activities UI | Professor/student where allowed | Activities route | Uses formative activity labels while preserving target schema. |

---

# Technical Notes

## Correct Conversation Mapping

Expected relationship:

```text
GroupClassMember 1 ── * Conversation
Conversation 1 ── * ConversationSnapshot
Conversation 1 ── 0..1 currentSnapshot
ConversationSnapshot 0..1 ── previous ConversationSnapshot
```

## Correct ID Types

```text
Conversation.id = UUID
ConversationSnapshot.id = Long

GroundingCollection.id = Long
GroundingDocument.id = Long
GroundingChunk.id = Long

Evaluation.id = UUID
EvaluationAssignment.id = UUID
```

## Correct Identity Source

```text
account
  -> tenant_account
      -> group_class_member
```

Do not replace old `client_id` with a new browser identity cookie.

## Final Report Format

The implementation report must include:

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
