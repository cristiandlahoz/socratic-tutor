package com.wornux.services.crunner;

public record CDebugRequest(String source, String standard, String filename, String stdin) {

    public CDebugRequest {
        var sourceRequest = new CSourceRequest(source, standard, filename);
        source = sourceRequest.source();
        standard = sourceRequest.standard();
        filename = sourceRequest.filename();
        stdin = stdin == null ? "" : stdin;
    }

    public static CDebugRequest from(CSourceRequest request, String stdin) {
        var safeRequest = request == null ? new CSourceRequest("", null, null) : request;
        return new CDebugRequest(safeRequest.source(), safeRequest.standard(), safeRequest.filename(), stdin);
    }
}
