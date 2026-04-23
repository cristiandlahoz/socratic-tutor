package com.wornux.documentingest;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class DoclingClientService {

  private final DocumentIngestionProperties properties;

  public DoclingClientService(DocumentIngestionProperties properties) {
    this.properties = properties;
  }

  public DoclingConversionResult convertPdfToMarkdown(String filename, byte[] content) {
    try {
      DoclingServeApi api =
          DoclingServeApi.builder()
              .baseUrl(properties.getDoclingBaseUrl())
              .connectTimeout(properties.getDoclingConnectTimeout())
              .readTimeout(properties.getDoclingReadTimeout())
              .build();

      ConvertDocumentRequest request =
          ConvertDocumentRequest.builder()
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

      var response = (InBodyConvertDocumentResponse) api.convertSource(request);
      var document = response.getDocument();
      String markdown =
          document == null || document.getMarkdownContent() == null
              ? ""
              : document.getMarkdownContent();
      return new DoclingConversionResult(markdown, null);
    } catch (RuntimeException exception) {
      throw new DocumentIngestionException("Docling no pudo transformar el PDF.", exception);
    }
  }
}
