# UC-009: Finalize and Report a Training Evaluation

---

**Goal:** As a professor, I want a reliable evidence-based report after a student submits an evaluation so that I can review strengths, weaknesses, observations, and the exact question-answer history without delaying the student's completion.

**Status:** Pending  
**Date:** 2026-07-10

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Professor or another authorized activity reviewer
- **Secondary actors:** Student, durable report worker, configured report model

---

## Preconditions

- UC-007 produced a validated terminal tutor decision.
- Assignment and all accepted turns are persisted.
- Assignment is `SUBMITTED` with completion/evidence metadata.
- Exactly one `training_activity_report` exists as `PENDING` and one semantic final-report job is available.
- Professor has permission to review the activity and target assignment.
- SPEC-005 report, job, priority, idempotency, and structured persistence rules exist.

---

## Trigger

UC-007 commits assignment submission and creates the pending report, or an authorized professor later opens the submitted student's report detail.

---

## Main Flow

1. UC-007 commits assignment `SUBMITTED`, report `PENDING`, and one `FINAL_REPORT` job in a short transaction.
2. System immediately returns the student to `/student` and shows the assignment as completed.
3. Report worker claims the pending job when higher-priority tutor work and configured model capacity permit.
4. Worker changes report to `GENERATING` in a short transaction and releases the transaction.
5. Worker builds immutable authorized report context from activity title/instructions, ordered persisted turns, validated evidence/completion metadata, and allowed academic context.
6. Worker calls the report model outside any domain transaction.
7. Model returns a structured report candidate containing summary, strengths, weaknesses, observations, recommendations, and evidence status.
8. Backend validates the candidate for required fields, allowed sizes, evidence compatibility, and absence of unsupported claims.
9. In a new short optimistic transaction, system verifies report/job input version and stores the structured report as `READY`.
10. Professor opens the published/closed activity detail and selects the submitted student.
11. System validates professor permission and class scope.
12. System displays the report state and, when `READY`, renders summary, strengths, weaknesses, observations, recommendations, and evidence limitation when applicable.
13. System loads the ordered questions and answers directly from `training_activity_turn` and displays them below the report.
14. Professor can distinguish a report based on sufficient evidence from one limited by insufficient student evidence.

---

## Alternative Flows

### AF-1: Report is still pending or generating

**Branches from:** Main Flow step 10 or 12  
**Condition:** Professor opens the detail before background generation completes.

1. System shows a nonblocking `PENDING` or `GENERATING` state and the already persisted question-answer history.
2. System does not regenerate synchronously from the UI request.
3. UI refreshes through persisted-state polling/push.
4. Professor may leave and return later.

### AF-2: Temporary model failure

**Branches from:** Main Flow step 6  
**Condition:** Model times out or returns a transient provider error.

1. Worker increments attempt metadata, changes the job to `RETRYABLE`, and returns the report to `PENDING` according to policy.
2. Assignment remains `SUBMITTED` and student completion remains final.
3. Persisted turns remain available to professor.
4. Job retries with bounded backoff without blocking tutor-turn jobs.

### AF-3: Invalid report output

**Branches from:** Main Flow step 8  
**Condition:** Candidate is malformed, unsafe, unsupported by transcript, or violates the structured contract.

1. Backend rejects the candidate and stores safe failure metadata only.
2. Raw invalid output is not shown as the report.
3. Job follows bounded retry/failure policy.
4. Assignment and turns remain unchanged.

### AF-4: Retry limit is exhausted

**Branches from:** AF-2 or AF-3  
**Condition:** Configured attempts are exhausted.

1. Report becomes `FAILED` with a safe professor-facing message and operational error code.
2. Submitted assignment is not reopened or rolled back.
3. Professor can still inspect ordered question-answer evidence.
4. An explicitly authorized retry command may create one new semantic job.

### AF-5: Duplicate report job or retry command

**Branches from:** Main Flow step 1, step 3, or AF-4  
**Condition:** Duplicate terminal event, worker claim, or retry request occurs.

1. Unique assignment/report and semantic job keys prevent duplicate reports.
2. Exactly one current report record remains authoritative.
3. A `READY` report is returned idempotently and not regenerated without an explicit approved regeneration feature.

### AF-6: Stale worker result

**Branches from:** Main Flow step 9  
**Condition:** Expected report/job input version no longer matches.

1. Result application is rejected.
2. Worker records a stale outcome for observability.
3. Current authoritative report state is preserved.
4. No older candidate overwrites a newer report.

### AF-7: Insufficient evidence completion

**Branches from:** Main Flow step 5 or 8  
**Condition:** UC-007 completed with `COMPLETE_INSUFFICIENT_EVIDENCE`.

1. Report clearly states that conclusions are limited by insufficient relevant/evaluable responses.
2. Strengths or weaknesses are included only when supported by concrete turns.
3. System does not invent mastery, misconceptions, or certainty.
4. Professor sees a clear evidence-limited indicator; student does not see internal tutor labels.
5. Flow continues at Main Flow step 9.

### AF-8: Unauthorized report access

**Branches from:** Main Flow step 11  
**Condition:** User cannot review the activity/student in the active class context.

1. System denies access without exposing report or transcript content.
2. No report job or state is changed.
3. Use case ends.

### AF-9: Historical activity is closed or archived

**Branches from:** Main Flow step 10  
**Condition:** Parent activity is no longer published/open.

1. Authorized professor can still read submitted report and turns.
2. Historical data remains immutable.
3. No student answers or report content can be edited through the review surface.

---

## Postconditions

- **On success:** One structured `READY` report exists for the submitted assignment; professor can read it and the authoritative ordered turns independently.
- **On insufficient evidence:** Report is honest about limitations and contains only claims supported by persisted evidence.
- **On failure:** Assignment stays submitted, student remains completed, turns remain reviewable, and report generation is retryable/observable without blocking tutor runtime.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Student completion and navigation occur before and independently from report generation. |
| BR-02 | Assignment submission, report creation, and initial report-job creation are atomic. |
| BR-03 | Exactly one report exists per assignment. |
| BR-04 | Report generation is asynchronous and never runs on the professor/student Vaadin request thread. |
| BR-05 | No database transaction remains open during the model call. |
| BR-06 | Report jobs have lower priority than live student tutor-turn jobs when model capacity is shared. |
| BR-07 | Report input uses immutable activity instructions and ordered persisted turns, not unsaved UI state. |
| BR-08 | The report is structured data; rendered Markdown/HTML is not its source of truth. |
| BR-09 | Question-answer history comes directly from `training_activity_turn` and is not duplicated inside report prose. |
| BR-10 | Report includes summary, strengths, weaknesses, observations, recommendations, and evidence status. |
| BR-11 | Every evaluative claim must be supportable from the transcript and activity instructions. |
| BR-12 | Insufficient-evidence reports explicitly limit conclusions and never invent student understanding. |
| BR-13 | Report model output is backend-validated before becoming authoritative. |
| BR-14 | Model failure never reopens, un-submits, or hides the student's assignment. |
| BR-15 | Duplicate terminal events, jobs, and retry commands are idempotent. |
| BR-16 | Closed/archived activities retain immutable submitted reports and turns. |
| BR-17 | Students do not see internal decision labels or professor-only report diagnostics unless a later approved use case grants that visibility. |
| BR-18 | Hidden model chain-of-thought is neither stored nor displayed; only the validated report content and decision metadata are retained. |

### Validated report result

The backend contract is equivalent to:

```json
{
  "evidenceStatus": "WEAK_EVIDENCE | PARTIAL_EVIDENCE | STRONG_EVIDENCE",
  "summary": "...",
  "strengths": [{ "observation": "...", "turnReferences": [1, 3] }],
  "weaknesses": [{ "observation": "...", "turnReferences": [2] }],
  "observations": [{ "observation": "...", "turnReferences": [1, 2] }],
  "recommendations": ["..."]
}
```

Every referenced turn must exist in the assignment and contain the cited evidence.

---

## Tests

- [ ] Main Flow covered from atomic submission through background generation and professor review.
- [ ] Student redirect is observed before a controllable report model is released.
- [ ] AF-1 pending/generating UI covered without synchronous regeneration.
- [ ] AF-2 and AF-3 transient/invalid model outputs preserve submission and turns.
- [ ] AF-4 terminal failure and authorized retry covered.
- [ ] AF-5 duplicate job/report idempotency covered.
- [ ] AF-6 stale report result cannot overwrite current state.
- [ ] AF-7 insufficient-evidence honesty and no invented claims covered.
- [ ] AF-8 authorization and class scope covered.
- [ ] AF-9 historical report preservation covered.
- [ ] Turn list renders from structured rows even when report is pending/failed.
- [ ] BR-01 through BR-18 covered.

---

## UI Surface

- Student workspace completed state, available immediately after submission.
- Professor activity detail with per-student report status.
- Professor report panel with pending/generating/ready/failed states.
- Structured report cards followed by an ordered question-answer list sourced from turns.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Completed assignment row | Authenticated owning student | `/student` |
| Per-student report detail | Authenticated authorized professor/reviewer | Published/closed activity detail at `/training-activities` |
