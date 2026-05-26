package com.wornux.ai.policy;

import com.wornux.domain.chat.GuardCheck;
import com.wornux.data.enums.GuardDecision;

public interface GuardrailEngine {
  GuardDecision classify(GuardCheck guardCheck);
}
