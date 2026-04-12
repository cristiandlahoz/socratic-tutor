package com.wornux.chat.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.profile")
public class ProfileProperties {

    private boolean enabled = true;
    private boolean shadowMode;
    private int misconceptionTtlDays = 21;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShadowMode() {
        return shadowMode;
    }

    public void setShadowMode(boolean shadowMode) {
        this.shadowMode = shadowMode;
    }

    public int getMisconceptionTtlDays() {
        return misconceptionTtlDays;
    }

    public void setMisconceptionTtlDays(int misconceptionTtlDays) {
        this.misconceptionTtlDays = misconceptionTtlDays;
    }
}
