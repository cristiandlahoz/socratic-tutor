package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TrainingActivityGridStyleTest {

    @Test
    void selectedTrainingActivityRowsUseTheAuraSelectedRowStyleProperty() throws Exception {
        var stylesheet = stylesheet("META-INF/resources/styles/training-activity-view.css");

        assertThat(stylesheet).contains(".training-activity-main-grid");
        assertThat(stylesheet).contains("--vaadin-grid-row-selected-background-color");
        assertThat(stylesheet).doesNotContain("--lumo-");
    }

    private String stylesheet(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
