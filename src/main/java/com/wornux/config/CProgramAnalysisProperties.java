package com.wornux.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.c-runner")
public class CProgramAnalysisProperties {

  private String compilerImage = "gcc:15.2.0-bookworm";
  private String debuggerImage = "socratic-tutor/c-runner:local";
  private Duration timeout = Duration.ofSeconds(8);
  private Duration debugTimeout = Duration.ofSeconds(12);
  private long maxSourceBytes = 64 * 1024;
  private int maxSnapshots = 300;
  private int maxOutputBytes = 16 * 1024;
  private String memory = "128m";
  private String debuggerMemory = "512m";
  private String cpus = "0.5";
  private int pidsLimit = 64;
  private int cacheMaximumSize = 256;
  private Duration cacheTtl = Duration.ofMinutes(10);
}
