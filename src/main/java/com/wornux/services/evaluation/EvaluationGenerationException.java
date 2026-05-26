package com.wornux.services.evaluation;

public class EvaluationGenerationException extends RuntimeException {

  public EvaluationGenerationException(String message) {
    super(message);
  }

  public EvaluationGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
