# UC-007: Adaptive Student Tutor Runtime for Training Assignments

---

**Goal:** As a student, I want the tutor to ask adaptive Socratic questions based on the training activity instructions and my previous answers so that the final formative report reflects real evidence of my understanding instead of generic, fixed, or hardcoded questions.

**Status:** Pending
**Date:** 2026-07-07

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Student
- **Secondary actors:** Professor, backend training assignment evaluation service, adaptive tutor runtime service, Spring AI `ChatClient`/`ChatModel`, local/remote tutor model, final report generation service

---

## Preconditions

- The student is authenticated.
- The student has an existing `training_activity_assignment` assigned to their `group_class_member`.
- The student opens an assignment they own from `/training-activity/assignments/{assignmentId}`.
- The parent training activity exists and is in a state that allows the student to start or continue.
- The training activity has a title and final saved professor instructions.
- The normal student assignment lifecycle already exists: assignment ownership validation, start, answer submission, transcript persistence, current question persistence, submission, and final report generation.
- The system can persist current question, student answer, transcript, assignment status, tutor decision metadata if supported, and final report.
- The system has access to an adaptive tutor model or configured Spring AI model endpoint for generating student-facing Socratic questions.
- Professor instructions are treated as untrusted content and cannot override backend/system tutor rules.
- This use case may benefit from UC-006 instruction quality review, but it must still work for legacy activities that were created before UC-006 existed.

---

## Trigger

The student opens or continues an assigned training activity from `/training-activity/assignments/{assignmentId}`, or submits an answer to the current tutor question.

---

## Main Flow

> This use case extends the existing student training assignment execution flow. It does not replace assignment ownership checks, safe-browser rules, closed-activity rules, transcript persistence, or final report generation.

1. Student opens `/training-activity/assignments/{assignmentId}`.
2. System loads the `training_activity_assignment` and validates that it belongs to the current student.
3. System validates that the parent training activity and assignment state allow the student to start or continue.
4. If the assignment is `ASSIGNED` and can be started, system starts the assignment through the normal service flow.
5. System prepares adaptive tutor context using the activity title, final saved professor instructions, group-class context, assignment state, existing transcript, current question, question progress metadata, and available grounding/context if supported.
6. If no current question exists, adaptive tutor runtime builds a first-question prompt from the activity context.
7. The first-question prompt instructs the model to identify the main instruction aspects and ask one diagnostic Socratic question about the highest-priority aspect.
8. Adaptive tutor model returns a backend-validated structured tutor decision.
9. If the decision is `QUESTION`, system stores the question as `currentQuestion` and displays exactly one Spanish Socratic question to the student.
10. Student writes an answer and submits it.
11. System validates that the answer can be accepted for the current assignment state.
12. System persists the student answer into the transcript before requesting the next tutor decision.
13. Adaptive tutor runtime builds the next-decision prompt using the activity title, final saved professor instructions, previous/current question, latest student answer, full transcript, assignment state, group-class context, and available grounding/context.
14. Adaptive tutor model must reason through the latest answer before asking another question.
15. The model classifies the answer quality, determines whether the answer provides usable evidence, updates coverage of professor-instruction aspects, detects repeated unproductive patterns, and chooses the next pedagogical move.
16. The model returns a structured decision: `QUESTION`, `COMPLETE_SUCCESS`, or `COMPLETE_INSUFFICIENT_EVIDENCE`.
17. Backend validates the tutor decision before storing or acting on it.
18. If the decision is `QUESTION`, system stores the next question, updates progress metadata, optionally stores tutor decision metadata, and displays the question to the student.
19. Student answers the next question.
20. Flow repeats from Main Flow step 11 until the tutor decision is `COMPLETE_SUCCESS` or `COMPLETE_INSUFFICIENT_EVIDENCE`.
21. If the decision is `COMPLETE_SUCCESS`, system submits/completes the assignment through the normal submission flow.
22. System generates a final formative report grounded in sufficient, varied, relevant transcript evidence and professor instructions.
23. If the decision is `COMPLETE_INSUFFICIENT_EVIDENCE`, system submits/completes the assignment through the normal student-facing completion flow, while storing insufficient-evidence status or equivalent internal report metadata.
24. System still generates a final formative report for the professor, but the report is explicitly limited by the transcript quality and explains that the student did not provide enough relevant or evaluable evidence for a reliable evaluation.
25. System shows the normal completed/submitted state to the student; it must not expose `COMPLETE_INSUFFICIENT_EVIDENCE`, evidence labels, or internal model decision labels to the student.
26. Professor can later review the student transcript, tutor decisions if stored, evidence status, internal insufficient-evidence tag, and final report through the existing review surface.

---

## Alternative Flows

### AF-1: Assignment does not belong to current student

**Branches from:** Main Flow step 2
**Condition:** Student opens an assignment id that is not assigned to their `group_class_member`.

1. System denies access.
2. System shows a no-access or not-found state.
3. No transcript, current question, assignment status, or final report is changed.
4. Adaptive tutor model is not called.
5. Use case ends.

### AF-2: No active or answerable assignment state

**Branches from:** Main Flow step 3 or step 11
**Condition:** Parent training activity is closed, assignment is locked, assignment is submitted, assignment is not started when required, or the assignment is otherwise not answerable.

1. System rejects the start or answer action.
2. System shows the appropriate blocked, closed, locked, submitted, or no-access state.
3. System does not update transcript, current question, assignment status, or final report.
4. Adaptive tutor model is not called for a new question.
5. Use case ends.

### AF-3: Assignment is already submitted

**Branches from:** Main Flow step 3
**Condition:** Student opens an assignment that has already been submitted or completed.

1. System loads the submitted assignment.
2. System does not call the adaptive tutor model for a new question.
3. System shows the completed/submitted state and existing report if available.
4. Student cannot modify previous answers.
5. Use case ends.

### AF-4: First tutor decision is invalid

**Branches from:** Main Flow step 8
**Condition:** Tutor model returns malformed JSON, invalid enum values, multiple questions, a non-Spanish question, an explanation instead of a question, unsafe text, or a question not grounded in the activity context.

1. Backend rejects the invalid tutor decision.
2. System logs the failure with assignment id, training activity id, progress count, model name, and error details.
3. System does not store the invalid question as authoritative.
4. System does not silently fall back to generic hardcoded questions in production.
5. Depending on configuration, system shows a friendly temporary error or allows retry.
6. Use case ends or returns to Main Flow step 5.

### AF-5: Student submits an empty or minimal answer

**Branches from:** Main Flow step 15
**Condition:** Student submits an empty answer, greeting, monosyllable, or minimal text such as “hola”, “ok”, “sí”, “no”, or “no sé”.

1. System persists the answer into the transcript if the assignment can accept answers.
2. Adaptive tutor model classifies the answer as `EMPTY` or equivalent no-evidence state.
3. If this is an early unproductive answer and another attempt is reasonable, model returns `QUESTION` with `REFOCUS` or `ASK_FOR_CLARITY`.
4. The question asks the student to provide a minimal concrete idea related to the activity.
5. The tutor does not advance to a new concept as if the answer were valid evidence.
6. Flow returns to Main Flow step 10.

### AF-6: Student submits absurd, spam, or joke content

**Branches from:** Main Flow step 15
**Condition:** Student submits random text, keyboard mashing, spam, repeated characters, jokes, or text such as “asdasdasd”.

1. System persists the answer into the transcript if the assignment can accept answers.
2. Adaptive tutor model classifies the answer as `ABSURD` or equivalent no-evidence state.
3. If this is an early occurrence, model returns `QUESTION` with a firm but respectful reconduction move.
4. The question asks for a real answer connected to the previous question or activity instructions.
5. The tutor does not regañar, mock, or give the answer.
6. Flow returns to Main Flow step 10.

### AF-7: Student answer is off topic

**Branches from:** Main Flow step 15
**Condition:** Student answer is understandable but does not respond to the tutor question or activity instructions.

1. Adaptive tutor model classifies the answer as `OFF_TOPIC`.
2. Model chooses `REFOCUS` or `REPHRASE`.
3. Model asks one question that connects the student back to the activity objective or the previous question.
4. System stores and displays the question.
5. Flow returns to Main Flow step 10.

### AF-8: Student answer is too vague

**Branches from:** Main Flow step 15
**Condition:** Student answer is related to the activity but too general, superficial, or unsupported.

1. Adaptive tutor model classifies the answer as `TOO_VAGUE`.
2. Model chooses `ASK_FOR_EXAMPLE`, `ASK_FOR_JUSTIFICATION`, or `ASK_FOR_CLARITY`.
3. Model asks one question requesting precision, a concrete example, or a reason.
4. The tutor does not treat the vague answer as enough evidence for completion.
5. Flow returns to Main Flow step 10.

### AF-9: Student answer is partially correct or almost understands

**Branches from:** Main Flow step 15
**Condition:** Latest answer contains a valid idea but is incomplete, confused, or mixes concepts.

1. Adaptive tutor model classifies the answer as `PARTIALLY_CORRECT`.
2. Model identifies the strongest idea and the main confusion.
3. Model chooses a move such as `REPHRASE`, `ASK_FOR_CLARITY`, `PROBE_MISCONCEPTION`, or `ASK_FOR_JUSTIFICATION`.
4. Model generates exactly one Socratic question that targets the weak or confused part without giving the answer.
5. System stores and displays the next question.
6. Flow returns to Main Flow step 10.

### AF-10: Student answer is good, but activity coverage is incomplete

**Branches from:** Main Flow step 15
**Condition:** Latest answer is useful, but important aspects from the professor instructions remain uncovered.

1. Adaptive tutor model classifies the answer as `GOOD` or `EXCELLENT`.
2. Model marks current evidence as useful but coverage as partial.
3. Model chooses `INCREASE_DIFFICULTY`, `MOVE_TO_NEXT_ASPECT`, `ASK_FOR_EXAMPLE`, `ASK_FOR_JUSTIFICATION`, or `TRANSFER_TO_NEW_CASE`.
4. Model generates exactly one question about another relevant instruction aspect or a deeper version of the same aspect.
5. System stores and displays the next question.
6. Flow returns to Main Flow step 10.

### AF-11: Student answer is excellent and coverage is sufficient

**Branches from:** Main Flow step 15
**Condition:** Latest answer is clear, specific, justified, and the transcript already covers the main instruction aspects.

1. Adaptive tutor model classifies the answer as `EXCELLENT`.
2. Model verifies that transcript evidence is sufficient, varied, and relevant.
3. Model returns `COMPLETE_SUCCESS`.
4. Backend validates covered aspects, evidence status, and reason.
5. System completes the assignment through the normal submission flow.
6. System generates a final report grounded in transcript evidence.
7. Use case ends.

### AF-12: Student repeatedly gives vague, absurd, evasive, or off-topic answers

**Branches from:** Main Flow step 15
**Condition:** Transcript shows a repeated unproductive pattern and the model determines that continuing is unlikely to produce useful evidence for a reliable report.

1. Adaptive tutor model returns `COMPLETE_INSUFFICIENT_EVIDENCE`.
2. Backend validates that the decision includes evidence status, missing instruction aspects, unproductive pattern flag, and reason.
3. System does not ask more questions just to reach a numeric quota.
4. System completes the assignment through the normal student-facing completion flow while storing insufficient-evidence status or equivalent internal metadata for report generation and professor review.
5. System generates a final report for the professor explaining why the transcript does not support a reliable evaluation.
6. System does not invent student understanding that is not supported by the transcript.
7. Student only sees the normal completed/submitted state; the internal insufficient-evidence decision is not displayed to the student.
8. Use case ends.

### AF-13: Technical maximum turn limit is reached

**Branches from:** Main Flow step 15
**Condition:** A configurable technical safety limit is reached to prevent infinite loops.

1. System asks the model or backend decision policy to determine whether existing evidence is sufficient or insufficient.
2. If evidence is sufficient, flow follows AF-11.
3. If evidence is insufficient, flow follows AF-12.
4. System does not treat reaching the technical limit as successful completion by itself.
5. Final report clearly reflects the evidence status.
6. Use case ends.

### AF-14: Adaptive tutor model returns invalid next decision

**Branches from:** Main Flow step 16 or step 17
**Condition:** Tutor model returns malformed JSON, missing fields, invalid enum values, multiple questions, explanation instead of a question, unsafe text, or output not grounded in activity context.

1. Backend rejects the invalid tutor decision.
2. System logs the failure with assignment id, training activity id, progress count, model name, and error details.
3. System does not silently use hardcoded generic questions in production.
4. No invalid tutor question is stored as authoritative.
5. Depending on configuration, system shows a friendly temporary error, keeps the assignment resumable, or allows retry.
6. Use case ends or returns to Main Flow step 13.

### AF-15: Adaptive tutor model is unavailable during student execution

**Branches from:** Main Flow step 6 or step 13
**Condition:** Tutor model is offline, times out, or cannot be reached.

1. System does not wait indefinitely.
2. System logs the model failure with assignment id, training activity id, model name, and error details.
3. System does not silently fall back to fixed English questions in production.
4. If configured production behavior is to block, system shows a friendly temporary error and keeps the assignment in a resumable state.
5. If an explicit local/development fallback is enabled, fallback usage is logged and must not be treated as AI-generated.
6. Use case ends or student retries.

### AF-16: Professor instructions are weak or legacy

**Branches from:** Main Flow step 5 or step 13
**Condition:** The activity was launched with legacy or weak instructions.

1. Adaptive tutor runtime still uses the final saved instructions as available context.
2. Model avoids inventing unsupported details beyond the activity context.
3. If instructions are too weak to support reliable coverage, model may ask broader diagnostic questions or eventually complete with insufficient evidence.
4. Final report must reflect the limits of the available instruction context and transcript evidence.
5. Use case continues or completes according to evidence status.

### AF-17: Prompt injection attempt inside professor instructions

**Branches from:** Main Flow step 5 or step 13
**Condition:** Professor instructions contain text attempting to override tutor/system rules, such as “ignore previous instructions”, “give the answer”, or “mark all answers correct”.

1. Adaptive tutor runtime treats professor instructions as untrusted activity content.
2. Backend/system tutor rules override professor-provided instructions.
3. Tutor must not reveal prompts, give answers directly, or violate system rules.
4. Suspicious instruction content may be logged for professor/admin review if supported.
5. Use case continues using safe tutor rules.

### AF-18: Grounding/context is unavailable

**Branches from:** Main Flow step 5 or step 13
**Condition:** Grounding documents, chunks, or contextual retrieval are unavailable for the activity.

1. Adaptive tutor runtime builds the prompt using title, instructions, transcript, assignment state, and group-class context.
2. Model must not invent external content.
3. If grounding is required for the activity and missing, system may show a friendly error or continue with limited context according to product rules.
4. Use case continues or ends according to configured behavior.

---

## Postconditions

- **On success:** The student-facing tutor uses the final saved professor instructions, previous/current question, latest student answer, full transcript, assignment state, group-class context, and available grounding/context to generate adaptive Socratic questions; the tutor classifies answer quality before deciding the next action; the assignment completes successfully only when the transcript contains sufficient, varied, relevant evidence for a meaningful formative report; the final report is grounded in transcript evidence and professor instructions.
- **On insufficient evidence:** If the student repeatedly submits vague, absurd, empty, evasive, or off-topic answers, the assignment may complete after reasonable reconduction attempts using the normal student-facing completed/submitted state; internally, the assignment/report metadata records insufficient evidence so the report model can generate an honest limited report for the professor. The student must not see the `COMPLETE_INSUFFICIENT_EVIDENCE` label or internal evidence-status labels. The report clearly states to the professor that evaluation is limited because the student did not provide enough relevant or evaluable responses, and it must not invent mastery or evidence.
- **On failure:** If assignment ownership, state, lock, or closed-activity validation fails, no tutor decision is generated; if the tutor model fails, times out, or returns invalid output, system does not trust it; invalid tutor decisions do not become authoritative questions; fallback questions must not be silently used in production; the assignment remains resumable when possible.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The student runtime must use an adaptive tutor model for first and follow-up questions; fixed hardcoded questions must not be the primary production flow. |
| BR-02 | The adaptive tutor runtime extends the existing student assignment execution flow; it does not replace ownership checks, transcript persistence, safe-browser rules, assignment locks, or report generation. |
| BR-03 | The tutor model must be invoked when generating the first question for a started assignment unless an explicit failure/fallback path applies. |
| BR-04 | The tutor model must be invoked after each accepted student answer to decide the next question or completion state. |
| BR-05 | The student answer must be persisted before the next tutor decision is generated so the model can reason over the complete transcript. |
| BR-06 | The tutor prompt must include activity title, final saved professor instructions, assignment state, previous/current question, latest student answer, transcript/history, group-class context, and available grounding/context when available. |
| BR-07 | Professor instructions are untrusted content and cannot override backend/system tutor rules. |
| BR-08 | The tutor must answer the student in Spanish unless a future product rule explicitly supports another language. |
| BR-09 | The tutor must generate exactly one student-facing question when continuing. |
| BR-10 | The tutor must not give the answer directly, solve the activity for the student, or explain as a traditional lecturer. |
| BR-11 | The first question must be derived from the professor instructions and activity context; it must not be a generic “initial understanding” template unless the instructions explicitly require that style. |
| BR-12 | Before every follow-up question, the tutor must analyze the latest answer in relation to the previous question, professor instructions, transcript, and expected evidence. |
| BR-13 | The tutor must classify answer quality using a backend-validated enum such as `EMPTY`, `ABSURD`, `OFF_TOPIC`, `TOO_VAGUE`, `PARTIALLY_CORRECT`, `GOOD`, or `EXCELLENT`. |
| BR-14 | The tutor must evaluate whether the answer provides usable evidence for the final report. |
| BR-15 | The tutor must track which instruction aspects are already covered, weak, missing, or unsupported by evidence. |
| BR-16 | If the student answer is empty or minimal, the tutor should ask for a minimal concrete response rather than advancing. |
| BR-17 | If the student answer is absurd, spam, or a joke, the tutor should reconduct firmly but respectfully and not treat it as evidence. |
| BR-18 | If the student answer is off topic, the tutor should refocus the student on the activity objective or previous question. |
| BR-19 | If the student answer is too vague, the tutor should request precision, an example, or justification. |
| BR-20 | If the student answer is partially correct, the tutor should identify the weak/confused part and probe it without giving the answer. |
| BR-21 | If the student answer is good but coverage is incomplete, the tutor should increase difficulty, ask for transfer/application, ask for justification, or move to another instruction aspect. |
| BR-22 | If the student answer is excellent about one aspect, the tutor must not repeat the same aspect unnecessarily; it should move to uncovered aspects or complete if coverage is sufficient. |
| BR-23 | The activity must not complete successfully only because a fixed number of questions was reached. |
| BR-24 | The activity must complete successfully only when the transcript contains sufficient, varied, relevant evidence to produce a useful formative report. |
| BR-25 | The tutor may complete with insufficient evidence when the student repeatedly submits non-evaluable, vague, absurd, evasive, or off-topic responses after reasonable reconduction attempts. |
| BR-25A | `COMPLETE_INSUFFICIENT_EVIDENCE` is an internal tutor/report-generation decision, not a student-facing status label. |
| BR-25B | The student must only see the normal completed/submitted state after an insufficient-evidence completion, unless a future product decision defines a different student-facing message without exposing internal labels. |
| BR-25C | Insufficient-evidence metadata must be stored for the final report model and professor review so the report can explain the transcript limitations honestly. |
| BR-26 | The tutor should not keep asking indefinitely when the transcript shows no reasonable chance of producing a useful report. |
| BR-27 | A technical maximum turn limit may exist to prevent infinite loops, but reaching the limit is not by itself evidence of successful completion. |
| BR-28 | If the technical maximum turn limit is reached without sufficient evidence, the assignment should complete with insufficient evidence or equivalent limited-report status. |
| BR-29 | The final report must not invent mastery, understanding, or evidence not present in the transcript. |
| BR-30 | A successful final report should cite concrete transcript evidence for strengths, weaknesses, misconceptions, and recommendations. |
| BR-31 | An insufficient-evidence final report should explain to the professor that evaluation is limited because the student did not provide enough relevant or evaluable responses. |
| BR-32 | Student-facing questions must not be hardcoded as the primary production flow. |
| BR-33 | Existing strings such as “What is your initial understanding of this activity?”, “Which concept feels least clear after your first answer?”, and “Can you explain the idea with a concrete example?” must not be used as the normal production tutor flow. |
| BR-34 | Fallback questions must be explicit, configurable, logged, and disabled or strongly restricted in production. |
| BR-35 | The system must not silently fall back to fixed English questions in production. |
| BR-36 | Tutor model failures must be logged with assignment id, training activity id, question/progress count, model name, and error details. |
| BR-37 | Invalid JSON or invalid tutor decisions must not be treated as valid questions or completion decisions. |
| BR-38 | A `QUESTION` decision must include one Spanish student-facing question in `questionText`. |
| BR-39 | A completion decision must use empty `questionText` and include a clear reason. |
| BR-40 | Suggested decision types are `QUESTION`, `COMPLETE_SUCCESS`, and `COMPLETE_INSUFFICIENT_EVIDENCE`. |
| BR-41 | Suggested evidence statuses are `NO_EVIDENCE`, `WEAK_EVIDENCE`, `PARTIAL_EVIDENCE`, and `STRONG_EVIDENCE`. |
| BR-42 | Suggested coverage statuses are `NONE`, `WEAK`, `PARTIAL`, and `SUFFICIENT`. |
| BR-43 | Suggested pedagogical moves include `REFOCUS`, `REPHRASE`, `ASK_FOR_CLARITY`, `ASK_FOR_EXAMPLE`, `ASK_FOR_JUSTIFICATION`, `PROBE_MISCONCEPTION`, `INCREASE_DIFFICULTY`, `MOVE_TO_NEXT_ASPECT`, `TRANSFER_TO_NEW_CASE`, `COMPLETE_SUCCESSFULLY`, and `COMPLETE_WITH_INSUFFICIENT_EVIDENCE`. |
| BR-44 | If an assignment is already submitted, closed, locked, or not answerable, the adaptive tutor model must not be called to generate another question. |
| BR-45 | This use case depends on final saved professor instructions, but it must still support legacy activities without UC-006 review metadata. |
| BR-46 | Tutor prompts and model configuration must be centralized in the backend, not hardcoded in the Vaadin view or TypeScript component. |

### Required tutor decision shape

The adaptive tutor runtime must produce a backend-validated object equivalent to:

```json
{
  "type": "QUESTION | COMPLETE_SUCCESS | COMPLETE_INSUFFICIENT_EVIDENCE",
  "answerQuality": "EMPTY | ABSURD | OFF_TOPIC | TOO_VAGUE | PARTIALLY_CORRECT | GOOD | EXCELLENT",
  "evidenceStatus": "NO_EVIDENCE | WEAK_EVIDENCE | PARTIAL_EVIDENCE | STRONG_EVIDENCE",
  "coverageStatus": "NONE | WEAK | PARTIAL | SUFFICIENT",
  "pedagogicalMove": "REFOCUS | REPHRASE | ASK_FOR_CLARITY | ASK_FOR_EXAMPLE | ASK_FOR_JUSTIFICATION | PROBE_MISCONCEPTION | INCREASE_DIFFICULTY | MOVE_TO_NEXT_ASPECT | TRANSFER_TO_NEW_CASE | COMPLETE_SUCCESSFULLY | COMPLETE_WITH_INSUFFICIENT_EVIDENCE",
  "shouldContinue": true,
  "coveredInstructionAspects": ["..."],
  "missingInstructionAspects": ["..."],
  "unproductivePatternDetected": false,
  "questionText": "¿...?",
  "reason": "..."
}
```

### Required adaptive tutor system prompt

```text
Eres un tutor socrático adaptativo para actividades formativas.

Tu trabajo es obtener evidencia suficiente y útil sobre la comprensión del estudiante siguiendo las instrucciones del profesor.

No eres una lista fija de preguntas.
No debes terminar por una cantidad fija de preguntas.
No debes seguir preguntando eternamente si el estudiante no aporta evidencia.
No debes inventar evidencia ni asumir comprensión que el estudiante no demostró.

En cada turno debes razonar así:

1. Lee las instrucciones de la actividad.
2. Identifica qué aspectos, conceptos, habilidades o evidencias deben evaluarse.
3. Lee la pregunta anterior, la última respuesta del estudiante y el historial completo.
4. Clasifica la última respuesta como EMPTY, ABSURD, OFF_TOPIC, TOO_VAGUE, PARTIALLY_CORRECT, GOOD o EXCELLENT.
5. Decide si esa respuesta aporta evidencia útil para el reporte final.
6. Actualiza qué aspectos de las instrucciones ya están cubiertos, cuáles están débiles y cuáles faltan.
7. Detecta si hay un patrón improductivo de respuestas vacías, absurdas, evasivas, vagas o fuera de tema.
8. Decide el mejor movimiento pedagógico: reconducir, reformular, pedir claridad, pedir ejemplo, pedir justificación, explorar una confusión, subir dificultad, mover a otro aspecto, finalizar con éxito o finalizar por evidencia insuficiente.
9. Si continúas, genera exactamente una pregunta en español.
10. Si ya hay evidencia suficiente y variada para un buen reporte, finaliza con COMPLETE_SUCCESS.
11. Si ya no hay señales razonables de que seguir preguntando produzca un buen reporte, devuelve internamente `COMPLETE_INSUFFICIENT_EVIDENCE` para que el backend genere un reporte limitado para el profesor.

Reglas obligatorias:

- Responde siempre en español.
- Haz exactamente una pregunta si decides continuar.
- No des la respuesta correcta.
- No expliques como profesor tradicional.
- No regañes ni humilles al estudiante.
- No trates una respuesta vacía, absurda, vaga o fuera de tema como evidencia válida.
- Si el estudiante escribe “hola”, “asdasd”, “jajaja”, “no sé”, “ok” o texto sin sentido, reconduce con firmeza amable.
- Si el estudiante casi entiende, reformula o pregunta desde otro ángulo.
- Si el estudiante responde parcialmente bien, profundiza en la parte débil.
- Si el estudiante responde bien, aumenta la dificultad o cambia a otro aspecto pendiente.
- Si el estudiante responde excelente sobre un aspecto, no repitas lo mismo; avanza a otro aspecto relevante o finaliza si ya hay evidencia suficiente.
- Si el estudiante responde basura, saludos, bromas o evasivas repetidas, finaliza internamente con evidencia insuficiente para que el reporte del profesor refleje esa limitación.
- Esa decisión interna no debe redactarse como mensaje para el estudiante; el estudiante solo verá que la actividad quedó completada/enviada.
- No finalices exitosamente si no podrías escribir un reporte útil con evidencias concretas del transcript.
- No inventes información fuera del contexto disponible.
```

### Required adaptive tutor user prompt

```text
Actividad formativa:
Título: {{title}}

Instrucciones del profesor:
{{instructions}}

Contexto académico:
Tenant: {{tenant}}
Group class: {{groupClass}}
Estado de la asignación: {{assignmentStatus}}
Cantidad de preguntas realizadas: {{questionCount}}

Pregunta actual/anterior:
{{currentQuestion}}

Última respuesta del estudiante:
{{lastAnswer}}

Historial completo:
{{transcript}}

Grounding disponible:
{{grounding}}

Analiza el historial y decide la próxima acción.

Debes evaluar:

1. Qué pedían realmente las instrucciones del profesor.
2. Qué aspectos ya fueron cubiertos por respuestas del estudiante.
3. Qué aspectos siguen sin evidencia suficiente.
4. Si la última respuesta fue vacía, absurda, fuera de tema, vaga, parcialmente correcta, buena o excelente.
5. Si la última respuesta aporta evidencia útil para el reporte final.
6. Qué movimiento pedagógico conviene ahora.
7. Si todavía vale la pena preguntar más.
8. Si ya existe evidencia suficiente para cerrar con éxito.
9. Si ya no vale la pena seguir preguntando porque el estudiante no aporta evidencia útil.
10. Si se debe cerrar internamente con evidencia insuficiente para que el reporte del profesor no invente comprensión.
11. Si se llegó a un límite técnico sin evidencia suficiente.

Devuelve SOLO JSON válido con la forma definida por el contrato del backend.
```

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [ ] Main Flow covered: student starts assignment, adaptive tutor generates first question, student answer is persisted, tutor analyzes the answer, returns a next decision, and assignment completes with success or with internal insufficient-evidence metadata while showing the normal completed state to the student.
- [ ] Assignment ownership and state alternatives covered: AF-1 through AF-3.
- [ ] First tutor decision failure covered: AF-4.
- [ ] Empty/minimal answer alternative covered: AF-5.
- [ ] Absurd/spam answer alternative covered: AF-6.
- [ ] Off-topic answer alternative covered: AF-7.
- [ ] Too-vague answer alternative covered: AF-8.
- [ ] Partially correct answer alternative covered: AF-9.
- [ ] Good-but-incomplete answer alternative covered: AF-10.
- [ ] Excellent-and-sufficient answer alternative covered: AF-11.
- [ ] Repeated unproductive pattern alternative covered: AF-12.
- [ ] Technical maximum turn limit covered: AF-13.
- [ ] Adaptive tutor model failure alternatives covered: AF-14 and AF-15.
- [ ] Weak instruction and prompt safety alternatives covered: AF-16 and AF-17.
- [ ] Grounding/context alternative covered: AF-18.
- [ ] Business rule covered: first question is generated through adaptive tutor model, not hardcoded strings.
- [ ] Business rule covered: next question is generated after each accepted student answer through adaptive tutor model.
- [ ] Business rule covered: student answer is persisted before next tutor decision.
- [ ] Business rule covered: prompt includes title, final instructions, previous/current question, latest answer, transcript, assignment state, group class, and grounding/context if available.
- [ ] Business rule covered: tutor classifies answer quality before generating the next question.
- [ ] Business rule covered: tutor reconducts empty, absurd, vague, or off-topic answers.
- [ ] Business rule covered: tutor probes partially correct answers instead of treating them as complete understanding.
- [ ] Business rule covered: tutor moves to another instruction aspect or increases difficulty when the student answers well but coverage is incomplete.
- [ ] Business rule covered: successful completion is based on sufficient evidence, not a fixed number of questions.
- [ ] Business rule covered: repeated non-evaluable answers can complete the assignment with internal insufficient-evidence metadata.
- [ ] Business rule covered: final report does not invent evidence not present in the transcript.
- [ ] Business rule covered: model failure does not silently fall back to generic hardcoded questions in production.
- [ ] Anti-regression test covered: the strings `What is your initial understanding of this activity?`, `Which concept feels least clear after your first answer?`, and `Can you explain the idea with a concrete example?` are not used as the primary student tutor path.
- [ ] Business rule covered: submitted, closed, locked, or not-answerable assignments do not call the tutor model.
- [ ] Business rule covered: `COMPLETE_INSUFFICIENT_EVIDENCE` is returned internally when repeated bad answers make a useful report unlikely.
- [ ] Business rule covered: student does not see `COMPLETE_INSUFFICIENT_EVIDENCE`, evidence-status labels, or internal tutor decision labels after completion.
- [ ] Business rule covered: insufficient-evidence metadata is available to the professor report generator even though the student-facing completion state remains normal.
- [ ] Business rule covered: `COMPLETE_SUCCESS` requires sufficient, varied, relevant evidence tied to professor instructions.

---

## UI Surface

> This use case is an authenticated student workspace flow using the existing Vaadin Flow assignment route and any existing TypeScript-enhanced conversation/composer components. There is no public anonymous route.

- Student assigned training activity execution screen: student sees one adaptive Socratic question at a time, submits answers, and receives follow-up questions generated from final saved professor instructions, transcript, latest answer, and assignment context.
- Student answer composer: may use existing TypeScript composer behavior for answer input, submit state, loading state while the tutor model decides, and error/retry state when model generation fails.
- Student completion state: always shows the normal completed/submitted state after tutor completion. It must not expose `COMPLETE_INSUFFICIENT_EVIDENCE`, evidence-status labels, or internal model decision labels to the student.
- Professor report/review surface: shows final report and transcript, including whether the report is based on sufficient evidence or limited by insufficient student responses. This surface may show the internal insufficient-evidence tag or a professor-facing equivalent.
- Service methods remain authoritative for assignment ownership, permission, group-class context, assignment lock, closed-activity rules, transcript persistence, and completion.

| Surface | Access | Entry Point |
|---------|--------|-------------|
| Student assigned training activity execution | Authenticated owning student | `/training-activity/assignments/{assignmentId}` |
| Student answer composer | Authenticated owning student | Embedded in assignment execution screen |
| Student completion state | Authenticated owning student | Existing assignment route after submission |
| Professor report/review surface | Authenticated professor or authorized reviewer | Existing training activity review/detail route |
