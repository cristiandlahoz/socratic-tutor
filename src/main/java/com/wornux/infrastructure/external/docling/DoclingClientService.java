package com.wornux.infrastructure.external.docling;

import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.wornux.config.ApplicationProperties;
import com.wornux.dtos.document.*;
import io.arconia.ai.document.docling.DoclingDocumentParser;
import io.arconia.ai.document.docling.DoclingDocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class DoclingClientService {

    private static final Logger log = LoggerFactory.getLogger(DoclingClientService.class);
    private static final String CAPTIONS = "captions";
    private static final String CHUNKER = "chunker";
    private static final String DOC_ITEMS = "docItems";
    private static final String DOCLING_HYBRID = "DOCLING_HYBRID";
    private static final String HEADING_PATH = "headingPath";
    private static final String ORDINAL = "ordinal";
    private static final String PAGE_NUMBER = "pageNumber";
    private static final String PAGE_NUMBERS = "pageNumbers";
    private static final String RAW_TEXT = "rawText";
    private static final String TOKEN_COUNT = "tokenCount";
    private final DoclingServeApi doclingServeApi;
    private final ApplicationProperties.DocumentIngest properties;

    public DoclingClientService(DoclingServeApi doclingServeApi, ApplicationProperties.DocumentIngest properties) {
        this.doclingServeApi = doclingServeApi;
        this.properties = properties;
        var hybrid = properties.getChunking().getHybrid();
        log.info(
            """
            docling_chunking_config provider=arconia max_tokens={} tokenizer={} merge_peers={}\
             use_markdown_tables={} include_raw_text={}\
            """,
            hybrid.getMaxTokens(),
            hybrid.getTokenizer(),
            hybrid.getMergePeers(),
            hybrid.isUseMarkdownTables(),
            hybrid.isIncludeRawText());
    }

    public DoclingConversionResult convertPdfToMarkdownAndChunks(String filename, byte[] content) {
        try {
            List<org.springframework.ai.document.Document> chunkDocuments = readChunks(filename, content);
            List<DoclingSegmentDraft> segments = mapChunkDocuments(chunkDocuments);
            logChunkStats(filename, segments);
            String convertMarkdown =
                    markdown(doclingServeApi.convertSource(buildConvertMarkdownRequest(filename, content)));
            boolean convertMarkdownPresent = !convertMarkdown.isBlank();

            log.info(
                "docling_ingest_trace filename={} chunks_count={} convert_md_present={} final_md_length={}",
                filename,
                segments.size(),
                convertMarkdownPresent,
                convertMarkdown.length());

            if (convertMarkdown.isBlank() && !segments.isEmpty()) {
                throw new DocumentIngestionException("Docling devolvio segmentos pero no contenido markdown util.");
            }

            return new DoclingConversionResult(convertMarkdown, pageCount(segments), segments);
        }
        catch (DocumentIngestionException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            log.error(
                "docling_pdf_chunk_conversion_failed filename={} content_bytes={}",
                filename,
                content == null ? 0 : content.length,
                ex);
            throw new DocumentIngestionException(
                    "Docling no pudo crear segmentos para este PDF. Revisa que Docling Serve este disponible e intenta de nuevo.",
                    ex);
        }
    }

    public String convertPdfToMarkdown(String filename, byte[] content) {
        try {
            String markdown = markdown(doclingServeApi.convertSource(buildConvertMarkdownRequest(filename, content)));
            log.info(
                "docling_markdown_conversion_trace filename={} final_md_length={}",
                filename,
                markdown.length());
            if (markdown.isBlank()) {
                throw new DocumentIngestionException("Docling returned an empty syllabus document.");
            }
            return markdown;
        }
        catch (DocumentIngestionException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            log.error(
                "docling_pdf_markdown_conversion_failed filename={} content_bytes={}",
                filename,
                content == null ? 0 : content.length,
                ex);
            throw new DocumentIngestionException(
                    "Docling no pudo convertir este PDF a markdown. Revisa que Docling Serve este disponible e intenta de nuevo.",
                    ex);
        }
    }

    List<org.springframework.ai.document.Document> readChunks(String filename, byte[] content) {
        return DoclingDocumentReader.builder()
                .doclingServeApi(doclingServeApi)
                .files(pdfResource(filename, content))
                .convertOptions(convertOptions())
                .chunkerOptions(hybridChunkerOptions())
                .documentParser(new ReviewMetadataDocumentParser())
                .build()
                .get();
    }

    HybridChunkerOptions hybridChunkerOptions() {
        var hybrid = properties.getChunking().getHybrid();
        return HybridChunkerOptions.builder()
                .maxTokens(hybrid.getMaxTokens())
                .tokenizer(hybrid.getTokenizer())
                .mergePeers(hybrid.getMergePeers())
                .useMarkdownTables(hybrid.isUseMarkdownTables())
                .includeRawText(hybrid.isIncludeRawText())
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

    private static ConvertDocumentOptions convertOptions() {
        return ConvertDocumentOptions.builder()
                .toFormat(OutputFormat.MARKDOWN)
                .imageExportMode(ImageRefMode.PLACEHOLDER)
                .includeImages(false)
                .build();
    }

    private static Resource pdfResource(String filename, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static String markdown(ConvertDocumentResponse response) {
        if (!(response instanceof InBodyConvertDocumentResponse inBodyResponse)
                || inBodyResponse.getDocument() == null) {
            return "";
        }
        String markdown = inBodyResponse.getDocument().getMarkdownContent();
        return markdown == null ? "" : markdown.trim();
    }

    private static List<DoclingSegmentDraft> mapChunkDocuments(
            List<org.springframework.ai.document.Document> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream().filter(Objects::nonNull).map(DoclingClientService::toSegmentDraft).toList();
    }

    private void logChunkStats(String filename, List<DoclingSegmentDraft> segments) {
        if (segments.isEmpty()) {
            return;
        }
        int maxConfiguredTokens = properties.getChunking().getHybrid().getMaxTokens();
        int maxReturnedTokens = segments.stream()
                .map(DoclingSegmentDraft::tokenCount)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int maxChars = segments.stream()
                .map(DoclingSegmentDraft::content)
                .filter(Objects::nonNull)
                .map(String::length)
                .max(Comparator.naturalOrder())
                .orElse(0);
        String eventName = maxReturnedTokens > maxConfiguredTokens
                ? "docling_chunk_stats_over_configured_limit"
                : "docling_chunk_stats";
        log.info(
            "{} filename={} chunks_count={} configured_max_tokens={} returned_max_tokens={} returned_max_chars={}",
            eventName,
            filename,
            segments.size(),
            maxConfiguredTokens,
            maxReturnedTokens,
            maxChars);
    }

    private static DoclingSegmentDraft toSegmentDraft(org.springframework.ai.document.Document document) {
        var metadata = document.getMetadata();
        var pageNumbers = integerList(metadata.get(PAGE_NUMBERS));
        return new DoclingSegmentDraft(integer(metadata.get(ORDINAL), 0),
                string(metadata.get(HEADING_PATH), "Documento"),
                document.getText().trim(),
                nullableInteger(metadata.get(TOKEN_COUNT)),
                nullableInteger(metadata.get(PAGE_NUMBER)),
                pageNumbers,
                stringList(metadata.get(CAPTIONS)),
                stringList(metadata.get(DOC_ITEMS)),
                string(metadata.get(RAW_TEXT), document.getText()));
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

    private static Integer nullableInteger(Object value) {
        return value == null ? null : integer(value, 0);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value));
        }
        catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : Objects.toString(value);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return compactStrings(values.stream().map(Objects::toString).toList());
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return compactIntegers(values.stream().map(item -> integer(item, 0)).toList());
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

    private static class ReviewMetadataDocumentParser implements DoclingDocumentParser {

        @Override
        public List<org.springframework.ai.document.Document> parse(
                List<Chunk> chunks,
                Map<String, Object> commonMetadata) {
            if (chunks == null) {
                return List.of();
            }
            var ordinal = new int[] { 1 };
            return chunks.stream()
                    .filter(Objects::nonNull)
                    .map(chunk -> toDocument(chunk, commonMetadata, ordinal))
                    .filter(Objects::nonNull)
                    .toList();
        }

        private org.springframework.ai.document.Document toDocument(
                Chunk chunk,
                Map<String, Object> commonMetadata,
                int[] ordinal) {
            String text = chunk.getText() == null ? "" : chunk.getText().trim();
            if (text.isBlank()) {
                return null;
            }
            return org.springframework.ai.document.Document.builder()
                    .id(UUID.randomUUID().toString())
                    .text(text)
                    .metadata(metadata(chunk, commonMetadata, ordinal[0]++))
                    .build();
        }

        private Map<String, Object> metadata(Chunk chunk, Map<String, Object> commonMetadata, int ordinal) {
            var metadata = new LinkedHashMap<String, Object>();
            if (commonMetadata != null) {
                metadata.putAll(commonMetadata);
            }
            var pageNumbers = compactIntegers(chunk.getPageNumbers());
            metadata.put(ORDINAL, ordinal);
            metadata.put(HEADING_PATH, headingPath(chunk.getHeadings()));
            metadata.put(TOKEN_COUNT, chunk.getNumTokens());
            metadata.put(PAGE_NUMBER, pageNumbers.isEmpty() ? null : pageNumbers.getFirst());
            metadata.put(PAGE_NUMBERS, pageNumbers);
            metadata.put(CAPTIONS, compactStrings(chunk.getCaptions()));
            metadata.put(DOC_ITEMS, compactStrings(chunk.getDocItems()));
            metadata.put(RAW_TEXT, chunk.getRawText() == null ? chunk.getText() : chunk.getRawText());
            metadata.put(CHUNKER, DOCLING_HYBRID);
            metadata.values().removeIf(Objects::isNull);
            return metadata;
        }
    }
}
