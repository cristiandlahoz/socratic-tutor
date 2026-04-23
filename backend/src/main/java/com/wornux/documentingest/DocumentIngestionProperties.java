package com.wornux.documentingest;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.document-ingest")
public class DocumentIngestionProperties {

  private String doclingBaseUrl = "http://localhost:5001";
  private Duration doclingConnectTimeout = Duration.ofSeconds(5);
  private Duration doclingReadTimeout = Duration.ofMinutes(2);
  private int maxFileSizeBytes = 20 * 1024 * 1024;
  private int retrievalTopK = 4;
  private double retrievalSimilarityThreshold = 0.55;
  private Chunking chunking = new Chunking();
  private Catalog catalog = new Catalog();
  private Inventory inventory = new Inventory();

  @Getter
  @Setter
  public static class Chunking {

    private Hybrid hybrid = new Hybrid();

    @Getter
    @Setter
    public static class Hybrid {

      private int maxTokens = 512;
      private String tokenizer = "/opt/tokenizers/qwen3-0.6b-tokenizer";
      private Boolean mergePeers = true;
      private boolean useMarkdownTables = true;
      private boolean includeRawText = true;
    }
  }

  @Getter
  @Setter
  public static class Catalog {

    private int maxTags = 8;
    private int maxQuestionExamples = 6;
  }

  @Getter
  @Setter
  public static class Inventory {

    private int maxDocuments = 8;
    private int maxChars = 2_400;
  }
}
