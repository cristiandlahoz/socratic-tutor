package com.wornux.infrastructure.external.crunner;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.wornux.services.crunner.CDebugRequest;
import com.wornux.services.crunner.CSourceRequest;
import org.springframework.stereotype.Component;

@Component
class CWorkspaceFactory {

    private final GdbScriptFactory scriptFactory;

    CWorkspaceFactory(GdbScriptFactory scriptFactory) {
        this.scriptFactory = scriptFactory;
    }

    CWorkspace compilerWorkspace(CSourceRequest request) throws IOException {
        var workspace = createWorkspace("c-runner-");
        try {
            writeSource(workspace.path(), request.filename(), request.source());
            return workspace;
        }
        catch (IOException | RuntimeException ex) {
            workspace.close();
            throw ex;
        }
    }

    CWorkspace debuggerWorkspace(CDebugRequest request, int maxSnapshots) throws IOException {
        var workspace = createWorkspace("c-debugger-");
        try {
            writeSource(workspace.path(), request.filename(), request.source());
            writeFile(workspace.path().resolve("stdin.txt"), request.stdin());
            writeFile(workspace.path().resolve("stdout.txt"), "");
            writeFile(workspace.path().resolve("debug.mi"), scriptFactory.script(maxSnapshots));
            return workspace;
        }
        catch (IOException | RuntimeException ex) {
            workspace.close();
            throw ex;
        }
    }

    private static CWorkspace createWorkspace(String prefix) throws IOException {
        var root = Files.createTempDirectory(prefix);
        var workspace = root.resolve("workspace");
        Files.createDirectory(workspace);
        return new CWorkspace(root, workspace);
    }

    private static void writeSource(Path workspace, String filename, String source) throws IOException {
        var sourcePath = workspace.resolve(filename).normalize();
        if (!sourcePath.getParent().equals(workspace)) {
            throw new IOException("Unsafe C source filename: %s".formatted(filename));
        }
        writeFile(sourcePath, source);
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.writeString(path, content, UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
