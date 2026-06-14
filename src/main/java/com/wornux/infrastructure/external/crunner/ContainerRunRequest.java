package com.wornux.infrastructure.external.crunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.Capability;

record ContainerRunRequest(String image, List<String> command, String workingDirectory, Duration timeout, String memory,
        String cpus, long pidsLimit, boolean readOnlyRoot, Map<String, String> tmpFs, Path workspace,
        List<Capability> capabilities, List<String> securityOptions) {}
