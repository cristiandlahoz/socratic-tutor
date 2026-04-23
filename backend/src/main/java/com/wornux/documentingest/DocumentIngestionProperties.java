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
  private int segmentMaxChars = 1_400;
  private int segmentOverlapChars = 160;
  private int retrievalTopK = 4;
  private double retrievalSimilarityThreshold = 0.55;
}
