package com.wornux.services.crunner;

public record CDebugRequest(String source, String standard, String filename, String stdin) {

    public CDebugRequest {
        var sourceRequest = new CSourceRequest(source, standard, filename);
        source = sourceRequest.source();
        standard = sourceRequest.standard();
        filename = sourceRequest.filename();
        stdin = parseStdin(stdin);
    }

    private static String parseStdin(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var entries = new java.util.ArrayList<String>();
        for (var index = 0; index < value.length();) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index == value.length()) {
                break;
            }
            var quote = value.charAt(index++);
            if (quote != '\'' && quote != '"') {
                throw new IllegalArgumentException("Cada entrada de stdin debe estar entre comillas simples o dobles.");
            }
            var end = value.indexOf(quote, index);
            if (end < 0) {
                throw new IllegalArgumentException("Falta cerrar una entrada de stdin con " + quote + ".");
            }
            entries.add(value.substring(index, end));
            index = end + 1;
        }
        return String.join("\n", entries) + "\n";
    }

    public static CDebugRequest from(CSourceRequest request, String stdin) {
        var safeRequest = request == null ? new CSourceRequest("", null, null) : request;
        return new CDebugRequest(safeRequest.source(), safeRequest.standard(), safeRequest.filename(), stdin);
    }
}
