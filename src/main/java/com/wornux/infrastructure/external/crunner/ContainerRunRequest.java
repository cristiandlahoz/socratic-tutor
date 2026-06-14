package com.wornux.infrastructure.external.crunner;

import com.github.dockerjava.api.model.Capability;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

record ContainerRunRequest(
    String image,
    List<String> command,
    String workingDirectory,
    Duration timeout,
    String memory,
    String cpus,
    long pidsLimit,
    boolean readOnlyRoot,
    Map<String, String> tmpFs,
    Path workspace,
    List<Capability> capabilities,
    List<String> securityOptions) {}
