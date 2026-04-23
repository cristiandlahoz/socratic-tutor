package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentSegmentationServiceTest {

  @Test
  void segmentMarkdown_preserves_headings_and_splits_long_blocks_with_overlap() {
    var properties = new DocumentIngestionProperties();
    properties.setSegmentMaxChars(120);
    properties.setSegmentOverlapChars(20);
    var service = new DocumentSegmentationService(properties);

    String markdown =
        """
        # Introduccion

        Este es un parrafo largo que deberia dividirse en multiples segmentos porque excede el limite configurado.
        Sigue hablando del mismo tema con suficiente longitud para probar el traslape entre segmentos.

        ## Detalle

        Otro bloque separado con contexto adicional y una segunda seccion.
        """;

    var segments = service.segmentMarkdown(markdown);

    assertThat(segments).hasSizeGreaterThanOrEqualTo(3);
    assertThat(segments.getFirst().headingPath()).isEqualTo("Introduccion");
    assertThat(segments.stream().map(DocumentSegmentationService.SegmentDraft::headingPath))
        .contains("Introduccion / Detalle");
    assertThat(segments).allMatch(segment -> segment.content().length() <= 120);
  }
}
