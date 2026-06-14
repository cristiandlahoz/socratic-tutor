package com.wornux.infrastructure.external.crunner;

import java.util.ArrayList;
import java.util.List;

import com.wornux.services.crunner.CDiagnostic;
import com.wornux.services.crunner.CDiagnosticSeverity;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SarifDiagnosticParser {

    private final ObjectMapper objectMapper;

    public SarifDiagnosticParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CDiagnostic> parse(String sarif, String source) {
        if (sarif == null || sarif.isBlank()) {
            return List.of();
        }
        try {
            var root = objectMapper.readTree(sarif);
            var diagnostics = new ArrayList<CDiagnostic>();
            for (var run : root.path("runs")) {
                for (var result : run.path("results")) {
                    diagnostics.add(toDiagnostic(result, source == null ? "" : source));
                }
            }
            return diagnostics;
        }
        catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to parse SARIF diagnostics", exception);
        }
    }

    private static CDiagnostic toDiagnostic(JsonNode result, String source) {
        var region = firstRegion(result);
        var line = positiveOrNull(region.path("startLine").asInt(0));
        var column = positiveOrNull(region.path("startColumn").asInt(0));
        var endLine = positiveOrNull(region.path("endLine").asInt(0));
        var endColumn = positiveOrNull(region.path("endColumn").asInt(0));
        var fromOffset = line == null ? null : offsetFor(source, line, column == null ? 1 : column);
        var toOffset = fromOffset == null
                ? null
                : offsetFor(
                    source,
                    endLine == null ? line : endLine,
                    endColumn == null ? (column == null ? 2 : column + 1) : endColumn);
        if (fromOffset != null && toOffset != null && toOffset <= fromOffset) {
            toOffset = Math.min(source.length(), fromOffset + 1);
        }
        return new CDiagnostic(severity(result.path("level").asString("warning")),
                message(result),
                line,
                column,
                endLine,
                endColumn,
                fromOffset,
                toOffset,
                emptyToNull(result.path("ruleId").asString("")));
    }

    private static JsonNode firstRegion(JsonNode result) {
        var locations = result.path("locations");
        if (!locations.isArray() || locations.isEmpty()) {
            return result.path("physicalLocation").path("region");
        }
        return locations.get(0).path("physicalLocation").path("region");
    }

    private static String message(JsonNode result) {
        var text = result.path("message").path("text").asString("");
        if (!text.isBlank()) {
            return text;
        }
        return result.path("message").path("markdown").asString("");
    }

    private static CDiagnosticSeverity severity(String level) {
        return switch (level == null ? "" : level) {
            case "error" -> CDiagnosticSeverity.ERROR;
            case "warning" -> CDiagnosticSeverity.WARNING;
            default -> CDiagnosticSeverity.INFO;
        };
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int offsetFor(String source, int line, int column) {
        var currentLine = 1;
        var offset = 0;
        while (offset < source.length() && currentLine < line) {
            if (source.charAt(offset) == '\n') {
                currentLine++;
            }
            offset++;
        }
        var lineStart = offset;
        while (offset < source.length() && source.charAt(offset) != '\n') {
            offset++;
        }
        var lineEnd = offset;
        return Math.min(lineEnd, lineStart + Math.max(0, column - 1));
    }
}
