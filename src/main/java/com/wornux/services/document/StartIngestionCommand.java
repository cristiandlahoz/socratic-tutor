package com.wornux.services.document;

import java.util.UUID;

public record StartIngestionCommand(UUID clientId, String originalFilename, String mimeType, byte[] content) {}
