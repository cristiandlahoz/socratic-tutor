package com.wornux.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.tutor")
public class TutorAiProperties {

  private boolean promptLoggingEnabled;
  private String routingModel;
}
