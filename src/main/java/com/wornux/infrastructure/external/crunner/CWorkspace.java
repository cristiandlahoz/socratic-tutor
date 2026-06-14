package com.wornux.infrastructure.external.crunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record CWorkspace(Path root, Path path) implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CWorkspace.class);

    @Override
    public void close() {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(CWorkspace::delete);
        }
        catch (IOException ex) {
            log.debug("Failed to clean C runner workspace {}", root, ex);
        }
    }

    private static void delete(Path currentPath) {
        try {
            Files.deleteIfExists(currentPath);
        }
        catch (IOException ex) {
            log.debug("Failed to delete C runner workspace path {}", currentPath, ex);
        }
    }
}
