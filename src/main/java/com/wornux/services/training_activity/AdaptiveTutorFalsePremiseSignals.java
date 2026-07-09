package com.wornux.services.training_activity;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

public final class AdaptiveTutorFalsePremiseSignals {

    private static final Pattern ERROR_PREMISE_REQUEST_PATTERN = Pattern.compile(
            "(?iu)(error de sintaxis|no compila|qué token|que token|qué línea|que línea|qué esta mal|qué está mal|que esta mal|dónde está el error|donde está el error|dónde esta el error|donde esta el error|cuál es el error|cual es el error|por\\s+qu[eé]\\s+falla|por\\s+qu[eé]\\s+(?:me\\s+)?da\\s+error|por\\s+qu[eé]\\s+(?:me\\s+)?marca\\s+error)");
    private static final Pattern C_LIKE_CODE_SIGNAL_PATTERN = Pattern.compile(
            "(?is)(```c\\b|\\b(?:for|while|if)\\s*\\(|\\bprintf\\s*\\()");
    private static final Pattern FOR_LOOP_HEADER_PATTERN = Pattern.compile("(?s)for\\s*\\([^)]*;[^)]*;[^)]*\\)");
    private static final Pattern SAFE_PRINTF_STATEMENT_PATTERN = Pattern.compile("(?s)^printf\\s*\\([^;{}]*\\)\\s*;\\s*$");
    private static final Pattern STUDENT_CORRECTION_PATTERN = Pattern.compile(
            "(?iu)(no tiene error|no hay error|sí compila|si compila|compila bien|compila correctamente|el código compila|el codigo compila|no falla por no tener llaves|no necesita llaves)");
    private static final Pattern BRACELESS_LOOP_PATTERN = Pattern.compile(
            "(?iu)(sin llaves|sin braces|una sola instrucción|una sola instruccion|un solo enunciado)");

    private AdaptiveTutorFalsePremiseSignals() {
    }

    public static boolean containsErrorPremiseRequest(String value) {
        return matches(ERROR_PREMISE_REQUEST_PATTERN, value);
    }

    public static boolean containsLikelyValidCLoop(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        var matcher = FOR_LOOP_HEADER_PATTERN.matcher(value);
        while (matcher.find()) {
            var body = extractLoopBodyCandidate(value.substring(matcher.end()));
            if (body != null && isLikelyValidCLoopSnippet(matcher.group() + body)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsCLikeCodeSignal(String value) {
        return matches(C_LIKE_CODE_SIGNAL_PATTERN, value);
    }

    public static boolean containsStudentCorrection(String value) {
        return matches(STUDENT_CORRECTION_PATTERN, value);
    }

    public static boolean mentionsBracelessLoop(String value) {
        return matches(BRACELESS_LOOP_PATTERN, value);
    }

    static boolean isLikelyValidCLoopSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        var normalized = snippet.replace("```c", "").replace("```", "").trim();
        var matcher = FOR_LOOP_HEADER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        var header = matcher.group();
        if (!hasBalancedDelimiters(header) || header.chars().filter(ch -> ch == ';').count() != 2) {
            return false;
        }
        var body = normalized.substring(matcher.end()).trim();
        if (body.isBlank() || !hasBalancedDelimiters(body)) {
            return false;
        }
        if (body.startsWith("{")) {
            var block = extractBalancedBlock(body);
            if (block == null) {
                return false;
            }
            var content = block.substring(1, block.length() - 1).trim();
            return SAFE_PRINTF_STATEMENT_PATTERN.matcher(content).matches();
        }
        return SAFE_PRINTF_STATEMENT_PATTERN.matcher(body).matches();
    }

    private static String extractLoopBodyCandidate(String trailingText) {
        var trimmed = trailingText == null ? "" : trailingText.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.startsWith("{")) {
            return extractBalancedBlock(trimmed);
        }
        var statementEnd = trimmed.indexOf(';');
        if (statementEnd < 0) {
            return null;
        }
        return trimmed.substring(0, statementEnd + 1);
    }

    private static String extractBalancedBlock(String value) {
        var stack = new ArrayDeque<Character>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            var current = value.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if ((inSingleQuote || inDoubleQuote) && current == '\\') {
                escaping = true;
                continue;
            }
            if (!inDoubleQuote && current == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && current == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (current == '{') {
                stack.push(current);
            }
            else if (current == '}') {
                if (stack.isEmpty()) {
                    return null;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return value.substring(0, index + 1);
                }
            }
        }
        return null;
    }

    private static boolean hasBalancedDelimiters(String value) {
        var stack = new ArrayDeque<Character>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            var current = value.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if ((inSingleQuote || inDoubleQuote) && current == '\\') {
                escaping = true;
                continue;
            }
            if (!inDoubleQuote && current == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && current == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            switch (current) {
                case '(', '{', '[' -> stack.push(current);
                case ')' -> {
                    if (!matchesOpening(stack, '(')) {
                        return false;
                    }
                }
                case '}' -> {
                    if (!matchesOpening(stack, '{')) {
                        return false;
                    }
                }
                case ']' -> {
                    if (!matchesOpening(stack, '[')) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        return !inSingleQuote && !inDoubleQuote && stack.isEmpty();
    }

    private static boolean matchesOpening(ArrayDeque<Character> stack, char expected) {
        return !stack.isEmpty() && stack.pop() == expected;
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).find();
    }
}
