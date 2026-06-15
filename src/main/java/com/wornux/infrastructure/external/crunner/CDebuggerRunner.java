package com.wornux.infrastructure.external.crunner;

import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.Capability;
import com.wornux.config.CProgramAnalysisProperties;
import com.wornux.services.crunner.CDebugRequest;
import org.springframework.stereotype.Component;

@Component
class CDebuggerRunner {

    private static final String WORKSPACE = "/workspace";

    private final CProgramAnalysisProperties properties;
    private final DockerContainerRunner containerRunner;

    CDebuggerRunner(CProgramAnalysisProperties properties, DockerContainerRunner containerRunner) {
        this.properties = properties;
        this.containerRunner = containerRunner;
    }

    ContainerRunResult debug(CWorkspace workspace, CDebugRequest request) throws InterruptedException {
        return containerRunner.run(containerRequest(workspace, request));
    }

    private ContainerRunRequest containerRequest(CWorkspace workspace, CDebugRequest request) {
        return new ContainerRunRequest(properties.getDebuggerImage(),
                command(request),
                WORKSPACE,
                properties.getDebugTimeout(),
                properties.getDebuggerMemory(),
                properties.getCpus(),
                properties.getPidsLimit(),
                false,
                Map.of("/tmp", "rw,nosuid,size=32m"),
                workspace.path(),
                List.of(Capability.SYS_PTRACE),
                List.of("seccomp=unconfined"));
    }

    private static List<String> command(CDebugRequest request) {
        return List.of("sh", "-lc", shellCommand(request));
    }

    private static String shellCommand(CDebugRequest request) {
        return """
               set -e

               command -v gdb >/dev/null 2>&1 || { echo 'gdb not found' >&2; exit 127; }

               gcc \
                 -std=%s \
                 -Wall \
                 -Wextra \
                 -Wpedantic \
                 -g \
                 -O0 \
                 -fno-omit-frame-pointer \
                 -fdiagnostics-format=sarif-stderr \
                 %s \
                 -lm \
                 -o main

               gdb --quiet --interpreter=mi2 < debug.mi
               """.formatted(request.standard(), request.filename());
    }
}
