package com.wornux.services.document;

public record StartIngestionCommand(String originalFilename, String mimeType, byte[] content) {}
