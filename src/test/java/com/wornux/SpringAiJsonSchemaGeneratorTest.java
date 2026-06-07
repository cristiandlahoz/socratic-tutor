package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

class SpringAiJsonSchemaGeneratorTest {

  private static final Logger log = LoggerFactory.getLogger(SpringAiJsonSchemaGeneratorTest.class);

  @Test
  void swaggerArraySchemaAddsItemBounds() {
    var schema = JsonSchemaGenerator.generateForType(SwaggerQuestionSet.class);

    log.info("Swagger schema:\n{}", schema);

    assertThat(schema).contains("\"minItems\" : 1", "\"maxItems\" : 3");
  }

  @Test
  void jakartaSizeDoesNotAddItemBounds() {
    var schema = JsonSchemaGenerator.generateForType(ValidationQuestionSet.class);

    log.info("Jakarta validation schema:\n{}", schema);

    assertThat(schema).doesNotContain("minItems", "maxItems");
  }

  record SwaggerQuestionSet(
      @ArraySchema(
              minItems = 1,
              maxItems = 3,
              schema = @Schema(implementation = SwaggerQuestion.class))
          List<SwaggerQuestion> questions) {}

  record SwaggerQuestion(
      String question,
      @ArraySchema(minItems = 1, maxItems = 4, schema = @Schema(implementation = String.class))
          List<String> options) {}

  record ValidationQuestionSet(@Size(min = 1, max = 3) List<ValidationQuestion> questions) {}

  record ValidationQuestion(String question, @Size(min = 1, max = 4) List<String> options) {}
}
