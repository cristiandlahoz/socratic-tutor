package com.wornux.documentingest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.documentingest.EditableSegmentVm;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentSegmentEditorListTest {

  @Test
  void setSegments_renders_docling_provenance_metadata() {
    var editor = new DocumentSegmentEditorList();

    editor.setSegments(
        List.of(
            new EditableSegmentVm(
                UUID.randomUUID(),
                1,
                "Reporte / Hallazgos",
                "Contenido del segmento",
                false,
                false,
                22,
                8,
                2,
                List.of(2, 3),
                List.of("Tabla 1"),
                List.of("#/tables/0"),
                "Contenido raw",
                "DOCLING_HYBRID")));

    var text =
        editor
            .getContent()
            .getChildren()
            .flatMap(component -> component.getElement().getTextRecursively().lines())
            .reduce("", (left, right) -> left + "\n" + right);

    assertThat(text).contains("paginas [2, 3]");
    assertThat(text).contains("captions: Tabla 1");
    assertThat(text).contains("refs: #/tables/0");
  }
}
