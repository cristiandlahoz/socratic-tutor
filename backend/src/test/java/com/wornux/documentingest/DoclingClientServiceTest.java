package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.chunk.response.ExportDocumentResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoclingClientServiceTest {

  @Test
  void buildHybridChunkRequest_uses_configured_local_tokenizer_options() {
    var properties = new DocumentIngestionProperties();
    properties.getChunking().getHybrid().setMaxTokens(768);
    properties.getChunking().getHybrid().setTokenizer("/opt/tokenizers/custom-qwen");
    properties.getChunking().getHybrid().setMergePeers(false);
    properties.getChunking().getHybrid().setUseMarkdownTables(false);
    properties.getChunking().getHybrid().setIncludeRawText(false);

    var request =
        new DoclingClientService(properties)
            .buildHybridChunkRequest("report.pdf", "%PDF".getBytes(StandardCharsets.UTF_8));

    assertThat(request.isIncludeConvertedDoc()).isTrue();
    assertThat(request.getChunkingOptions().getMaxTokens()).isEqualTo(768);
    assertThat(request.getChunkingOptions().getTokenizer()).isEqualTo("/opt/tokenizers/custom-qwen");
    assertThat(request.getChunkingOptions().getMergePeers()).isFalse();
    assertThat(request.getChunkingOptions().isUseMarkdownTables()).isFalse();
    assertThat(request.getChunkingOptions().isIncludeRawText()).isFalse();
  }

  @Test
  void ingestionProperties_default_to_vendored_docling_tokenizer_path() {
    var properties = new DocumentIngestionProperties();

    assertThat(properties.getChunking().getHybrid().getTokenizer())
        .isEqualTo("/opt/tokenizers/qwen3-0.6b-tokenizer");
  }

  @Test
  void mapChunkResponse_extracts_markdown_and_docling_chunk_metadata() {
    var response =
        ChunkDocumentResponse.builder()
            .document(
                ai.docling.serve.api.chunk.response.Document.builder()
                    .content(
                        ExportDocumentResponse.builder()
                            .markdownContent("# Reporte\n\nContenido")
                            .build())
                    .status("success")
                    .build())
            .chunk(
                Chunk.builder()
                    .chunkIndex(0)
                    .text("Reporte\n\nHallazgo importante.")
                    .rawText("Hallazgo importante.")
                    .numTokens(12)
                    .headings(List.of("Reporte", "Hallazgos"))
                    .captions(List.of("Tabla 1"))
                    .docItems(List.of("#/tables/0"))
                    .pageNumbers(List.of(3, 4))
                    .build())
            .build();

    var result = DoclingClientService.mapChunkResponse(response);

    assertThat(result.markdown()).contains("# Reporte");
    assertThat(result.pageCount()).isEqualTo(4);
    assertThat(result.segments()).hasSize(1);
    assertThat(result.segments().getFirst().headingPath()).isEqualTo("Reporte / Hallazgos");
    assertThat(result.segments().getFirst().tokenCount()).isEqualTo(12);
    assertThat(result.segments().getFirst().pageNumber()).isEqualTo(3);
    assertThat(result.segments().getFirst().pageNumbers()).containsExactly(3, 4);
    assertThat(result.segments().getFirst().captions()).containsExactly("Tabla 1");
    assertThat(result.segments().getFirst().docItems()).containsExactly("#/tables/0");
    assertThat(result.segments().getFirst().rawText()).isEqualTo("Hallazgo importante.");
  }
}
