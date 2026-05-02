package com.wornux.domain.profile;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum TopicKey {
  VARIABLES("variable", "variables", "dato", "datos", "type", "tipo"),
  CONDITIONALS("if", "switch", "condicional", "condition", "selección"),
  LOOPS(
      "for",
      "while",
      "do while",
      "bucle",
      "loop",
      "iteración",
      "iteración",
      "counter",
      "contador",
      "accumulator",
      "acumulador"),
  FUNCTIONS("función", "función", "function", "parameter", "parámetro", "parámetro", "return"),
  ARRAYS("array", "arreglo", "vector", "indice", "índice", "index"),
  STRINGS("string", "cadena", "char", "texto"),
  MATRICES("matriz", "matrices", "bidimensional", "multidimensional");

  private final List<String> hints;

  TopicKey(String... hints) {
    this.hints = List.of(hints);
  }

  public static List<TopicKey> detectTopics(String text) {
    var normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(topic -> topic.hints.stream().anyMatch(normalized::contains))
        .toList();
  }
}
