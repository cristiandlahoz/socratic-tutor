# Training Activity Reports

This document explains how Socratic Tutor creates **formative activity reports**
from the guided evaluation flow and exposes them to the professor workspace.

The important split is:

```text
TrainingActivity             = activity definition and lifecycle
TrainingActivityAssignment   = one student's evaluation state
evaluation_transcript        = real question/answer exchanges
final_report                 = persisted professor-facing report
TrainingActivityDialog       = report display projection
```

The report is not a separate aggregate. It is generated when the student's
assignment reaches a terminal submitted state and is stored on the assignment
itself.

## Purpose

Formative activity reports give the professor a compact, reviewable artifact for
each student submission.

They preserve:

- activity title
- number of guided reflection steps completed
- tutor questions asked during the evaluation
- student answers
- assignment status and submission timing

They do not replace the assignment transcript. The transcript remains the source
used to build the report.

## Data model

The activity module stores report state in two tables:

```text
training_activity
  - id
  - group_class_id
  - created_by_tenant_account_id
  - created_by_group_class_member_id
  - title
  - instructions
  - status
  - opens_at
  - closes_at
  - safe_browser_enabled
  - created_at
  - updated_at

training_activity_assignment
  - id
  - training_activity_id
  - group_class_member_id
  - status
  - assigned_at
  - started_at
  - submitted_at
  - current_question
  - question_count
  - evaluation_transcript
  - final_report
  - safe_browser_* fields
  - updated_at
```

The report field is:

```text
training_activity_assignment.final_report
```

It is nullable while the activity is still assigned or in progress. It is filled
only when the guided evaluation finishes.

## Runtime flow

```text
Professor launches activity
        │
        ▼
┌─────────────────────────────────────────┐
│ TrainingActivityService.launch(...)     │
│                                         │
│ 1. require DRAFT activity               │
│ 2. find unlocked students               │
│ 3. create ASSIGNED assignments          │
│ 4. mark activity PUBLISHED              │
│ 5. notify students after commit         │
└────────────────────┬────────────────────┘
                     │
                     ▼
Student opens assignment
        │
        ▼
┌─────────────────────────────────────────┐
│ TrainingAssignmentEvaluationService     │
│ .start(...)                             │
│                                         │
│ 1. require current student assignment   │
│ 2. ensure assignment is answerable      │
│ 3. ask first tutor question             │
│ 4. mark assignment STARTED              │
└────────────────────┬────────────────────┘
                     │
                     ▼
Student answers questions
        │
        ▼
┌─────────────────────────────────────────┐
│ TrainingAssignmentEvaluationService     │
│ .answer(...)                            │
│                                         │
│ 1. append Q/A to evaluation_transcript  │
│ 2. ask next tutor question              │
│ 3. if no next question: submit          │
│ 4. generate final_report                │
└────────────────────┬────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│ Professor opens TrainingActivityDialog  │
│                                         │
│ report button enabled only when         │
│ final_report is present                 │
└─────────────────────────────────────────┘
```

## Assignment lifecycle

The report is tied to assignment completion:

```text
ASSIGNED
   │ start(...)
   ▼
STARTED
   │ answer(...)
   │
   ├── next question exists ─────────────► STARTED
   │
   └── no next question
          │
          ▼
      SUBMITTED
          │
          ▼
      final_report generated
```

The current tutor runtime is adaptive. It starts with a model-generated first
question, persists every accepted answer before the next decision, and keeps
asking only while the transcript is producing useful evidence.

The assignment can close in two internal ways:

```text
COMPLETE_SUCCESS                  -> enough evidence for a normal report
COMPLETE_INSUFFICIENT_EVIDENCE    -> student-facing completion stays normal,
                                     but the professor report states that the
                                     transcript was not sufficient for a
                                     reliable evaluation
```

## Transcript storage

The evaluation transcript is stored as JSON in the assignment:

```text
training_activity_assignment.evaluation_transcript
```

In Java it is represented as:

```java
public record EvaluationExchange(String question, String answer) {}
```

On every answer, the service appends one exchange:

```text
current_question + submitted answer
        │
        ▼
evaluation_transcript JSON
```

Before report generation, the JSON transcript is converted to markdown:

```markdown
### Pregunta 1
<question>

**Respuesta del estudiante:**
<answer>

### Pregunta 2
<question>

**Respuesta del estudiante:**
<answer>
```

That markdown shape is important because the UI report projection parses the
question headings and answer labels later.

## Report generation

Core class:

```text
src/main/java/com/wornux/services/training_activity/
TrainingAssignmentEvaluationService.java
```

Generation point:

```java
assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
assignment.setSubmittedAt(Instant.now());
assignment.setCurrentQuestion(null);
assignment.setFinalReport(
    tutorService.finalReport(assignment, transcript, finalDecision)
);
```

The report text is created by:

```text
src/main/java/com/wornux/services/training_activity/
TrainingAssignmentTutorService.java
```

Current report shape:

```text
## Síntesis diagnóstica
<AI formative synthesis grounded in observable evidence>

## Evidencias observables
- <facts that are directly visible in the transcript>

## Fortalezas observadas
- <strengths grounded in the transcript>

## Dudas o aspectos a trabajar
- <reinforcement opportunities grounded in the transcript>

## Limitaciones de esta evaluación
- <limits caused by weak evidence or tutor false premises>

## Recomendación docente
<suggested teacher follow-up>

## Transcripción

<markdown transcript>
```

The mechanism now makes an LLM call from `TrainingAssignmentTutorService` using
`prompt/training_activity/report-prompt.st`. The prompt receives the activity
instructions, title, a teacher-friendly closure summary, transcript-grounded
evidence hints, explicit evaluation limitations, and the canonical transcript
markdown, then asks for a compact professor-facing **formative diagnostic
report** as valid JSON only: `{"report": "markdown completo aquí"}`.

The report is still persisted in:

```text
training_activity_assignment.final_report
```

The LLM does not become the source of truth. The source of truth remains:

```text
training_activity_assignment.evaluation_transcript
```

If the model call fails, returns invalid JSON, or does not include a usable
`report` field, submission still completes. In that case the service stores a
fallback report with the same diagnostic sections, observable evidence,
strengths, teacher-facing limitations, and a teacher recommendation. Both
model-generated and fallback reports must be honest about insufficient evidence,
must never invent understanding that does not exist in the transcript, and must
not expose internal runtime metadata such as enum names, `type=...`,
`answerQuality=...`, or `coverageStatus=...`.

The diagnostic report must not assign numeric grades, percentages,
approved/rejected labels, rankings, or definitive judgments. Its purpose is to
help the professor understand the student's visible responses, likely strengths,
confusions, and useful next pedagogical steps. Fallback reports must stay honest:
they infer only observable signals such as very brief answers, “no sé”, examples,
explicit reasoning, code, or blank answers.

## Professor display projection

Reports are displayed inside the activity dialog:

```text
src/main/java/com/wornux/ui/training_activity/
TrainingActivityDialog.java
```

The professor opens an activity, and the dialog renders an assignments grid:

```text
assignment row
  - student
  - status
  - Safe Browser status
  - report button
  - incident action
```

The report button is enabled only when the assignment has a report:

```java
button.setEnabled(
    assignment.getFinalReport() != null && !assignment.getFinalReport().isBlank()
);
```

Opening the report switches the overlay from activity mode to report mode:

```text
TrainingActivityDialog
  -> reportButton(...)
  -> renderReportMode(...)
  -> reportBody(...)
  -> extractTranscriptEvidence(...)
  -> parseQuestions(...)
```

## Structured report parsing

The report view sanitizes teacher-facing text to remove internal metadata, then
separates the narrative from the canonical transcript before it
parses question cards. This keeps narrative lines in `## Lectura por intercambio`
from being mistaken for real transcript questions.

Patterns:

```text
QUESTION_LABEL_PATTERN         -> Pregunta N labels, including markdown headings like ### Pregunta N
TRANSCRIPT_HEADING_PATTERN     -> ## Transcripción / ## Evidencia disponible / Transcript headings
STUDENT_ANSWER_LABEL_PATTERN   -> **Respuesta del estudiante:**
```

When a transcript/evidence heading is present, the parser only reads the content
after that heading so narrative sections such as `## Lectura por intercambio` are
not mistaken for transcript cards. Legacy canonical reports without a
transcript/evidence heading are still parsed as a whole report, so stored reports
that start directly with `### Pregunta 1` continue to render question cards.

If parsing succeeds, the UI renders the transcript evidence as question cards:

```text
┌─────────────────────────────────────────┐
│ Reporte diagnóstico formativo           │
├─────────────────────────────────────────┤
│ Pregunta 1                              │
│ question text                           │
│                                         │
│ Respuesta del estudiante                │
│ answer text                             │
├─────────────────────────────────────────┤
│ Pregunta 2                              │
│ ...                                     │
└─────────────────────────────────────────┘
```

If parsing fails, the dialog falls back to rendering the stored markdown report
body. The stored `final_report` remains the display source either way, while
`evaluation_transcript` remains the canonical conversation source.

## Activity closing behavior

Reports are generated by submission, not by manual activity close.

Manual close does this:

```text
TrainingActivityService.close(...)
  - mark activity CLOSED
  - find non-submitted assignments
  - mark non-terminal assignments EXPIRED
  - deactivate Safe Browser sessions
```

It does not synthesize final reports for expired assignments. A report exists
only for assignments that reached `SUBMITTED` through the evaluation flow.

## Student review behavior

After submission, the student sees a completion message:

```text
La actividad formativa ha finalizado. Tu profesor ya puede revisar el reporte.
```

If the activity is later closed, a submitted assignment can be reopened in review
mode. In that mode the normal shell is preserved and the input composer is hidden.

The student sees the real evaluation transcript and completion message. The
professor sees the persisted `final_report` projection from the activity dialog.

## Implementation map

Activity and assignment entities:

```text
src/main/java/com/wornux/data/entities/training_activity/
TrainingActivity.java
TrainingActivityAssignment.java
TrainingActivityAssignmentStatus.java
TrainingActivityLifecycleStatus.java
```

Activity lifecycle and assignment creation:

```text
src/main/java/com/wornux/services/training_activity/
TrainingActivityService.java
TrainingActivityLaunchedBus.java
```

Student evaluation and report generation:

```text
src/main/java/com/wornux/services/training_activity/
TrainingAssignmentEvaluationService.java
TrainingAssignmentTutorService.java
```

Student assignment UI:

```text
src/main/java/com/wornux/ui/training_activity/
TrainingAssignmentView.java
```

Professor report UI:

```text
src/main/java/com/wornux/ui/training_activity/
TrainingActivityView.java
TrainingActivityDialog.java
```

Student dashboard projection:

```text
src/main/java/com/wornux/services/workspace/StudentWorkspaceService.java
src/main/java/com/wornux/ui/student/StudentWorkspaceView.java
```

## Things not to do

- Do not create reports from the professor dialog. Reports belong to assignment
  submission.
- Do not treat `final_report` as the source transcript. The source transcript is
  `evaluation_transcript`.
- Do not generate reports for expired assignments unless the domain rule changes.
- Do not change the markdown transcript headings casually; the current report UI
  parses `### Pregunta N` and `**Respuesta del estudiante:**`.
- Do not assume every assignment row has a report. Only submitted assignments do.

## Future improvements

- Store report sections structurally instead of parsing markdown in the UI.
- Add report quality metadata such as strengths, misconceptions, and next steps.
- Add focused tests for `TrainingActivityDialog` report parsing and fallback
  rendering.
- Decide whether expired assignments should have an explicit no-submission report.
