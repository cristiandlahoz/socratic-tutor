package com.wornux.infrastructure.external.crunner;

record ContainerRunResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
