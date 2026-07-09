package com.wornux.infrastructure.external.crunner;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.wornux.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verifies required Docker images before accepting application traffic.
 *
 * @author cristiandlahoz
 */
@Component
public class DockerImageStartupGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerImageStartupGuard.class);
    private static final long PULL_TIMEOUT_MINUTES = 5;

    private final DockerClient dockerClient;
    private final ApplicationProperties.CRunner properties;

    public DockerImageStartupGuard(DockerClient dockerClient, ApplicationProperties.CRunner properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        dockerClient.pingCmd().exec();
        requiredImages().forEach(this::ensureImageAvailable);
    }

    private Set<String> requiredImages() {
        var images = new LinkedHashSet<String>();
        images.add(requireConfiguredImage(properties.getCompilerImage(), "compiler"));
        images.add(requireConfiguredImage(properties.getDebuggerImage(), "debugger"));
        return images;
    }

    private String requireConfiguredImage(String image, String role) {
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("Required Docker %s image is not configured".formatted(role));
        }
        return image;
    }

    private void ensureImageAvailable(String image) {
        if (imageExists(image)) {
            log.info("Required Docker image is available: {}", image);
            return;
        }

        log.info("Required Docker image is missing, pulling: {}", image);
        pullImage(image);
        requirePulledImage(image);
    }

    private boolean imageExists(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return true;
        }
        catch (NotFoundException _) {
            return false;
        }
    }

    private void pullImage(String image) {
        var imageRef = ImageRef.from(image);
        var callback = new PullImageResultCallback();
        boolean completed;

        try {
            completed = dockerClient.pullImageCmd(imageRef.repository())
                    .withTag(imageRef.tag())
                    .exec(callback)
                    .awaitCompletion(PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pulling required Docker image: %s".formatted(image), ex);
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to pull required Docker image: %s".formatted(image), ex);
        }

        if (!completed) {
            closeCallback(callback);
            throw new IllegalStateException("Timed out pulling required Docker image: %s".formatted(image));
        }
    }

    private void closeCallback(PullImageResultCallback callback) {
        try {
            callback.close();
        }
        catch (IOException ex) {
            log.warn("Failed to close Docker image pull callback", ex);
        }
    }

    private void requirePulledImage(String image) {
        if (!imageExists(image)) {
            throw new IllegalStateException("Required Docker image is unavailable after pull: %s".formatted(image));
        }
        log.info("Required Docker image pulled successfully: {}", image);
    }

    private record ImageRef(String repository, String tag) {

        private static final String DEFAULT_TAG = "latest";

        private static ImageRef from(String image) {
            var slashIndex = image.lastIndexOf('/');
            var tagIndex = image.lastIndexOf(':');
            if (tagIndex > slashIndex) {
                return new ImageRef(image.substring(0, tagIndex), image.substring(tagIndex + 1));
            }
            return new ImageRef(image, DEFAULT_TAG);
        }
    }
}
