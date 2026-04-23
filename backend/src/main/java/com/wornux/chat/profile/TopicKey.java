package com.wornux.chat.profile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum TopicKey {
  VARIABLES("variable", "variables", "dato", "datos", "type", "tipo"),
  CONDITIONALS("if", "switch", "condicional", "condition", "seleccion"),
  LOOPS(
      "for",
      "while",
      "do while",
      "bucle",
      "loop",
      "iteracion",
      "iteración",
      "counter",
      "contador",
      "accumulator",
      "acumulador"),
  FUNCTIONS("funcion", "función", "function", "parameter", "parametro", "parámetro", "return"),
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
