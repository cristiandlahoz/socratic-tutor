package com.wornux.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties implements InitializingBean {

  private Security security = new Security();
  private Ai ai = new Ai();
  private DocumentIngest documentIngest = new DocumentIngest();
  private CRunner cRunner = new CRunner();
  private Email email = new Email();

  private static void require(List<String> missing, Object value, String key) {
    if (value == null) {
      missing.add(key);
    }
  }

  @Getter
  @Setter
  public static class Security {

    private Boolean disableForLocalDevelopment;
  }

  @Getter
  @Setter
  public static class Ai {

    private Conversation conversation = new Conversation();
    private SwitzerlandKnife switzerlandKnife = new SwitzerlandKnife();
    private ModelAvailability modelAvailability = new ModelAvailability();

    @Getter
    @Setter
    public static class Conversation {

      private static final double COMPOSER_PROMPT_LIMIT_RATIO = 0.40;

      private Config config = new Config();
      private Ui ui = new Ui();

      public int getContextWindowTokens() {
        return config.getContextWindowTokens();
      }

      public void setContextWindowTokens(Integer contextWindowTokens) {
        config.setContextWindowTokens(contextWindowTokens);
      }

      public double getCompactionThresholdRatio() {
        return config.getCompactionThresholdRatio();
      }

      public void setCompactionThresholdRatio(Double compactionThresholdRatio) {
        config.setCompactionThresholdRatio(compactionThresholdRatio);
      }

      public double getRecentHistoryRetentionRatio() {
        return config.getRecentHistoryRetentionRatio();
      }

      public void setRecentHistoryRetentionRatio(Double recentHistoryRetentionRatio) {
        config.setRecentHistoryRetentionRatio(recentHistoryRetentionRatio);
      }

      public int compactionThresholdTokens() {
        int threshold = (int) Math.floor(getContextWindowTokens() * getCompactionThresholdRatio());
        if (threshold <= 0) {
          throw new IllegalStateException("Chat compaction threshold must be greater than zero");
        }
        return threshold;
      }

      public int composerPromptLimit() {
        int limit = (int) Math.floor(getContextWindowTokens() * COMPOSER_PROMPT_LIMIT_RATIO);
        if (limit <= 0) {
          throw new IllegalStateException("Composer prompt limit must be greater than zero");
        }
        return limit;
      }

      public int recentHistoryRetentionTokens() {
        int limit = (int) Math.floor(getContextWindowTokens() * getRecentHistoryRetentionRatio());
        if (limit <= 0) {
          throw new IllegalStateException("Recent history retention must be greater than zero");
        }
        return limit;
      }

      @Getter
      @Setter
      public static class Config {

        private Integer contextWindowTokens;
        private Double compactionThresholdRatio;
        private Double recentHistoryRetentionRatio;
      }

      @Getter
      @Setter
      public static class Ui {

        private String thinkingSpinner;
      }
    }

    @Getter
    @Setter
    public static class SwitzerlandKnife {

      private String model;
    }

    @Getter
    @Setter
    public static class ModelAvailability {

      private Duration timeout;
      private Long probeIntervalMs;
      private Long initialDelayMs;
    }
  }

  @Getter
  @Setter
  public static class DocumentIngest {

    private Integer maxFileSizeBytes;
    private Integer retrievalTopK;
    private Double retrievalSimilarityThreshold;
    private Chunking chunking = new Chunking();

    @Getter
    @Setter
    public static class Chunking {

      private Hybrid hybrid = new Hybrid();

      @Getter
      @Setter
      public static class Hybrid {

        private Integer maxTokens;
        private String tokenizer;
        private Boolean mergePeers;
        private Boolean useMarkdownTables;
        private Boolean includeRawText;

        public boolean isUseMarkdownTables() {
          return useMarkdownTables;
        }

        public boolean isIncludeRawText() {
          return includeRawText;
        }
      }
    }
  }

  @Getter
  @Setter
  public static class CRunner {

    private String compilerImage;
    private String debuggerImage;
    private Duration timeout;
    private Duration debugTimeout;
    private Long maxSourceBytes;
    private Integer maxSnapshots;
    private Integer maxOutputBytes;
    private String memory;
    private String debuggerMemory;
    private String cpus;
    private Integer pidsLimit;
    private Integer cacheMaximumSize;
    private Duration cacheTtl;
  }

  @Getter
  @Setter
  public static class Email {

    private String invitationBaseUrl;
    private Duration invitationExpiration;
    private String fromAddress;
    private String fromName;
    private Smtp smtp = new Smtp();

    @Getter
    @Setter
    public static class Smtp {

      private String host;
      private Integer port;
      private String username;
      private String password;
      private Boolean auth;
      private Boolean starttlsEnabled;
      private Boolean sslEnabled;
      private Integer connectionTimeout;
      private Integer timeout;
      private Integer writeTimeout;

      public boolean isAuth() {
        return auth;
      }

      public boolean isStarttlsEnabled() {
        return starttlsEnabled;
      }

      public boolean isSslEnabled() {
        return sslEnabled;
      }
    }
  }

  @Override
  public void afterPropertiesSet() {
    var missing = new ArrayList<String>();
    require(
        missing, security.disableForLocalDevelopment, "app.security.disable-for-local-development");
    require(
        missing,
        ai.conversation.config.contextWindowTokens,
        "app.ai.conversation.config.context-window-tokens");
    require(
        missing,
        ai.conversation.config.compactionThresholdRatio,
        "app.ai.conversation.config.compaction-threshold-ratio");
    require(
        missing,
        ai.conversation.config.recentHistoryRetentionRatio,
        "app.ai.conversation.config.recent-history-retention-ratio");
    require(missing, ai.conversation.ui.thinkingSpinner, "app.ai.conversation.ui.thinking-spinner");
    require(missing, ai.switzerlandKnife.model, "app.ai.switzerland-knife.model");
    require(missing, ai.modelAvailability.timeout, "app.ai.model-availability.timeout");
    require(
        missing,
        ai.modelAvailability.probeIntervalMs,
        "app.ai.model-availability.probe-interval-ms");
    require(
        missing, ai.modelAvailability.initialDelayMs, "app.ai.model-availability.initial-delay-ms");
    require(missing, documentIngest.maxFileSizeBytes, "app.document-ingest.max-file-size-bytes");
    require(missing, documentIngest.retrievalTopK, "app.document-ingest.retrieval-top-k");
    require(
        missing,
        documentIngest.retrievalSimilarityThreshold,
        "app.document-ingest.retrieval-similarity-threshold");
    require(
        missing,
        documentIngest.chunking.hybrid.maxTokens,
        "app.document-ingest.chunking.hybrid.max-tokens");
    require(
        missing,
        documentIngest.chunking.hybrid.tokenizer,
        "app.document-ingest.chunking.hybrid.tokenizer");
    require(
        missing,
        documentIngest.chunking.hybrid.mergePeers,
        "app.document-ingest.chunking.hybrid.merge-peers");
    require(
        missing,
        documentIngest.chunking.hybrid.useMarkdownTables,
        "app.document-ingest.chunking.hybrid.use-markdown-tables");
    require(
        missing,
        documentIngest.chunking.hybrid.includeRawText,
        "app.document-ingest.chunking.hybrid.include-raw-text");
    require(missing, cRunner.compilerImage, "app.c-runner.compiler-image");
    require(missing, cRunner.debuggerImage, "app.c-runner.debugger-image");
    require(missing, cRunner.timeout, "app.c-runner.timeout");
    require(missing, cRunner.debugTimeout, "app.c-runner.debug-timeout");
    require(missing, cRunner.maxSourceBytes, "app.c-runner.max-source-bytes");
    require(missing, cRunner.maxSnapshots, "app.c-runner.max-snapshots");
    require(missing, cRunner.maxOutputBytes, "app.c-runner.max-output-bytes");
    require(missing, cRunner.memory, "app.c-runner.memory");
    require(missing, cRunner.debuggerMemory, "app.c-runner.debugger-memory");
    require(missing, cRunner.cpus, "app.c-runner.cpus");
    require(missing, cRunner.pidsLimit, "app.c-runner.pids-limit");
    require(missing, cRunner.cacheMaximumSize, "app.c-runner.cache-maximum-size");
    require(missing, cRunner.cacheTtl, "app.c-runner.cache-ttl");
    require(missing, email.invitationBaseUrl, "app.email.invitation-base-url");
    require(missing, email.invitationExpiration, "app.email.invitation-expiration");
    require(missing, email.fromAddress, "app.email.from-address");
    require(missing, email.fromName, "app.email.from-name");
    require(missing, email.smtp.host, "app.email.smtp.host");
    require(missing, email.smtp.port, "app.email.smtp.port");
    require(missing, email.smtp.username, "app.email.smtp.username");
    require(missing, email.smtp.password, "app.email.smtp.password");
    require(missing, email.smtp.auth, "app.email.smtp.auth");
    require(missing, email.smtp.starttlsEnabled, "app.email.smtp.starttls-enabled");
    require(missing, email.smtp.sslEnabled, "app.email.smtp.ssl-enabled");
    require(missing, email.smtp.connectionTimeout, "app.email.smtp.connection-timeout");
    require(missing, email.smtp.timeout, "app.email.smtp.timeout");
    require(missing, email.smtp.writeTimeout, "app.email.smtp.write-timeout");
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Missing required application properties: " + String.join(", ", missing));
    }
  }
}
