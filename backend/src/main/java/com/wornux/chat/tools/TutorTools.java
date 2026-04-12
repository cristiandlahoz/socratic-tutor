package com.wornux.chat.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TutorTools {

    private static final int SEARCH_TOP_K = 3;
    private static final Pattern SIMPLE_FOR_PATTERN = Pattern.compile(
            "for\\s*\\(\\s*(?:int\\s+)?([a-zA-Z_]\\w*)\\s*=\\s*(-?\\d+)\\s*;\\s*\\1\\s*([<>]=?)\\s*(-?\\d+)\\s*;\\s*\\1\\s*(\\+\\+|--)\\s*\\)",
            Pattern.MULTILINE);
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("([a-zA-Z_]\\w*)\\s*(\\+=|-=|=)\\s*([a-zA-Z_]\\w*|-?\\d+)\\s*;");

    private final VectorStore vectorStore;
    private final ToolUsageAuditService toolUsageAuditService;

    public TutorTools(VectorStore vectorStore, ToolUsageAuditService toolUsageAuditService) {
        this.vectorStore = vectorStore;
        this.toolUsageAuditService = toolUsageAuditService;
    }

    @Tool(name = "searchCourseMaterial", description = "Searches indexed course material for the most relevant passages about introductory algorithms and C programming.")
    public SearchCourseMaterialResult searchCourseMaterial(
            @ToolParam(description = "The student's question or the concept to search for.")
            String query,
            @ToolParam(required = false, description = "Optional topic hint like loops, arrays, or functions.")
            String topicHint,
            ToolContext toolContext) {
        String composedQuery = topicHint == null || topicHint.isBlank() ? query : "%s topic:%s".formatted(query, topicHint);
        return toolUsageAuditService.audit(
                "searchCourseMaterial",
                toolContext,
                "query_len=%d topic_hint=%s".formatted(query == null ? 0 : query.length(), topicHint == null ? "none" : topicHint),
                () -> {
                    List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                            .query(composedQuery)
                            .topK(SEARCH_TOP_K)
                            .similarityThreshold(0.60)
                            .build());
                    List<SearchHit> hits = documents == null ? List.of() : documents.stream()
                            .map(document -> new SearchHit(
                                    summarize(document.getText()),
                                    document.getMetadata().getOrDefault("source", "vector_store").toString(),
                                    document.getScore()))
                            .toList();
                    var result = new SearchCourseMaterialResult(hits, !hits.isEmpty());
                    return new ToolUsageAuditService.ToolResult<>(
                            result,
                            "hits=%d context_found=%s".formatted(hits.size(), result.contextFound()),
                            new ToolLearningSignal("topic=" + (topicHint == null ? "unknown" : topicHint), !hits.isEmpty(), "retrieval_context"));
                });
    }

    @Tool(name = "traceCProgram", description = "Produces a beginner-friendly trace for small introductory C loops and variable updates.")
    public TraceCProgramResult traceCProgram(
            @ToolParam(description = "A small C snippet to trace.")
            String code,
            @ToolParam(required = false, description = "Optional stdin content if the code expects input.")
            String stdin,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
                "traceCProgram",
                toolContext,
                "code_len=%d stdin=%s".formatted(code == null ? 0 : code.length(), stdin == null || stdin.isBlank() ? "no" : "yes"),
                () -> {
                    var matcher = SIMPLE_FOR_PATTERN.matcher(code == null ? "" : code);
                    if (!matcher.find()) {
                        var fallback = new TraceCProgramResult(false, List.of(), List.of(
                                "No pude ejecutar una traza automática segura para este fragmento.",
                                "Puedo ayudarte si lo reducimos a un ejemplo corto con inicialización, condición y actualización claras."
                        ));
                        return new ToolUsageAuditService.ToolResult<>(
                                fallback,
                                "supported=false",
                                new ToolLearningSignal("trace_support=manual", true, "unsupported_trace_requests_example"));
                    }

                    String iterator = matcher.group(1);
                    int start = Integer.parseInt(matcher.group(2));
                    String operator = matcher.group(3);
                    int end = Integer.parseInt(matcher.group(4));
                    boolean increment = "++".equals(matcher.group(5));
                    Map<String, Integer> variables = parseInitialVariables(code);
                    variables.put(iterator, start);
                    List<TraceStep> steps = new ArrayList<>();

                    for (int iteration = 0; iteration < 12 && conditionMatches(variables.get(iterator), operator, end); iteration++) {
                        Map<String, Integer> before = new LinkedHashMap<>(variables);
                        applyAssignments(code, variables);
                        Map<String, Integer> after = new LinkedHashMap<>(variables);
                        steps.add(new TraceStep(iteration, before, after));
                        variables.put(iterator, variables.get(iterator) + (increment ? 1 : -1));
                    }

                    var result = new TraceCProgramResult(true, steps, List.of(
                            "Sigue el cambio de variables por iteración.",
                            "Fíjate en cómo la condición del bucle decide cuándo se detiene."
                    ));
                    return new ToolUsageAuditService.ToolResult<>(
                            result,
                            "supported=true steps=%d".formatted(steps.size()),
                            new ToolLearningSignal("trace_steps=" + steps.size(), true, "trace_state_support"));
                });
    }

    @Tool(name = "evaluateStudentAnswer", description = "Evaluates a student's answer against a lightweight teaching rubric and highlights likely misconceptions.")
    public EvaluateStudentAnswerResult evaluateStudentAnswer(
            @ToolParam(description = "The original question or exercise prompt.")
            String question,
            @ToolParam(description = "The student's answer to evaluate.")
            String studentAnswer,
            @ToolParam(required = false, description = "Optional rubric key like loops, arrays, or functions.")
            String rubricKey,
            ToolContext toolContext) {
        return toolUsageAuditService.audit(
                "evaluateStudentAnswer",
                toolContext,
                "question_len=%d answer_len=%d rubric=%s".formatted(question == null ? 0 : question.length(), studentAnswer == null ? 0 : studentAnswer.length(), rubricKey == null ? "none" : rubricKey),
                () -> {
                    String normalized = (studentAnswer == null ? "" : studentAnswer).toLowerCase(Locale.ROOT);
                    List<String> misconceptions = new ArrayList<>();
                    if (normalized.contains("contador") && normalized.contains("guarda la suma")) {
                        misconceptions.add("counter_vs_accumulator");
                    }
                    if (normalized.contains("empieza en 1")) {
                        misconceptions.add("array_index_origin");
                    }
                    if (normalized.contains("hasta que sea falso") && normalized.contains("while")) {
                        misconceptions.add("loop_condition");
                    }
                    String understandingLevel = misconceptions.isEmpty() && normalized.length() > 40 ? "partial_understanding" : "needs_support";
                    List<String> coachingMoves = misconceptions.isEmpty()
                            ? List.of("Reconoce lo correcto primero y luego pide que justifique un paso clave.")
                            : List.of("Corrige la confusión central antes de seguir.", "Pide una traza breve o un ejemplo concreto.");
                    var result = new EvaluateStudentAnswerResult(understandingLevel, misconceptions, coachingMoves);
                    return new ToolUsageAuditService.ToolResult<>(
                            result,
                            "misconception=%s level=%s".formatted(misconceptions.isEmpty() ? "none" : misconceptions.getFirst(), understandingLevel),
                            new ToolLearningSignal("misconceptions=" + misconceptions.size(), !misconceptions.isEmpty(), "answer_evaluation"));
                });
    }

    private static String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 217) + "...";
    }

    private static Map<String, Integer> parseInitialVariables(String code) {
        Map<String, Integer> variables = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("(?:int|float|double)\\s+([a-zA-Z_]\\w*)\\s*=\\s*(-?\\d+)\\s*;").matcher(code == null ? "" : code);
        while (matcher.find()) {
            variables.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        return variables;
    }

    private static boolean conditionMatches(int current, String operator, int end) {
        return switch (operator) {
            case "<" -> current < end;
            case "<=" -> current <= end;
            case ">" -> current > end;
            case ">=" -> current >= end;
            default -> false;
        };
    }

    private static void applyAssignments(String code, Map<String, Integer> variables) {
        Matcher matcher = ASSIGNMENT_PATTERN.matcher(code == null ? "" : code);
        while (matcher.find()) {
            String variable = matcher.group(1);
            String operator = matcher.group(2);
            String operandToken = matcher.group(3);
            Integer current = variables.getOrDefault(variable, 0);
            Integer operand = variables.getOrDefault(operandToken, parseIntOrZero(operandToken));
            if (variable.equals(operandToken)) {
                continue;
            }
            switch (operator) {
                case "+=" -> variables.put(variable, current + operand);
                case "-=" -> variables.put(variable, current - operand);
                case "=" -> variables.put(variable, operand);
                default -> {
                }
            }
        }
    }

    private static int parseIntOrZero(String token) {
        try {
            return Integer.parseInt(token);
        }
        catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record SearchCourseMaterialResult(List<SearchHit> hits, boolean contextFound) {
    }

    public record SearchHit(String excerpt, String source, Double score) {
    }

    public record TraceCProgramResult(boolean supported, List<TraceStep> steps, List<String> teachingNotes) {
    }

    public record TraceStep(int iteration, Map<String, Integer> before, Map<String, Integer> after) {
    }

    public record EvaluateStudentAnswerResult(String understandingLevel, List<String> misconceptions, List<String> coachingMoves) {
    }
}
