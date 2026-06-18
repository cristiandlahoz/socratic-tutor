package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.wornux.dtos.chat.questions.StudentQuestionSet;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

class SpringAiJsonSchemaGeneratorTest {

    private static final Logger log = LoggerFactory.getLogger(SpringAiJsonSchemaGeneratorTest.class);

    @Test
    void swaggerArraySchemaAddsItemBounds() {
        var schema = JsonSchemaGenerator.generateForType(StudentQuestionSet.class);
        log.info("Swagger schema:\n{}", schema);
        assertThat(schema).isNotNull();
    }

    @Test
    void jakartaSizeDoesNotAddItemBounds() {
        var schema = JsonSchemaGenerator.generateForType(ValidationQuestionSet.class);

        log.info("Jakarta validation schema:\n{}", schema);

        assertThat(schema).doesNotContain("minItems", "maxItems");
    }

    record ValidationQuestionSet(@Size(min = 1, max = 3) List<ValidationQuestion> questions) {}

    record ValidationQuestion(String question, @Size(min = 1, max = 4) List<String> options) {}
}
