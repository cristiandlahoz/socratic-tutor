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

    public String getRoutingModel() {
        return routingModel;
    }

    public void setRoutingModel(String routingModel) {
        this.routingModel = routingModel;
    }

    public ToolObservability getToolObservability() {
        return toolObservability;
    }

    public void setToolObservability(ToolObservability toolObservability) {
        this.toolObservability = toolObservability;
    }

    @Getter
    @Setter
    public static class ToolObservability {

        private boolean capturePayloads;
        private int maxPayloadChars = 4000;

        public boolean isCapturePayloads() {
            return capturePayloads;
        }

        public void setCapturePayloads(boolean capturePayloads) {
            this.capturePayloads = capturePayloads;
        }

        public int getMaxPayloadChars() {
            return maxPayloadChars;
        }

        public void setMaxPayloadChars(int maxPayloadChars) {
            this.maxPayloadChars = maxPayloadChars;
        }
    }
}
