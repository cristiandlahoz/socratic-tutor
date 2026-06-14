package com.wornux.infrastructure.external.crunner;

import java.util.List;
import java.util.Map;

import com.wornux.config.CProgramAnalysisProperties;
import com.wornux.services.crunner.CSourceRequest;
import org.springframework.stereotype.Component;

@Component
class CCompilerRunner {

    private static final String WORKSPACE = "/workspace";

    private final CProgramAnalysisProperties properties;
    private final DockerContainerRunner containerRunner;

    CCompilerRunner(CProgramAnalysisProperties properties, DockerContainerRunner containerRunner) {
        this.properties = properties;
        this.containerRunner = containerRunner;
    }

    ContainerRunResult validate(CWorkspace workspace, CSourceRequest request) throws InterruptedException {
        return containerRunner.run(containerRequest(workspace, request));
    }

    private ContainerRunRequest containerRequest(CWorkspace workspace, CSourceRequest request) {
        return new ContainerRunRequest(properties.getCompilerImage(),
                command(request),
                WORKSPACE,
                properties.getTimeout(),
                properties.getMemory(),
                properties.getCpus(),
                properties.getPidsLimit(),
                true,
                Map.of("/tmp", "rw,noexec,nosuid,size=16m"),
                workspace.path(),
                List.of(),
                List.of());
    }

    private static List<String> command(CSourceRequest request) {
        return List.of(
            "gcc",
            "-fsyntax-only",
            "-std=%s".formatted(request.standard()),
            "-Wall",
            "-Wextra",
            "-Wpedantic",
            "-fdiagnostics-format=sarif-stderr",
            request.filename());
    }
}
