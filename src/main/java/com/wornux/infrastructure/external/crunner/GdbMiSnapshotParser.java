package com.wornux.infrastructure.external.crunner;

import com.wornux.application.crunner.CDebugSnapshot;
import com.wornux.application.crunner.CDebugVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GdbMiSnapshotParser {

  private static final Pattern FRAME_PATTERN = Pattern.compile("frame=\\{([^}]*)}");
  private static final Pattern VAR_PATTERN = Pattern.compile("\\{([^{}]*)}");

  public List<CDebugSnapshot> parse(String miOutput, int maxSnapshots, int maxOutputBytes) {
    var snapshots = new ArrayList<CDebugSnapshot>();
    var stdout = new StringBuilder();
    var currentLine = (Integer) null;
    var currentFunction = "";
    var currentReason = "";
    var terminated = false;

    for (var rawLine : safeLines(miOutput)) {
      var line = rawLine.trim();
      if (line.isBlank()) {
        continue;
      }
      if (line.startsWith("@") || line.startsWith("~")) {
        appendCapped(stdout, unquotePayload(line), maxOutputBytes);
        continue;
      }
      if (line.startsWith("*stopped")) {
        currentReason = attributeMap(line).getOrDefault("reason", "stopped");
        terminated = currentReason.startsWith("exited");
        var frame = firstFrame(line);
        if (!frame.isEmpty()) {
          currentLine = integerOrNull(frame.get("line"));
          currentFunction = frame.getOrDefault("func", currentFunction);
        } else {
          currentLine = null;
        }
        if (terminated) {
          snapshots.add(
              snapshot(
                  snapshots.size(),
                  currentLine,
                  currentFunction,
                  stdout,
                  List.of(),
                  true,
                  currentReason));
        }
        continue;
      }
      if (line.startsWith("^done,stack=")) {
        var topFrame = firstFrame(line);
        if (!topFrame.isEmpty()) {
          currentLine = integerOrNull(topFrame.get("line"));
          currentFunction = topFrame.getOrDefault("func", currentFunction);
        }
        continue;
      }
      if (line.startsWith("^done,variables=") && currentLine != null && snapshots.size() < maxSnapshots) {
        snapshots.add(
            snapshot(
                snapshots.size(),
                currentLine,
                currentFunction,
                stdout,
                parseVariables(line),
                terminated,
                currentReason));
      }
    }
    return snapshots.size() <= maxSnapshots ? snapshots : snapshots.subList(0, maxSnapshots);
  }

  private static CDebugSnapshot snapshot(
      int index,
      Integer line,
      String functionName,
      StringBuilder stdout,
      List<CDebugVariable> locals,
      boolean terminated,
      String reason) {
    return new CDebugSnapshot(
        index, line, functionName, stdout.toString(), locals, terminated, reason);
  }

  private static List<String> safeLines(String value) {
    return value == null ? List.of() : value.lines().toList();
  }

  private static Map<String, String> firstFrame(String line) {
    var matcher = FRAME_PATTERN.matcher(line);
    return matcher.find() ? attributeMap(matcher.group(1)) : Map.of();
  }

  private static List<CDebugVariable> parseVariables(String line) {
    var variables = new ArrayList<CDebugVariable>();
    var matcher = VAR_PATTERN.matcher(line);
    while (matcher.find()) {
      var values = attributeMap(matcher.group(1));
      var name = values.get("name");
      if (name == null || name.isBlank()) {
        continue;
      }
      var value = values.getOrDefault("value", "");
      variables.add(
          new CDebugVariable(values.getOrDefault("type", inferType(value)), name, value, "local"));
    }
    return variables;
  }

  private static Map<String, String> attributeMap(String value) {
    var attributes = new LinkedHashMap<String, String>();
    var current = value == null ? "" : value;
    var index = 0;
    while (index < current.length()) {
      var equals = current.indexOf('=', index);
      if (equals < 0) {
        break;
      }
      var keyStart = current.lastIndexOf(',', equals);
      var key = current.substring(keyStart < index ? index : keyStart + 1, equals).trim();
      var valueStart = equals + 1;
      if (valueStart < current.length() && current.charAt(valueStart) == '"') {
        var end = findQuotedEnd(current, valueStart + 1);
        attributes.put(key, unescape(current.substring(valueStart + 1, end)));
        index = Math.min(current.length(), end + 2);
      } else {
        var comma = current.indexOf(',', valueStart);
        var end = comma < 0 ? current.length() : comma;
        attributes.put(key, current.substring(valueStart, end).trim());
        index = end + 1;
      }
    }
    return attributes;
  }

  private static int findQuotedEnd(String value, int start) {
    var escaped = false;
    for (int i = start; i < value.length(); i++) {
      var ch = value.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (ch == '\\') {
        escaped = true;
      } else if (ch == '"') {
        return i;
      }
    }
    return value.length();
  }

  private static String unquotePayload(String line) {
    var quote = line.indexOf('"');
    if (quote < 0) {
      return "";
    }
    var end = findQuotedEnd(line, quote + 1);
    return unescape(line.substring(quote + 1, end));
  }

  private static String unescape(String value) {
    return value
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\");
  }

  private static void appendCapped(StringBuilder target, String value, int maxBytes) {
    if (value.isEmpty() || target.length() >= maxBytes) {
      return;
    }
    var remaining = Math.max(0, maxBytes - target.length());
    target.append(value, 0, Math.min(value.length(), remaining));
  }

  private static String inferType(String value) {
    if (value == null || value.isBlank()) {
      return "?";
    }
    if (value.matches("-?\\d+")) {
      return "int";
    }
    if (value.startsWith("0x")) {
      return "ptr";
    }
    return "?";
  }

  private static Integer integerOrNull(String value) {
    try {
      return value == null || value.isBlank() ? null : Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
