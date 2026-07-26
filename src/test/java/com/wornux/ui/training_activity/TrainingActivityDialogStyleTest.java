package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TrainingActivityDialogStyleTest {

    @Test
    void dialogInstructionReviewSpacingUsesTheNamedAuraBaseStyle() throws Exception {
        var stylesheet = new ClassPathResource("META-INF/resources/styles/training-activity-view.css")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(stylesheet).contains(".training-activity-dialog-instructions {");
        assertThat(stylesheet).contains("flex-shrink: 0;");
        assertThat(stylesheet).contains("margin-block-end: var(--vaadin-padding-m, 1rem);");
        assertThat(stylesheet).doesNotContain("--lumo-");
    }
}
