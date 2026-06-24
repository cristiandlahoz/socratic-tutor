package com.wornux.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.document-ingest")
public class DocumentIngestionProperties {

    private int maxFileSizeBytes = 20 * 1024 * 1024;
    private int retrievalTopK = 4;
    private double retrievalSimilarityThreshold = 0.55;
    private Chunking chunking = new Chunking();

    @Getter
    @Setter
    public static class Chunking {

        private Hybrid hybrid = new Hybrid();

        @Getter
        @Setter
        public static class Hybrid {

            private int maxTokens = 512;
            private Boolean mergePeers = true;
            private boolean useMarkdownTables = true;
            private boolean includeRawText = true;
        }
    }

}
