package com.wornux.domain.document;

public class DocumentIngestionException extends RuntimeException {

  public DocumentIngestionException(String message) {
    super(message);
  }

  public DocumentIngestionException(String message, Throwable cause) {
    super(message, cause);
  }
}
