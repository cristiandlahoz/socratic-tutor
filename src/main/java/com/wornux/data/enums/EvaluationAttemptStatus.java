package com.wornux.data.enums;

public enum EvaluationAttemptStatus {
  READY_TO_RUN,
  RUNNING,
  COMPLETED,

  @Deprecated(since = "evaluation-lifecycle-and-diagnostic-flow")
  IN_PROGRESS,

  @Deprecated(since = "evaluation-lifecycle-and-diagnostic-flow")
  SUBMITTED,

  @Deprecated(since = "evaluation-lifecycle-and-diagnostic-flow")
  GRADED
}
