package com.wornux.infrastructure.external.crunner;

import com.wornux.services.crunner.CDebugSnapshot;
import com.wornux.services.crunner.CDebugVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GdbDebugDumpParser {

  private static final Pattern FRAME_PATTERN = Pattern.compile("frame=\\{([^}]*)}");
  private static final String STDOUT_BEGIN = "__C_STDOUT_BEGIN__";
  private static final String STDOUT_END = "__C_STDOUT_END__";

  public List<CDebugSnapshot> parse(String miOutput, int maxSnapshots, int maxOutputBytes) {
    var snapshots = new ArrayList<CDebugSnapshot>();
    var stdout = new StringBuilder();
    var stdoutCapture = new StringBuilder();
    var currentLine = (Integer) null;
    var currentFunction = "";
    var currentReason = "";
    var terminated = false;
    var capturingStdout = false;

    for (var rawLine : safeLines(miOutput)) {
      var line = rawLine.trim();
      if (line.isBlank()) {
        continue;
      }
      if (line.startsWith("~")) {
        var payload = unquotePayload(line);
        var stdoutResult =
            processStdoutMarkers(payload, capturingStdout, stdoutCapture, stdout, maxOutputBytes, false);
        capturingStdout = stdoutResult.capturing();
        if (stdoutResult.consumed()) {
          continue;
        }
        continue;
      }
      if (line.startsWith("@")) {
        appendCapped(stdout, unquotePayload(line), maxOutputBytes);
        continue;
      }
      var stdoutResult =
          processStdoutMarkers(rawLine, capturingStdout, stdoutCapture, stdout, maxOutputBytes, true);
      capturingStdout = stdoutResult.capturing();
      if (stdoutResult.consumed()) {
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

  private static StdoutMarkerResult processStdoutMarkers(
      String text,
      boolean capturingStdout,
      StringBuilder stdoutCapture,
      StringBuilder stdout,
      int maxOutputBytes,
      boolean appendLineBreakWhenOpen) {
    var current = text == null ? "" : text;
    var capturing = capturingStdout;
    var consumed = false;

    while (true) {
      if (!capturing) {
        var beginIndex = current.indexOf(STDOUT_BEGIN);
        if (beginIndex < 0) {
          return new StdoutMarkerResult(false, consumed);
        }
        capturing = true;
        consumed = true;
        stdoutCapture.setLength(0);
        current = current.substring(beginIndex + STDOUT_BEGIN.length());
        if (current.startsWith("\n")) {
          current = current.substring(1);
        }
      }

      var endIndex = current.indexOf(STDOUT_END);
      if (endIndex >= 0) {
        appendCapped(stdoutCapture, current.substring(0, endIndex), maxOutputBytes);
        stdout.setLength(0);
        appendCapped(stdout, stdoutCapture.toString(), maxOutputBytes);
        stdoutCapture.setLength(0);
        capturing = false;
        consumed = true;
        current = current.substring(endIndex + STDOUT_END.length());
        if (current.isEmpty()) {
          return new StdoutMarkerResult(false, true);
        }
        continue;
      }

      if (!current.isEmpty()) {
        appendCapped(
            stdoutCapture,
            appendLineBreakWhenOpen ? "%s\n".formatted(current) : current,
            maxOutputBytes);
      }
      return new StdoutMarkerResult(true, true);
    }
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
    for (var variableObject : variableObjects(line)) {
      var values = attributeMap(variableObject);
      var name = values.get("name");
      if (name == null || name.isBlank()) {
        continue;
      }
      var value = values.getOrDefault("value", "");
      variables.add(new CDebugVariable(name, value, "local"));
    }
    return variables;
  }

  private static List<String> variableObjects(String line) {
    var objects = new ArrayList<String>();
    var variablesStart = line.indexOf("variables=[");
    if (variablesStart < 0) {
      return objects;
    }
    var index = variablesStart + "variables=[".length();
    while (index < line.length()) {
      if (line.charAt(index) != '{') {
        index++;
        continue;
      }
      var end = findBalancedBraceEnd(line, index);
      if (end <= index) {
        break;
      }
      objects.add(line.substring(index + 1, end));
      index = end + 1;
    }
    return objects;
  }

  private static int findBalancedBraceEnd(String value, int start) {
    var depth = 0;
    var quoted = false;
    var escaped = false;
    for (int i = start; i < value.length(); i++) {
      var ch = value.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
        continue;
      }
      if (ch == '"') {
        quoted = !quoted;
        continue;
      }
      if (quoted) {
        continue;
      }
      if (ch == '{') {
        depth++;
      } else if (ch == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
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

  private static Integer integerOrNull(String value) {
    try {
      return value == null || value.isBlank() ? null : Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private record StdoutMarkerResult(boolean capturing, boolean consumed) {}
}
