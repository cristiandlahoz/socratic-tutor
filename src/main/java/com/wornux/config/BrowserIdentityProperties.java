package com.wornux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.browser")
public class BrowserIdentityProperties {

    private String idCookieName = "st_browser_id";

    public String getIdCookieName() {
        return idCookieName;
    }

    public void setIdCookieName(String idCookieName) {
        this.idCookieName = idCookieName;
    }
}
