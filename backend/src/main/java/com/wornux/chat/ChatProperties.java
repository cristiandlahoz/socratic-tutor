package com.wornux.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

  private String clientIdCookieName;
  private int contextWindowTokens;
  private double compactionThresholdRatio;
  private Ui ui;

  @Setter
  @Getter
  public static class Ui {
    private String thinkingSpinner;
  }
}
