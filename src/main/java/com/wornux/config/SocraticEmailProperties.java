package com.wornux.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "socratic.email")
public class SocraticEmailProperties {

    private String invitationBaseUrl = "";
    private Duration invitationExpiration = Duration.ofHours(72);
    private String fromAddress = "";
    private String fromName = "";
    private Smtp smtp = new Smtp();

    public String getInvitationBaseUrl() {
        return invitationBaseUrl;
    }

    public void setInvitationBaseUrl(String invitationBaseUrl) {
        this.invitationBaseUrl = invitationBaseUrl;
    }

    public Duration getInvitationExpiration() {
        return invitationExpiration;
    }

    public void setInvitationExpiration(Duration invitationExpiration) {
        this.invitationExpiration = invitationExpiration;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp;
    }

    public static class Smtp {
        private String host = "";
        private int port = 1025;
        private String username = "";
        private String password = "";
        private boolean auth;
        private boolean starttlsEnabled;
        private boolean sslEnabled;
        private int connectionTimeout = 5000;
        private int timeout = 5000;
        private int writeTimeout = 5000;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isAuth() {
            return auth;
        }

        public void setAuth(boolean auth) {
            this.auth = auth;
        }

        public boolean isStarttlsEnabled() {
            return starttlsEnabled;
        }

        public void setStarttlsEnabled(boolean starttlsEnabled) {
            this.starttlsEnabled = starttlsEnabled;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getWriteTimeout() {
            return writeTimeout;
        }

        public void setWriteTimeout(int writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
    }
}
