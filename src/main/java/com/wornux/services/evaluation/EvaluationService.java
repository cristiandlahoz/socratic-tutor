package com.wornux.services.evaluation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.wornux.services.profile.StudentProfileService;
import com.wornux.services.subject.SubjectConfigService;
import com.wornux.data.entities.EvaluationAttempt;
import com.wornux.data.entities.EvaluationAttemptQuestion;
import com.wornux.data.entities.EvaluationAttemptResponse;
import com.wornux.data.entities.EvaluationGuideArtifact;
import com.wornux.data.entities.EvaluationQuestionExample;
import com.wornux.data.entities.EvaluationResultArtifact;
import com.wornux.data.entities.EvaluationRevision;
import com.wornux.data.enums.EvaluationAttemptCompletionReason;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.data.repositories.subject.SubjectConfigRevisionRepository;
import com.wornux.data.repositories.evaluation.EvaluationAttemptRepository;
import com.wornux.data.repositories.evaluation.EvaluationAttemptQuestionRepository;
import com.wornux.data.repositories.evaluation.EvaluationAttemptResponseRepository;
import com.wornux.data.repositories.evaluation.EvaluationGuideArtifactRepository;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import com.wornux.data.repositories.evaluation.EvaluationQuestionExampleRepository;
import com.wornux.data.repositories.evaluation.EvaluationResultArtifactRepository;
import com.wornux.data.repositories.evaluation.EvaluationRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String GRADING_SYSTEM =
            """
            You grade a diagnostic evaluation. Return JSON only.
            Do not invent certainty. If evidence is thin, put it in uncertaintyNotes.
            overallScore must be null when the answers are too sparse to grade.
            Use strengths, weakConcepts, activeMisconceptions, and tutorRecommendations as concise labels.
            """;

    private final EvaluationRepository evaluationRepository;
    private final EvaluationRevisionRepository revisionRepository;
    private final EvaluationAttemptRepository attemptRepository;
    private final EvaluationAttemptQuestionRepository attemptQuestionRepository;
    private final EvaluationAttemptResponseRepository responseRepository;
    private final EvaluationQuestionExampleRepository exampleRepository;
    private final EvaluationGuideArtifactRepository guideArtifactRepository;
    private final EvaluationResultArtifactRepository resultArtifactRepository;
    private final SubjectConfigRevisionRepository subjectConfigRevisionRepository;
    private final EvaluationQuestionGenerationService questionGenerationService;
    private final StudentProfileService studentProfileService;
    private final SubjectConfigService subjectConfigService;
    private final ChatModel chatModel;
    private final DiagnosticModePolicy diagnosticModePolicy;
    private final BeanOutputConverter<EvaluationGradeResult> outputConverter =
            new BeanOutputConverter<>(EvaluationGradeResult.class);
    private final ObjectMapper objectMapper;
    private final CurrentModeTurnFactory currentModeTurnFactory = new CurrentModeTurnFactory();
    private final Set<String> activePublishContexts = ConcurrentHashMap.newKeySet();
    private static final int DEFAULT_MAX_DIAGNOSTIC_QUESTIONS = 5;
    private static final int DEFAULT_MIN_DIAGNOSTIC_QUESTIONS = 2;
    private static final int MIN_FREE_TEXT_ANSWER_LENGTH = 10;

    public EvaluationService(
            EvaluationRepository evaluationRepository,
            EvaluationRevisionRepository revisionRepository,
            EvaluationAttemptRepository attemptRepository,
            EvaluationAttemptQuestionRepository attemptQuestionRepository,
            EvaluationAttemptResponseRepository responseRepository,
            EvaluationQuestionExampleRepository exampleRepository,
            EvaluationGuideArtifactRepository guideArtifactRepository,
            EvaluationResultArtifactRepository resultArtifactRepository,
            SubjectConfigRevisionRepository subjectConfigRevisionRepository,
            EvaluationQuestionGenerationService questionGenerationService,
            StudentProfileService studentProfileService,
            SubjectConfigService subjectConfigService,
            ChatModel chatModel,
            DiagnosticModePolicy diagnosticModePolicy,
            ObjectMapper objectMapper) {
        this.evaluationRepository = evaluationRepository;
        this.revisionRepository = revisionRepository;
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.responseRepository = responseRepository;
        this.exampleRepository = exampleRepository;
        this.guideArtifactRepository = guideArtifactRepository;
        this.resultArtifactRepository = resultArtifactRepository;
        this.subjectConfigRevisionRepository = subjectConfigRevisionRepository;
        this.questionGenerationService = questionGenerationService;
        this.studentProfileService = studentProfileService;
        this.subjectConfigService = subjectConfigService;
        this.chatModel = chatModel;
        this.diagnosticModePolicy = diagnosticModePolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EvaluationAttemptVm launchEvaluation(UUID clientId, UUID evaluationId) {
        var evaluation = evaluationRepository.findWithCurrentRevisionById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationId));
        if (evaluation.getCurrentRevision() == null) {
            throw new IllegalStateException("Evaluation has no published revision");
        }
        var revision = revisionRepository.findWithExamplesById(evaluation.getCurrentRevision().getId())
                .orElseThrow(() -> new IllegalStateException("Evaluation has no published revision"));
        var examples = revision.getQuestionExamples().isEmpty()
                ? exampleRepository.findByEvaluationRevisionOrderByOrdinalAsc(revision)
                : revision.getQuestionExamples();

        var profileSnapshot = studentProfileService.load(clientId);
        var subject = subjectConfigService.current(revision.getSubjectConfigRevision().getSubject().getSlug());
        var generatedQuestions =
                questionGenerationService.generate(subject, revision, examples, profileSnapshot.learningProfile());
        var attempt = EvaluationAttempt.launch(
            revision,
            clientId,
            null,
            objectMapper.convertValue(profileSnapshot, MAP_TYPE),
            profileSnapshot.profileVersion());
        for (var question : generatedQuestions) {
            var snapshot = questionSnapshot(subject.revisionId(), revision.getId(), question);
            var sourceExample = sourceExample(question, examples);
            attempt.addGeneratedQuestion(
                sourceExample,
                question.questionKey(),
                question.blueprintKey(),
                question.ordinal(),
                snapshot,
                hash(snapshot));
        }
        attempt = attemptRepository.save(attempt);
        return toAttemptVm(attempt);
    }

    @Transactional
    public void submitResponse(UUID attemptQuestionId, String freeText, List<String> selectedOptions) {
        var attemptQuestion = attemptQuestionRepository.findById(attemptQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown attempt question " + attemptQuestionId));
        responseRepository.save(EvaluationAttemptResponse.answer(attemptQuestion, freeText, selectedOptions));
    }

    @Transactional
    public EvaluationAttemptVm submitAttempt(UUID attemptId) {
        var attempt = requireAttempt(attemptId);
        attempt.markSubmitted();
        return toAttemptVm(attemptRepository.save(attempt));
    }

    @Transactional
    public EvaluationAttemptVm gradeAttempt(UUID attemptId) {
        var attempt = requireAttempt(attemptId);
        var grade = gradeOrFallback(attempt);
        var feedback = objectMapper.convertValue(grade, MAP_TYPE);
        attempt.applyGrade(grade.overallScore(), feedback);
        var saved = attemptRepository.save(attempt);
        studentProfileService
                .applyEvaluationProfile(attempt.getClientId(), attempt.getId(), toLearningProfile(saved, grade));
        return toAttemptVm(saved);
    }

    @Transactional(readOnly = true)
    public EvaluationReport latestReport(UUID clientId, UUID subjectId) {
        var attempt = attemptRepository
                .findFirstByClientIdAndEvaluationRevision_Evaluation_Subject_IdOrderByStartedAtDesc(clientId, subjectId)
                .orElseThrow(() -> new IllegalArgumentException("No evaluation report exists"));
        return new EvaluationReport(attempt.getId(),
                attempt.getStatus().name(),
                attempt.getScore(),
                attempt.getStartedAt(),
                attempt.getGradedAt(),
                attempt.getFeedback(),
                questionsFor(attempt));
    }

    @Transactional(readOnly = true)
    public List<EvaluationAttemptVm> attempts(UUID clientId, String subjectSlug) {
        if (subjectSlug == null || subjectSlug.isBlank()) {
            return attemptRepository.findByClientIdOrderByStartedAtDesc(clientId)
                    .stream()
                    .map(this::toAttemptVm)
                    .toList();
        }
        return attemptRepository
                .findByClientIdAndEvaluationRevision_Evaluation_Subject_SlugOrderByStartedAtDesc(clientId, subjectSlug)
                .stream()
                .map(this::toAttemptVm)
                .toList();
    }

    @Transactional(readOnly = true)
    public UUID defaultEvaluationId() {
        return evaluationRepository.findFirstByStatusOrderByUpdatedAtDesc(EvaluationStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Default evaluation is missing"))
                .getId();
    }

    @Transactional(readOnly = true)
    public EvaluationTargetVm resolveEvaluationTarget(UUID evaluationId) {
        var evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationId));
        return new EvaluationTargetVm(evaluation.getId(), evaluation.getSubject().getSlug(), evaluation.getSlug());
    }

    @Transactional
    public UUID publishEvaluationRevision(
            String subjectSlug,
            String evaluationSlug,
            String instructions,
            Map<String, Object> settings,
            Map<String, Object> rubric,
            List<String> exampleGuidelines) {
        var evaluation = evaluationRepository.findBySubject_SlugAndSlug(subjectSlug, evaluationSlug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationSlug));
        var subjectConfig = subjectConfigService.current(subjectSlug);
        var subjectConfigRevision = subjectConfigRevisionRepository.findById(subjectConfig.revisionId())
                .orElseThrow(() -> new IllegalStateException("Subject config revision is missing"));
        var latest = revisionRepository.findFirstByEvaluationOrderByVersionDesc(evaluation);
        long nextVersion = latest.map(revision -> revision.getVersion() + 1).orElse(1L);
        var revision = revisionRepository.save(
            EvaluationRevision.create(
                evaluation,
                subjectConfigRevision,
                nextVersion,
                instructions == null || instructions.isBlank() ? "Generate a diagnostic evaluation." : instructions,
                settings,
                rubric));
        int ordinal = 1;
        for (var guideline : exampleGuidelines == null ? List.<String>of() : exampleGuidelines) {
            if (guideline == null || guideline.isBlank()) {
                continue;
            }
            exampleRepository.save(
                EvaluationQuestionExample
                        .create(revision, "teacher-example-" + ordinal, ordinal, guideline, Map.of()));
            ordinal++;
        }
        evaluation.publish(revision);
        evaluationRepository.save(evaluation);
        return revision.getId();
    }

    @Transactional
    public UUID publishDefaultEvaluationRevision(String instructions, List<String> exampleGuidelines) {
        var evaluation = evaluationRepository.findFirstByStatusOrderByUpdatedAtDesc(EvaluationStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Default evaluation is missing"));
        return publishEvaluationRevision(
            evaluation.getSubject().getSlug(),
            evaluation.getSlug(),
            instructions,
            Map.of("allowFreeText", true, "showReviewBeforeSubmit", true),
            Map.of("profileEvidenceOnly", true),
            exampleGuidelines);
    }

    @Transactional
    public PublishEvaluationVm publishEvaluationRevisionWithLifecycle(
            String subjectSlug,
            String evaluationSlug,
            String instructions,
            Map<String, Object> settings,
            Map<String, Object> rubric,
            List<String> exampleGuidelines) {
        var contextKey = subjectSlug + ":" + evaluationSlug;
        if (!activePublishContexts.add(contextKey)) {
            return new PublishEvaluationVm(
                    PublishLifecycleState.IN_PROGRESS,
                    null,
                    null,
                    "A publication attempt is already in progress");
        }
        try {
            var revisionId = publishEvaluationRevision(
                    subjectSlug, evaluationSlug, instructions, settings, rubric, exampleGuidelines);
            var evaluation = evaluationRepository.findBySubject_SlugAndSlug(subjectSlug, evaluationSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation " + evaluationSlug));
            var revision = revisionRepository.findById(revisionId)
                    .orElseThrow(() -> new IllegalStateException("Published revision not found"));
            var guide = guideArtifactRepository.save(EvaluationGuideArtifact.create(
                    evaluation,
                    revision,
                    instructions == null || instructions.isBlank()
                            ? "Generate a diagnostic evaluation."
                            : instructions,
                    Instant.now()));
            return new PublishEvaluationVm(
                    PublishLifecycleState.COMPLETED, revisionId, guide.getId(), null);
        }
        catch (RuntimeException exception) {
            return new PublishEvaluationVm(PublishLifecycleState.FAILED, null, null, exception.getMessage());
        }
        finally {
            activePublishContexts.remove(contextKey);
        }
    }

    @Transactional(readOnly = true)
    public List<EvaluationGuideArtifactVm> publishedGuides(UUID evaluationId) {
        return guideArtifactRepository.findByEvaluation_IdOrderByPublishedAtDesc(evaluationId).stream()
                .map(artifact -> new EvaluationGuideArtifactVm(
                        artifact.getId(),
                        artifact.getEvaluation().getId(),
                        artifact.getRevision().getId(),
                        artifact.getGuideContent(),
                        artifact.getPublishedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationGuideArtifactVm guideDetail(UUID evaluationId, UUID guideArtifactId) {
        var artifact = guideArtifactRepository.findByIdAndEvaluation_Id(guideArtifactId, evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Guide artifact not found"));
        return new EvaluationGuideArtifactVm(
                artifact.getId(),
                artifact.getEvaluation().getId(),
                artifact.getRevision().getId(),
                artifact.getGuideContent(),
                artifact.getPublishedAt());
    }

    @Transactional
    public DiagnosticSessionVm startDiagnosticSession(UUID clientId, UUID evaluationId) {
        var launched = launchEvaluation(clientId, evaluationId);
        var attempt = requireAttempt(launched.attemptId());
        attempt.markSubmitted();
        var saved = attemptRepository.save(attempt);
        var firstQuestion = selectActiveQuestion(questionsForAttempt(saved));
        return new DiagnosticSessionVm(
                saved.getId(),
                saved.getStatus().name(),
                0,
                resolveMaxQuestions(saved.getEvaluationRevision()),
                currentModeTurnFactory.fromQuestion(firstQuestion, "Respond with free text (min 10 chars)."),
                saved.getCompletionReason() == null ? null : saved.getCompletionReason().name(),
                DiagnosticContinuationDecision.CONTINUE);
    }

    @Transactional(readOnly = true)
    public DiagnosticSessionVm activeDiagnosticSession(UUID attemptId) {
        var attempt = requireAttempt(attemptId);
        var questions = questionsForAttempt(attempt);
        var activeQuestion = selectActiveQuestion(questions);
        int answeredCount = questions.stream().mapToInt(this::responseCount).sum();
        return new DiagnosticSessionVm(
                attempt.getId(),
                attempt.getStatus().name(),
                answeredCount,
                resolveMaxQuestions(attempt.getEvaluationRevision()),
                currentModeTurnFactory.fromQuestion(activeQuestion, "Respond with free text (min 10 chars)."),
                attempt.getCompletionReason() == null ? null : attempt.getCompletionReason().name(),
                "COMPLETED".equals(attempt.getStatus().name())
                        ? DiagnosticContinuationDecision.COMPLETE_MAX_QUESTIONS
                        : DiagnosticContinuationDecision.CONTINUE);
    }

    @Transactional
    public DiagnosticSessionVm continueDiagnosticSession(UUID attemptId, UUID questionId, String answer) {
        var attempt = requireAttempt(attemptId);
        if (!"RUNNING".equals(attempt.getStatus().name()) && !"IN_PROGRESS".equals(attempt.getStatus().name())) {
            throw new IllegalStateException("Diagnostic session is not active");
        }
        var questions = questionsForAttempt(attempt);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Diagnostic session has no questions");
        }
        var activeQuestion = selectActiveAttemptQuestion(questions).orElse(null);
        if (activeQuestion == null || !activeQuestion.getId().equals(questionId)) {
            throw new IllegalStateException("Invalid question progression");
        }
        validateFreeTextAnswer(answer);
        int answeredBeforeCurrentTurn = questions.stream()
                .mapToInt(q -> q.getResponses() == null ? 0 : q.getResponses().size())
                .sum();
        responseRepository.save(EvaluationAttemptResponse.answer(activeQuestion, answer, List.of()));

        int answeredCount = answeredBeforeCurrentTurn + 1;
        int minQuestions = resolveMinQuestions(attempt.getEvaluationRevision());
        int maxQuestions = resolveMaxQuestions(attempt.getEvaluationRevision());
        var decision = diagnosticModePolicy.decideContinuation(
                answer,
                answeredCount,
                minQuestions,
                maxQuestions,
                List.of(Map.of("questionId", questionId.toString(), "answer", answer == null ? "" : answer)));
        if (decision == DiagnosticContinuationDecision.COMPLETE_MAX_QUESTIONS) {
            return completeDiagnostic(attempt, EvaluationAttemptCompletionReason.MAX_QUESTIONS,
                    decision, answeredCount, maxQuestions);
        }
        if (decision == DiagnosticContinuationDecision.COMPLETE_MODEL_STOP) {
            return completeDiagnostic(attempt, EvaluationAttemptCompletionReason.MODEL_STOP,
                    decision, answeredCount, maxQuestions);
        }

        var revision = attempt.getEvaluationRevision();
        var subject = subjectConfigService.current(revision.getSubjectConfigRevision().getSubject().getSlug());
        var examples = revision.getQuestionExamples().isEmpty()
                ? exampleRepository.findByEvaluationRevisionOrderByOrdinalAsc(revision)
                : revision.getQuestionExamples();
        var profile = studentProfileService.load(attempt.getClientId()).learningProfile();
        var turnContext = new CurrentModeTurnContext(
                "SOCRATIC_FREE_TEXT",
                answeredCount,
                questions.size() + 1,
                List.of(Map.of("questionId", questionId.toString(), "answer", answer == null ? "" : answer)),
                "CONTINUE",
                maxQuestions);
        var next = questionGenerationService.generateNextQuestion(
                subject,
                revision,
                examples,
                profile,
                turnContext);
        var snapshot = questionSnapshot(subject.revisionId(), revision.getId(), next);
        attempt.addGeneratedQuestion(sourceExample(next, examples), next.questionKey(), next.blueprintKey(), next.ordinal(), snapshot, hash(snapshot));
        var saved = attemptRepository.save(attempt);
        var nextQuestion = questionsForAttempt(saved).stream()
                .filter(q -> q.getOrdinal() == next.ordinal())
                .findFirst()
                .map(this::toQuestionVm)
                .orElse(null);
        return new DiagnosticSessionVm(
                saved.getId(),
                saved.getStatus().name(),
                answeredCount,
                maxQuestions,
                currentModeTurnFactory.fromQuestion(nextQuestion, "Respond with free text (min 10 chars)."),
                saved.getCompletionReason() == null ? null : saved.getCompletionReason().name(),
                DiagnosticContinuationDecision.CONTINUE);
    }

    @Transactional(readOnly = true)
    public List<EvaluationResultArtifactVm> resultHistory(UUID evaluationId) {
        return resultArtifactRepository.findByEvaluation_IdOrderByCompletedAtDesc(evaluationId).stream()
                .map(artifact -> new EvaluationResultArtifactVm(
                        artifact.getId(),
                        artifact.getEvaluation().getId(),
                        artifact.getRevision().getId(),
                        artifact.getAttempt().getId(),
                        artifact.getCompletedAt(),
                        artifact.getResultPayload()))
                .toList();
    }

    private DiagnosticSessionVm completeDiagnostic(
            EvaluationAttempt attempt,
            EvaluationAttemptCompletionReason reason,
            DiagnosticContinuationDecision decision,
            int answeredCount,
            int maxQuestions) {
        var grade = gradeOrFallback(attempt);
        var feedback = objectMapper.convertValue(grade, MAP_TYPE);
        attempt.applyGrade(grade.overallScore(), feedback);
        attempt.setCompletionReason(reason);
        var saved = attemptRepository.save(attempt);
        resultArtifactRepository.save(EvaluationResultArtifact.create(
                saved.getEvaluationRevision().getEvaluation(),
                saved.getEvaluationRevision(),
                saved,
                feedback,
                saved.getCompletedAt()));
        return new DiagnosticSessionVm(
                saved.getId(),
                saved.getStatus().name(),
                answeredCount,
                maxQuestions,
                null,
                reason.name(),
                decision);
    }

    private int resolveMaxQuestions(EvaluationRevision revision) {
        var value = revision.getSettings().get("maxQuestions");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return DEFAULT_MAX_DIAGNOSTIC_QUESTIONS;
    }

    private int resolveMinQuestions(EvaluationRevision revision) {
        var value = revision.getSettings().get("minQuestions");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return DEFAULT_MIN_DIAGNOSTIC_QUESTIONS;
    }

    private void validateFreeTextAnswer(String answer) {
        var normalized = answer == null ? "" : answer.trim();
        if (normalized.length() < MIN_FREE_TEXT_ANSWER_LENGTH) {
            throw new IllegalArgumentException("Answer must contain at least 10 characters");
        }
    }

    private EvaluationGradeResult gradeOrFallback(EvaluationAttempt attempt) {
        try {
            var subject = subjectConfigService
                    .current(attempt.getEvaluationRevision().getSubjectConfigRevision().getSubject().getSlug());
            var prompt = Prompt.builder()
                    .messages(
                        new SystemMessage(GRADING_SYSTEM),
                        new UserMessage("""
                                        Subject config:
                                        %s

                                        Attempt:
                                        %s

                                        Format:
                                        %s
                                        """.formatted(
                            subject.config(),
                            attemptPayload(attempt.getId()),
                            outputConverter.getFormat())))
                    .chatOptions(
                        OllamaChatOptions.builder().temperature(0.0).format(outputConverter.getJsonSchemaMap()).build())
                    .build();
            var raw = chatModel.call(prompt).getResult().getOutput().getText();
            var result = outputConverter.convert(raw);
            if (result == null) {
                throw new IllegalStateException("Empty grade result");
            }
            return result;
        }
        catch (RuntimeException exception) {
            return new EvaluationGradeResult(null,
                    "Automatic grading failed; no score assigned.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Review the submitted answers manually before adapting instruction."),
                    List.of("Model grading failed, so this attempt should not update mastery strongly."));
        }
    }

    private Map<String, Object> attemptPayload(UUID attemptId) {
        var attempt = attemptRepository.findReportById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation attempt " + attemptId));
        var payload = new LinkedHashMap<String, Object>();
        var questions = attemptQuestionRepository.findWithResponsesByAttemptOrderByOrdinalAsc(attempt);
        var responses = responseRepository.findByAttemptQuestionInOrderByAnsweredAtDesc(questions);
        payload.put("questions", questions.stream().map(this::questionPayload).toList());
        payload.put("responses", responses.stream().map(this::responsePayload).toList());
        return payload;
    }

    private Map<String, Object> questionPayload(EvaluationAttemptQuestion question) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("attemptQuestionId", question.getId());
        payload.put("questionKey", question.getQuestionKey());
        payload.put("blueprintKey", question.getBlueprintKey());
        payload.put("ordinal", question.getOrdinal());
        payload.put("questionSnapshot", question.getQuestionSnapshot());
        payload.put("questionHash", question.getQuestionHash());
        return payload;
    }

    private Map<String, Object> responsePayload(EvaluationAttemptResponse response) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("attemptQuestionId", response.getAttemptQuestion().getId());
        payload.put("freeText", response.getFreeText());
        payload.put("selectedOptions", response.getSelectedOptions());
        payload.put("score", response.getScore());
        payload.put("rubricResult", response.getRubricResult());
        payload.put("feedback", response.getFeedback());
        payload.put("answeredAt", response.getAnsweredAt());
        return payload;
    }

    private Map<String, Object> questionSnapshot(
            UUID subjectConfigRevisionId,
            UUID evaluationRevisionId,
            GeneratedEvaluationQuestion question) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("generationMode", "generated");
        snapshot.put("subjectConfigRevisionId", subjectConfigRevisionId);
        snapshot.put("evaluationRevisionId", evaluationRevisionId);
        snapshot.put("generatedAt", Instant.now().toString());
        snapshot.put("questionKey", question.questionKey());
        snapshot.put("blueprintKey", question.blueprintKey());
        snapshot.put("ordinal", question.ordinal());
        snapshot.put("topicKey", question.topicKey());
        snapshot.put("difficulty", question.difficulty());
        snapshot.put("prompt", question.prompt());
        snapshot.put("options", List.of());
        snapshot.put("expectedAnswer", question.expectedAnswer());
        snapshot.put("rubric", question.rubric());
        snapshot.put("sourceExampleIds", question.sourceExampleIds());
        return snapshot;
    }

    private EvaluationQuestionExample sourceExample(
            GeneratedEvaluationQuestion question,
            List<EvaluationQuestionExample> examples) {
        if (question.sourceExampleIds().isEmpty()) {
            return null;
        }
        var ids = question.sourceExampleIds();
        return examples.stream()
                .filter(example -> ids.contains(example.getId().toString()) || ids.contains(example.getExampleKey()))
                .findFirst()
                .orElse(null);
    }

    private EvaluationAttemptVm toAttemptVm(EvaluationAttempt attempt) {
        return new EvaluationAttemptVm(attempt.getId(),
                attempt.getEvaluationRevision().getId(),
                attempt.getStatus().name(),
                attempt.getScore(),
                questionsFor(attempt),
                attempt.getFeedback());
    }

    private List<EvaluationQuestionVm> questionsFor(EvaluationAttempt attempt) {
        return questionsForAttempt(attempt).stream().map(this::toQuestionVm).toList();
    }

    private List<EvaluationAttemptQuestion> questionsForAttempt(EvaluationAttempt attempt) {
        if (attempt.getQuestions() != null && !attempt.getQuestions().isEmpty()) {
            return attempt.getQuestions();
        }
        return attemptQuestionRepository.findByAttemptOrderByOrdinalAsc(attempt);
    }

    private EvaluationQuestionVm selectActiveQuestion(List<EvaluationAttemptQuestion> questions) {
        return selectActiveAttemptQuestion(questions).map(this::toQuestionVm).orElse(null);
    }

    private java.util.Optional<EvaluationAttemptQuestion> selectActiveAttemptQuestion(List<EvaluationAttemptQuestion> questions) {
        return questions.stream()
                .filter(question -> responseCount(question) == 0)
                .min(java.util.Comparator.comparingInt(EvaluationAttemptQuestion::getOrdinal));
    }

    private int responseCount(EvaluationAttemptQuestion question) {
        return question.getResponses() == null ? 0 : question.getResponses().size();
    }

    private EvaluationQuestionVm toQuestionVm(EvaluationAttemptQuestion question) {
        var snapshot = question.getQuestionSnapshot();
        Object options = snapshot.get("options");
        return new EvaluationQuestionVm(question.getId(),
                question.getQuestionKey(),
                question.getBlueprintKey(),
                question.getOrdinal(),
                String.valueOf(snapshot.getOrDefault("topicKey", "")),
                String.valueOf(snapshot.getOrDefault("difficulty", "")),
                String.valueOf(snapshot.getOrDefault("prompt", "")),
                options instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of());
    }

    private EvaluationAttempt requireAttempt(UUID attemptId) {
        return attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown evaluation attempt " + attemptId));
    }

    private StudentLearningProfile toLearningProfile(EvaluationAttempt attempt, EvaluationGradeResult grade) {
        var now = attempt.getGradedAt() == null ? Instant.now() : attempt.getGradedAt();
        return new StudentLearningProfile(signals("competency", grade.strengths(), attempt, now, false),
                signals("strength", grade.strengths(), attempt, now, false),
                signals("misconception", grade.activeMisconceptions(), attempt, now, true),
                signals("weak_concept", grade.weakConcepts(), attempt, now, true),
                List.of("Use guided questions and short traces when evidence is weak."),
                "es",
                List.of(attempt.getId().toString()),
                grade.uncertaintyNotes(),
                grade.tutorRecommendations());
    }

    private List<StudentLearningProfile.LearningSignal> signals(
            String source,
            List<String> labels,
            EvaluationAttempt attempt,
            Instant observedAt,
            boolean needsMoreEvidence) {
        return labels.stream()
                .map(
                    label -> new StudentLearningProfile.LearningSignal(label.toLowerCase().replace(' ', '_'),
                            label,
                            "evaluation:" + attempt.getId(),
                            1,
                            observedAt,
                            attempt.getScore() == null ? "unscored" : "score:" + attempt.getScore(),
                            false,
                            needsMoreEvidence))
                .toList();
    }

    private String hash(Map<String, Object> snapshot) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        }
        catch (NoSuchAlgorithmException ex) {
            return HexFormat.of().formatHex(snapshot.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 64);
        }
    }
}
