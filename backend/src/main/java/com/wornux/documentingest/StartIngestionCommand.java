package com.wornux.documentingest;

import java.util.UUID;

public record StartIngestionCommand(
    UUID clientId, String originalFilename, String mimeType, byte[] content) {}
