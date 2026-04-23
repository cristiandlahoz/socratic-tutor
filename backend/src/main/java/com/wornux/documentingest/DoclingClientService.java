package com.wornux.documentingest;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.chunk.response.Document;
import ai.docling.serve.api.chunk.response.ExportDocumentResponse;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DoclingClientService {

  private static final Logger log = LoggerFactory.getLogger(DoclingClientService.class);
  private final DocumentIngestionProperties properties;

  public DoclingClientService(DocumentIngestionProperties properties) {
    this.properties = properties;
    var hybrid = properties.getChunking().getHybrid();
    log.info(
        "docling_chunking_config base_url={} tokenizer={} max_tokens={} merge_peers={} use_markdown_tables={} include_raw_text={}",
        properties.getDoclingBaseUrl(),
        hybrid.getTokenizer(),
        hybrid.getMaxTokens(),
        hybrid.getMergePeers(),
        hybrid.isUseMarkdownTables(),
        hybrid.isIncludeRawText());
  }

  public DoclingConversionResult convertPdfToMarkdownAndChunks(String filename, byte[] content) {
    try {
      DoclingServeApi api = createApi();
      ChunkDocumentResponse chunkResponse =
          api.chunkSourceWithHybridChunker(buildHybridChunkRequest(filename, content));
      DoclingConversionResult chunkResult = mapChunkResponse(chunkResponse);

      String chunkMarkdown = chunkResult.markdown();
      boolean chunkMarkdownPresent = !chunkMarkdown.isBlank();
      String convertMarkdown = "";
      if (!chunkMarkdownPresent) {
        convertMarkdown = markdown(api.convertSource(buildConvertMarkdownRequest(filename, content)));
      }
      boolean convertMarkdownPresent = !convertMarkdown.isBlank();
      String finalMarkdown = chunkMarkdownPresent ? chunkMarkdown : convertMarkdown;

      log.info(
          "docling_ingest_trace filename={} chunks_count={} chunk_docs_count={} chunk_md_present={} convert_md_present={} final_md_length={}",
          filename,
          chunkResult.segments().size(),
          chunkDocumentCount(chunkResponse),
          chunkMarkdownPresent,
          convertMarkdownPresent,
          finalMarkdown.length());

      if (finalMarkdown.isBlank() && !chunkResult.segments().isEmpty()) {
        throw new DocumentIngestionException(
            "Docling devolvio segmentos pero no contenido markdown util.");
      }

      return new DoclingConversionResult(
          finalMarkdown, chunkResult.pageCount(), chunkResult.segments());
    } catch (DocumentIngestionException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new DocumentIngestionException(
          "Docling no pudo crear segmentos para este PDF. Revisa que Docling Serve este disponible e intenta de nuevo.",
          exception);
    }
  }

  DoclingServeApi createApi() {
    return DoclingServeApi.builder()
        .baseUrl(properties.getDoclingBaseUrl())
        .connectTimeout(properties.getDoclingConnectTimeout())
        .readTimeout(properties.getDoclingReadTimeout())
        .build();
  }

  HybridChunkDocumentRequest buildHybridChunkRequest(String filename, byte[] content) {
    var hybrid = properties.getChunking().getHybrid();
    return HybridChunkDocumentRequest.builder()
        .source(
            FileSource.builder()
                .filename(filename)
                .base64String(Base64.getEncoder().encodeToString(content))
                .build())
        .options(
            ConvertDocumentOptions.builder()
                .toFormat(OutputFormat.MARKDOWN)
                .imageExportMode(ImageRefMode.PLACEHOLDER)
                .includeImages(false)
                .build())
        .chunkingOptions(
            HybridChunkerOptions.builder()
                .maxTokens(hybrid.getMaxTokens())
                .tokenizer(hybrid.getTokenizer())
                .mergePeers(hybrid.getMergePeers())
                .useMarkdownTables(hybrid.isUseMarkdownTables())
                .includeRawText(hybrid.isIncludeRawText())
                .build())
        .target(InBodyTarget.builder().build())
        .includeConvertedDoc(true)
        .build();
  }

  ConvertDocumentRequest buildConvertMarkdownRequest(String filename, byte[] content) {
    return ConvertDocumentRequest.builder()
        .source(
            FileSource.builder()
                .filename(filename)
                .base64String(Base64.getEncoder().encodeToString(content))
                .build())
        .options(
            ConvertDocumentOptions.builder()
                .toFormat(OutputFormat.MARKDOWN)
                .imageExportMode(ImageRefMode.PLACEHOLDER)
                .includeImages(false)
                .build())
        .target(InBodyTarget.builder().build())
        .build();
  }

  static DoclingConversionResult mapChunkResponse(ChunkDocumentResponse response) {
    var chunks =
        response == null || response.getChunks() == null
            ? List.<Chunk>of()
            : response.getChunks();
    var segments = new ArrayList<DoclingSegmentDraft>();
    int ordinal = 1;
    for (Chunk chunk : chunks) {
      String text = chunk.getText() == null ? "" : chunk.getText().trim();
      if (text.isBlank()) {
        continue;
      }
      var pageNumbers = compactIntegers(chunk.getPageNumbers());
      var captions = compactStrings(chunk.getCaptions());
      var docItems = compactStrings(chunk.getDocItems());
      segments.add(
          new DoclingSegmentDraft(
              ordinal++,
              headingPath(chunk.getHeadings()),
              text,
              chunk.getNumTokens(),
              pageNumbers.isEmpty() ? null : pageNumbers.getFirst(),
              pageNumbers,
              captions,
              docItems,
              chunk.getRawText()));
    }
    return new DoclingConversionResult(
        markdown(response), pageCount(segments), List.copyOf(segments));
  }

  private static String markdown(ChunkDocumentResponse response) {
    if (response == null || response.getDocuments() == null) {
      return "";
    }
    return response.getDocuments().stream()
        .filter(Objects::nonNull)
        .map(Document::getContent)
        .filter(Objects::nonNull)
        .map(ExportDocumentResponse::getMarkdownContent)
        .filter(markdown -> markdown != null && !markdown.isBlank())
        .findFirst()
        .orElse("");
  }

  private static String markdown(ConvertDocumentResponse response) {
    if (!(response instanceof InBodyConvertDocumentResponse inBodyResponse)
        || inBodyResponse.getDocument() == null) {
      return "";
    }
    String markdown = inBodyResponse.getDocument().getMarkdownContent();
    return markdown == null ? "" : markdown.trim();
  }

  private static int chunkDocumentCount(ChunkDocumentResponse response) {
    if (response == null || response.getDocuments() == null) {
      return 0;
    }
    return response.getDocuments().size();
  }

  private static Integer pageCount(List<DoclingSegmentDraft> segments) {
    return segments.stream()
        .flatMap(segment -> segment.pageNumbers().stream())
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private static String headingPath(List<String> headings) {
    var compact = compactStrings(headings);
    return compact.isEmpty() ? "Documento" : String.join(" / ", compact);
  }

  private static List<String> compactStrings(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<Integer> compactIntegers(List<Integer> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().filter(Objects::nonNull).distinct().toList();
  }
}
