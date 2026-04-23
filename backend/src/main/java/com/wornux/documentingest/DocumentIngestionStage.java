package com.wornux.documentingest;

public enum DocumentIngestionStage {
  UPLOAD,
  DOCLING_CONVERT,
  SEGMENT_BUILD,
  REVIEW,
  EMBED,
  COMPLETE,
  FAILED
}
