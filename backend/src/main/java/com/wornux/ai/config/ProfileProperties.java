package com.wornux.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.ai.profile")
public class ProfileProperties {

  private boolean enabled = true;
  private boolean shadowMode;
  private int misconceptionTtlDays = 21;
}
