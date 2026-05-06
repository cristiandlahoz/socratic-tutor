package com.wornux.ai.policy;

import com.wornux.domain.chat.GuardCheck;
import com.wornux.domain.chat.GuardDecision;

public interface GuardrailEngine {
  GuardDecision classify(GuardCheck guardCheck);
}
