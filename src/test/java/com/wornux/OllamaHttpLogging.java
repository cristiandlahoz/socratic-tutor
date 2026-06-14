package com.wornux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class OllamaHttpLogging {

    private static final Logger log = LoggerFactory.getLogger(OllamaHttpLogging.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path TRANSCRIPT_DIRECTORY = Path.of("target", "ollama-http-transcripts");

    private OllamaHttpLogging() {}

    static RestClient.Builder restClientBuilder(String transcriptName) {
        var transcript = new Transcript(transcriptName);
        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor((request, body, execution) -> {
                    var requestBody = new String(body, StandardCharsets.UTF_8);
                    transcript.recordRequest(request.getMethod().name(), request.getURI().toString(), requestBody);

                    var response = execution.execute(request, body);
                    var responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    transcript.recordResponse(response.getStatusCode().value(), response.getStatusText(), responseBody);
                    return response;
                });
    }

    private static final class Transcript {

        private final AtomicInteger exchangeCounter = new AtomicInteger();
        private final Path path;

        private Transcript(String name) {
            this.path = TRANSCRIPT_DIRECTORY.resolve("%s.http".formatted(name));
            reset();
            log.info("Ollama HTTP transcript: {}", path.toAbsolutePath());
        }

        private void recordRequest(String method, String uri, String body) {
            var exchange = exchangeCounter.incrementAndGet();
            var formattedBody = formatJson(body);
            var entry = """

                        ### Exchange %d request %s
                        %s %s
                        Content-Type: application/json

                        %s
                        """.formatted(exchange, Instant.now(), method, uri, formattedBody);
            append(entry);
            log.info("Ollama request #{} {} {}\n{}", exchange, method, uri, formattedBody);
        }

        private void recordResponse(int statusCode, String statusText, String body) {
            var exchange = exchangeCounter.get();
            var formattedBody = formatJson(body);
            var entry = """

                        ### Exchange %d response %s
                        HTTP %d %s
                        Content-Type: application/json

                        %s
                        """.formatted(exchange, Instant.now(), statusCode, statusText, formattedBody);
            append(entry);
            log.info("Ollama response #{} HTTP {} {}\n{}", exchange, statusCode, statusText, formattedBody);
        }

        private String formatJson(String body) {
            if (body == null || body.isBlank()) {
                return "";
            }
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(body));
            }
            catch (JsonProcessingException ex) {
                return body;
            }
        }

        private void reset() {
            try {
                Files.createDirectories(TRANSCRIPT_DIRECTORY);
                Files.writeString(path, "# Ollama HTTP transcript%n".formatted());
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private void append(String content) {
            try {
                Files.writeString(path, content, StandardOpenOption.APPEND);
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
