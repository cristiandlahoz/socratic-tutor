package com.wornux.documentingest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.ai.document.DocumentIngestionProperties;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DoclingRealIntegrationTest {

    @Test
    void converts_real_pdf_file_with_real_docling_service() throws Exception {
        var properties = new DocumentIngestionProperties();
        properties.setDoclingBaseUrl(System.getProperty("docling.base-url", "http://localhost:5001"));

        Path tempPdf = Files.createTempFile("docling-real-", ".pdf");
        try {
            Files.writeString(tempPdf, minimalPdf(), UTF_8);

            var service = new DoclingClientService(properties);
            var result = service.convertPdfToMarkdownAndChunks("docling-real-test.pdf", Files.readAllBytes(tempPdf));

            assertThat(result.segments()).isNotEmpty();
            assertThat(result.segments().getFirst().content()).containsIgnoringCase("hello docling");
            assertThat(result.markdown()).isNotBlank();
            assertThat(result.markdown()).containsIgnoringCase("hello docling");
        }
        finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    private static String minimalPdf() {
        return """
               %PDF-1.1
               1 0 obj
               << /Type /Catalog /Pages 2 0 R >>
               endobj
               2 0 obj
               << /Type /Pages /Kids [3 0 R] /Count 1 >>
               endobj
               3 0 obj
               << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
               endobj
               4 0 obj
               << /Length 55 >>
               stream
               BT
               /F1 18 Tf
               30 80 Td
               (hello docling real test) Tj
               ET
               endstream
               endobj
               5 0 obj
               << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
               endobj
               xref
               0 6
               0000000000 65535 f
               0000000010 00000 n
               0000000062 00000 n
               0000000119 00000 n
               0000000246 00000 n
               0000000351 00000 n
               trailer
               << /Size 6 /Root 1 0 R >>
               startxref
               421
               %%EOF
               """;
    }
}
