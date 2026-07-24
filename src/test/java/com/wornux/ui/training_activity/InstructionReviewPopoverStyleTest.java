package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class InstructionReviewPopoverStyleTest {

    @Test
    void popoverUsesAuraOpaqueSurfaceInsteadOfTheTranslucentContainerSurface() throws Exception {
        var stylesheet = new ClassPathResource("META-INF/resources/styles/instruction-review-popover.css")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(stylesheet).contains("background: var(--aura-surface-color-solid);");
        assertThat(stylesheet).doesNotContain("background: var(--vaadin-background-container-strong);");
        assertThat(stylesheet).doesNotContain("--lumo-");
    }
}
