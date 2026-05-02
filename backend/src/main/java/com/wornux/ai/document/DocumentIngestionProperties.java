package com.wornux.ai.document;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
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
