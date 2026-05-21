package com.wornux.ai.policy;

public interface ToolExecutionPolicy {
  boolean canRun(String toolName, String prompt);
}
