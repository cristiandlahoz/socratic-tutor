package com.wornux.dtos.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import org.junit.jupiter.api.Test;

class GuardCheckTest {

    @Test
    void acceptsEachValidActionShape() {
        assertThat(new GuardCheck(GuardDecision.SAFE, GuardAction.ALLOW, "", "").action())
                .isEqualTo(GuardAction.ALLOW);
        assertThat(new GuardCheck(
                GuardDecision.NOT_SAFE, GuardAction.STEER, "Explícame el concepto.", "").safeUserMessage())
                .isEqualTo("Explícame el concepto.");
        assertThat(new GuardCheck(
                GuardDecision.IMPERSONATION, GuardAction.SHORT_CIRCUIT, "", "Comparte tu duda del curso.")
                .directResponse())
                .isEqualTo("Comparte tu duda del curso.");
    }

    @Test
    void rejectsInvalidActionTextCombinations() {
        assertThatThrownBy(() -> new GuardCheck(
                GuardDecision.SAFE, GuardAction.ALLOW, "rewritten", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuardCheck(
                GuardDecision.NOT_SAFE, GuardAction.STEER, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuardCheck(
                GuardDecision.NOT_SAFE, GuardAction.SHORT_CIRCUIT, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuardCheck(
                GuardDecision.SAFE, GuardAction.SHORT_CIRCUIT, "", "blocked"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
