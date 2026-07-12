package com.wornux.dtos.chat;

import java.util.Objects;

import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;

public record GuardCheck(GuardDecision decision, GuardAction action, String safeUserMessage, String directResponse) {

    public GuardCheck {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(safeUserMessage, "safeUserMessage must not be null");
        Objects.requireNonNull(directResponse, "directResponse must not be null");
        safeUserMessage = safeUserMessage.trim();
        directResponse = directResponse.trim();

        switch (action) {
            case ALLOW -> {
                if (decision != GuardDecision.SAFE || !safeUserMessage.isEmpty() || !directResponse.isEmpty()) {
                    throw new IllegalArgumentException("ALLOW requires SAFE and empty text fields");
                }
            }
            case STEER -> {
                if (decision != GuardDecision.NOT_SAFE || safeUserMessage.isEmpty() || !directResponse.isEmpty()) {
                    throw new IllegalArgumentException(
                        "STEER requires NOT_SAFE, a safeUserMessage, and an empty directResponse");
                }
            }
            case SHORT_CIRCUIT -> {
                if (decision == GuardDecision.SAFE || !safeUserMessage.isEmpty() || directResponse.isEmpty()) {
                    throw new IllegalArgumentException(
                        "SHORT_CIRCUIT requires an unsafe decision, an empty safeUserMessage, and a directResponse");
                }
            }
        }
    }
}
