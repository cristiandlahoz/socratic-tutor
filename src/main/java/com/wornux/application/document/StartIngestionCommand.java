package com.wornux.application.document;

import java.util.UUID;

public record StartIngestionCommand(
    UUID clientId, String originalFilename, String mimeType, byte[] content) {}
