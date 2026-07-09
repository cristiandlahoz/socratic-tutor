package com.wornux.services.chat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.wornux.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ModelAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(ModelAvailabilityService.class);

    private final List<Consumer<ModelAvailabilityStatus>> listeners = new CopyOnWriteArrayList<>();
    private final HttpClient httpClient;
    private final URI modelsUri;
    private final String apiKey;
    private final Duration timeout;
    private volatile ModelAvailabilityStatus status = ModelAvailabilityStatus.CHECKING;

    public ModelAvailabilityService(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            ApplicationProperties.Ai.ModelAvailability modelAvailabilityProperties) {
        this.apiKey = apiKey;
        this.timeout = modelAvailabilityProperties.getTimeout();
        this.modelsUri = URI.create(baseUrl.replaceAll("/+$", "") + "/models");
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public ModelAvailabilityStatus currentStatus() {
        return status;
    }

    public AutoCloseable subscribe(Consumer<ModelAvailabilityStatus> listener) {
        listeners.add(listener);
        listener.accept(status);
        return () -> listeners.remove(listener);
    }

    public void markConnected() {
        updateStatus(ModelAvailabilityStatus.CONNECTED);
    }

    public void markOffline() {
        updateStatus(ModelAvailabilityStatus.OFFLINE);
    }

    @Scheduled(fixedDelayString = "#{@modelAvailabilityProperties.probeIntervalMs}",
            initialDelayString = "#{@modelAvailabilityProperties.initialDelayMs}")
    public void probeConfiguredModelServer() {
        try {
            var requestBuilder =
                    HttpRequest.newBuilder(modelsUri).timeout(timeout).GET().header("Accept", "application/json");
            if (apiKey != null && !apiKey.isBlank() && !"dummy".equals(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                updateStatus(ModelAvailabilityStatus.CONNECTED);
            }
            else {
                log.debug("model_availability_probe_failed status={} uri={}", response.statusCode(), modelsUri);
                updateStatus(ModelAvailabilityStatus.OFFLINE);
            }
        }
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            updateStatus(ModelAvailabilityStatus.OFFLINE);
        }
        catch (Exception exception) {
            log.debug("model_availability_probe_failed uri={} error={}", modelsUri, exception.toString());
            updateStatus(ModelAvailabilityStatus.OFFLINE);
        }
    }

    private void updateStatus(ModelAvailabilityStatus nextStatus) {
        if (status == nextStatus) {
            return;
        }
        status = nextStatus;
        listeners.forEach(listener -> listener.accept(nextStatus));
    }
}
