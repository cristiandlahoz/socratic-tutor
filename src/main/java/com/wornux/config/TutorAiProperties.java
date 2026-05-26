package com.wornux.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.tutor")
public class TutorAiProperties {

  private String routingModel;
  private ToolObservability toolObservability = new ToolObservability();

  @Getter
  @Setter
  public static class ToolObservability {

    private boolean capturePayloads;
    private int maxPayloadChars = 4000;
  }
}
