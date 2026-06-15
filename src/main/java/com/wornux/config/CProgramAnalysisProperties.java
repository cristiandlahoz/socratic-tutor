package com.wornux.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.c-runner")
public class CProgramAnalysisProperties {

    private String compilerImage;
    private String debuggerImage;
    private Duration timeout;
    private Duration debugTimeout;
    private long maxSourceBytes;
    private int maxSnapshots;
    private int maxOutputBytes;
    private String memory;
    private String debuggerMemory;
    private String cpus;
    private int pidsLimit;
    private int cacheMaximumSize;
    private Duration cacheTtl;
}
