package com.wornux.dtos.chat;

import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;

public record GuardCheck(GuardDecision decision, GuardAction action) {}
