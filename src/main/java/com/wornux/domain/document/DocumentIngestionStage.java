package com.wornux.domain.document;

public enum DocumentIngestionStage {
  UPLOAD,
  DOCLING_CONVERT,
  SEGMENT_BUILD,
  REVIEW,
  EMBED,
  COMPLETE,
  FAILED
}
