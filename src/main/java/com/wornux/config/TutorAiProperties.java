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

        private boolean captureToolReturns;
        private int maxToolReturnChars = 4000;

        public boolean isCaptureToolReturns() {
            return captureToolReturns;
        }

        public void setCaptureToolReturns(boolean captureToolReturns) {
            this.captureToolReturns = captureToolReturns;
        }

        public int getMaxToolReturnChars() {
            return maxToolReturnChars;
        }

        public void setMaxToolReturnChars(int maxToolReturnChars) {
            this.maxToolReturnChars = maxToolReturnChars;
        }
    }
}
